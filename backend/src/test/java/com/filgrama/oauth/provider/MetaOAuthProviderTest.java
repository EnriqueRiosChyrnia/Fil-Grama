package com.filgrama.oauth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.TokenRevokedException;
import com.filgrama.oauth.config.OAuthProperties;

/**
 * Tests del provider real de Meta (Camino A) con el Graph API mockeado a nivel HTTP: canje
 * FB/IG, detección de cuenta personal y refresh (incl. token revocado). Nunca pega a la red.
 */
class MetaOAuthProviderTest {

    private MockRestServiceServer server;

    private OAuthProperties props() {
        OAuthProperties props = new OAuthProperties();
        props.setRedirectBaseUri("http://localhost:8080");
        props.getMeta().setAppId("app");
        props.getMeta().setAppSecret("secret"); // nunca debe loguearse
        return props;
    }

    private MetaOAuthProvider provider(OAuthProperties props) {
        RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        return new MetaOAuthProvider(props, builder);
    }

    private void expectShortAndLong() {
        server.expect(requestTo(containsString("code=auth-code")))
                .andRespond(withSuccess("{\"access_token\":\"short-user\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("grant_type=fb_exchange_token")))
                .andRespond(withSuccess("{\"access_token\":\"long-user\",\"expires_in\":5184000}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void exchangeFacebookBusinessUsesPageToken() {
        MetaOAuthProvider p = provider(props());
        expectShortAndLong();
        server.expect(requestTo(containsString("/me/accounts")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"PAGE1\",\"name\":\"My Page\","
                        + "\"access_token\":\"page-tok\"}]}", MediaType.APPLICATION_JSON));

        OAuthExchangeResult r = p.exchangeCode(Platform.FACEBOOK, "auth-code");

        assertThat(r.accountType()).isEqualTo(AccountType.BUSINESS);
        assertThat(r.externalAccountId()).isEqualTo("PAGE1");
        assertThat(r.accessToken()).isEqualTo("page-tok");
        assertThat(r.displayName()).isEqualTo("My Page");
        server.verify();
    }

    @Test
    void exchangeInstagramResolvesLinkedIgAccount() {
        MetaOAuthProvider p = provider(props());
        expectShortAndLong();
        server.expect(requestTo(containsString("/me/accounts")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"PAGE1\",\"name\":\"My Page\","
                        + "\"access_token\":\"page-tok\",\"instagram_business_account\":{\"id\":\"IG777\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("IG777?fields=username")))
                .andRespond(withSuccess("{\"username\":\"demo_ig\",\"id\":\"IG777\"}", MediaType.APPLICATION_JSON));

        OAuthExchangeResult r = p.exchangeCode(Platform.INSTAGRAM, "auth-code");

        assertThat(r.accountType()).isEqualTo(AccountType.BUSINESS);
        assertThat(r.externalAccountId()).isEqualTo("IG777");
        assertThat(r.handle()).isEqualTo("@demo_ig");
        assertThat(r.accessToken()).isEqualTo("page-tok");
        server.verify();
    }

    @Test
    void exchangeWithoutPagesIsPersonal() {
        MetaOAuthProvider p = provider(props());
        expectShortAndLong();
        server.expect(requestTo(containsString("/me/accounts")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/me?fields=id,name")))
                .andRespond(withSuccess("{\"id\":\"USER1\",\"name\":\"Personal User\"}",
                        MediaType.APPLICATION_JSON));

        OAuthExchangeResult r = p.exchangeCode(Platform.FACEBOOK, "auth-code");

        assertThat(r.accountType()).isEqualTo(AccountType.PERSONAL);
        assertThat(r.externalAccountId()).isEqualTo("USER1");
        server.verify();
    }

    @Test
    void refreshReExchangesLongLivedToken() {
        MetaOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("grant_type=fb_exchange_token")))
                .andRespond(withSuccess("{\"access_token\":\"renewed\",\"expires_in\":5184000}",
                        MediaType.APPLICATION_JSON));

        OAuthRefreshResult r = p.refreshToken(Platform.FACEBOOK, "old-token", null);

        assertThat(r.accessToken()).isEqualTo("renewed");
        assertThat(r.expiresAt()).isAfter(java.time.Instant.now());
        server.verify();
    }

    @Test
    void refreshWithRevokedTokenThrows() {
        MetaOAuthProvider p = provider(props());
        server.expect(requestTo(containsString("grant_type=fb_exchange_token")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"invalid token\",\"type\":\"OAuthException\",\"code\":190}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> p.refreshToken(Platform.FACEBOOK, "revoked-token", null))
                .isInstanceOf(TokenRevokedException.class);
    }
}
