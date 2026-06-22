package com.filgrama.oauth.provider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProvider;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.Platforms;
import com.filgrama.oauth.TokenRevokedException;

/**
 * Proveedor mockeado para dev/test sin App Review: simula el canje y devuelve token
 * fake + datos de cuenta. Activo en perfiles {@code local}/{@code test} y con precedencia
 * máxima, así intercepta todas las redes. El flujo end-to-end corre 100% con este mock.
 *
 * <p>Convenciones del {@code code} para ejercitar casos de borde en tests:
 * <ul>
 *   <li>{@code "personal..."} → cuenta PERSONAL (gatea UNSUPPORTED en Meta).</li>
 *   <li>{@code "revoked"} → {@link TokenRevokedException}.</li>
 *   <li>vacío/nulo → canje fallido.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile({"local", "test"})
public class MockOAuthProvider implements OAuthProvider {

    @Override
    public boolean supports(Platform platform) {
        return true;
    }

    @Override
    public String buildAuthorizationUrl(Platform platform, String state) {
        return "https://mock.oauth.local/" + Platforms.path(platform) + "/authorize?state=" + state;
    }

    @Override
    public OAuthExchangeResult exchangeCode(Platform platform, String code) {
        if (code == null || code.isBlank()) {
            throw new com.filgrama.oauth.OAuthException("code vacío (mock)");
        }
        if ("revoked".equalsIgnoreCase(code)) {
            throw new TokenRevokedException("autorización revocada (mock)");
        }
        boolean personal = code.toLowerCase().startsWith("personal");
        AccountType type = personal
                ? AccountType.PERSONAL
                : (platform == Platform.TIKTOK ? AccountType.CREATOR : AccountType.BUSINESS);

        boolean tiktok = platform == Platform.TIKTOK;
        Instant expiresAt = Instant.now().plus(tiktok ? 24 : 60 * 24, ChronoUnit.HOURS);
        // id externo determinístico por (red, code) → permite probar el upsert/re-conexión.
        String externalId = "mock-" + Platforms.path(platform) + "-" + Integer.toHexString(code.hashCode());

        return new OAuthExchangeResult(
                externalId,
                "@" + Platforms.path(platform) + "_demo",
                "Demo " + platform.name(),
                type,
                "{\"source\":\"mock\",\"metrics\":[\"reach\",\"followers\"]}",
                "mock-access-" + code,
                tiktok ? "mock-refresh-" + code : null,
                "bearer",
                "mock_scope",
                expiresAt);
    }

    @Override
    public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
        boolean tiktok = platform == Platform.TIKTOK;
        Instant expiresAt = Instant.now().plus(tiktok ? 24 : 60 * 24, ChronoUnit.HOURS);
        // TikTok puede rotar el refresh token; Meta no usa refresh token.
        String rotated = tiktok ? "mock-refresh-rotated" : null;
        return new OAuthRefreshResult("mock-access-refreshed", rotated, "bearer", "mock_scope", expiresAt);
    }
}
