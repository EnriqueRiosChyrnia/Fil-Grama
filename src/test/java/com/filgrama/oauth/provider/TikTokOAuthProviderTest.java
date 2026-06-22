package com.filgrama.oauth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthExchangeResult;
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
}
