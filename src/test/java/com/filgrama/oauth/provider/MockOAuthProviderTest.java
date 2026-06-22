package com.filgrama.oauth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthException;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.TokenRevokedException;

class MockOAuthProviderTest {

    private final MockOAuthProvider mock = new MockOAuthProvider();

    @Test
    void authorizationUrlCarriesState() {
        assertThat(mock.buildAuthorizationUrl(Platform.TIKTOK, "abc123")).contains("state=abc123");
    }

    @Test
    void exchangeDefaultsToBusinessWithToken() {
        OAuthExchangeResult r = mock.exchangeCode(Platform.FACEBOOK, "good-code");
        assertThat(r.accountType()).isEqualTo(AccountType.BUSINESS);
        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.externalAccountId()).isNotBlank();
    }

    @Test
    void personalCodeDetectedAsPersonal() {
        assertThat(mock.exchangeCode(Platform.INSTAGRAM, "personal-1").accountType())
                .isEqualTo(AccountType.PERSONAL);
    }

    @Test
    void tiktokExchangeHasRefreshToken() {
        assertThat(mock.exchangeCode(Platform.TIKTOK, "c").refreshToken()).isNotNull();
    }

    @Test
    void revokedCodeThrows() {
        assertThatThrownBy(() -> mock.exchangeCode(Platform.TIKTOK, "revoked"))
                .isInstanceOf(TokenRevokedException.class);
    }

    @Test
    void blankCodeThrows() {
        assertThatThrownBy(() -> mock.exchangeCode(Platform.TIKTOK, ""))
                .isInstanceOf(OAuthException.class);
    }
}
