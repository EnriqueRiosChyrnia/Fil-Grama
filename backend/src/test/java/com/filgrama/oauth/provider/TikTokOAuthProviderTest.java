package com.filgrama.oauth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProfile;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.TokenRevokedException;
import com.filgrama.oauth.config.OAuthProperties;

/** Tests del provider real de TikTok con {@code /v2/oauth/token/} mockeado a nivel HTTP. */
class TikTokOAuthProviderTest {

    private MockRestServiceServer server;

    private OAuthProperties props() {
        OAuthProperties props = new OAuthProperties();
        props.setRedirectBaseUri("http://localhost:8080");
        props.getTiktok().setClientKey("key");
        props.getTiktok().setClientSecret("secret"); // nunca debe loguearse
        return props;
    }

    private TikTokOAuthProvider provider(OAuthProperties props) {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new TikTokOAuthProvider(props, builder);
    }

    @Test
    void exchangeReturnsTokensAndOpenId() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/oauth/token/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"a-tok","refresh_token":"r-tok","open_id":"oid-1",
                         "expires_in":86400,"refresh_expires_in":31536000,"scope":"user.info.basic",
                         "token_type":"Bearer"}""", MediaType.APPLICATION_JSON));
        // El canje ahora enriquece el nombre con user/info (best-effort); acá no nos importa el nombre.
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        OAuthExchangeResult r = p.exchangeCode(Platform.TIKTOK, "auth-code");

        assertThat(r.accountType()).isEqualTo(AccountType.CREATOR);
        assertThat(r.externalAccountId()).isEqualTo("oid-1");
        assertThat(r.accessToken()).isEqualTo("a-tok");
        assertThat(r.refreshToken()).isEqualTo("r-tok");
        assertThat(r.expiresAt()).isAfter(java.time.Instant.now());
        server.verify();
    }

    @Test
    void refreshRotatesRefreshToken() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/oauth/token/")))
                .andRespond(withSuccess("{\"access_token\":\"a2\",\"refresh_token\":\"r2\","
                        + "\"expires_in\":86400,\"scope\":\"user.info.basic\"}", MediaType.APPLICATION_JSON));

        OAuthRefreshResult r = p.refreshToken(Platform.TIKTOK, "a1", "r1");

        assertThat(r.accessToken()).isEqualTo("a2");
        assertThat(r.refreshToken()).isEqualTo("r2"); // rotado → el servicio lo re-cifra
        server.verify();
    }

    @Test
    void refreshWithInvalidGrantThrowsRevoked() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/oauth/token/")))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\",\"error_description\":\"expired\","
                        + "\"log_id\":\"x\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> p.refreshToken(Platform.TIKTOK, "a1", "expired-refresh"))
                .isInstanceOf(TokenRevokedException.class);
    }

    // ---- TAREA A: nombre real (display_name + @username) vía /v2/user/info/ ----

    @Test
    void exchangeEnrichesNameAndHandleFromUserInfo() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/oauth/token/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"a-tok","refresh_token":"r-tok","open_id":"oid-1",
                         "expires_in":86400,"scope":"user.info.profile","token_type":"Bearer"}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer a-tok"))
                .andRespond(withSuccess("""
                        {"data":{"user":{"open_id":"oid-1","display_name":"Cebando Tertulias",
                         "username":"cebandotertulias","avatar_url":"https://cdn.tiktok/x.jpg"}},
                         "error":{"code":"ok","message":"","log_id":"z1"}}""",
                        MediaType.APPLICATION_JSON));

        OAuthExchangeResult r = p.exchangeCode(Platform.TIKTOK, "auth-code");

        // El id externo sigue siendo el open_id; el nombre/handle ahora son los reales.
        assertThat(r.externalAccountId()).isEqualTo("oid-1");
        assertThat(r.displayName()).isEqualTo("Cebando Tertulias");
        assertThat(r.handle()).isEqualTo("@cebandotertulias");
        server.verify();
    }

    @Test
    void exchangeFallsBackToOpenIdWhenUserInfoFails() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/oauth/token/")))
                .andRespond(withSuccess("""
                        {"access_token":"a-tok","refresh_token":"r-tok","open_id":"oid-9",
                         "expires_in":86400,"scope":"user.info.profile"}""", MediaType.APPLICATION_JSON));
        // user/info cae (401): best-effort → NO rompe el canje, cae al open_id.
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        OAuthExchangeResult r = p.exchangeCode(Platform.TIKTOK, "auth-code");

        assertThat(r.externalAccountId()).isEqualTo("oid-9");
        assertThat(r.handle()).isEqualTo("oid-9");
        assertThat(r.displayName()).isEqualTo("TikTok oid-9");
        server.verify();
    }

    @Test
    void fetchProfileReturnsHandleAndDisplayName() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer a-tok"))
                .andRespond(withSuccess("""
                        {"data":{"user":{"open_id":"oid-1","display_name":"Cebando Tertulias",
                         "username":"cebandotertulias","avatar_url":"https://cdn.tiktok/x.jpg"}},
                         "error":{"code":"ok"}}""", MediaType.APPLICATION_JSON));

        Optional<OAuthProfile> profile = p.fetchProfile(Platform.TIKTOK, "a-tok");

        assertThat(profile).isPresent();
        assertThat(profile.get().handle()).isEqualTo("@cebandotertulias");
        assertThat(profile.get().displayName()).isEqualTo("Cebando Tertulias");
        assertThat(profile.get().avatarUrl()).isEqualTo("https://cdn.tiktok/x.jpg");
        server.verify();
    }

    @Test
    void fetchProfileIsEmptyWhenUserInfoFails() {
        TikTokOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("/v2/user/info/")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(p.fetchProfile(Platform.TIKTOK, "a-tok")).isEmpty();
        server.verify();
    }
}
