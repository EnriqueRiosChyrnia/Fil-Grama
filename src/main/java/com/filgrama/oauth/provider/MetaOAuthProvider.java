package com.filgrama.oauth.provider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthException;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProvider;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.Platforms;
import com.filgrama.oauth.config.OAuthProperties;

/**
 * Proveedor real de Meta (Instagram + Facebook) vía Facebook Login for Business.
 * spec/09 §Meta: canje code → user token corto → long-lived (60 d) → {@code /me/accounts}
 * (Page tokens + IG Business id). Sin Página/IG profesional → cuenta personal → UNSUPPORTED.
 *
 * <p>Scaffolding: en dev/test lo intercepta {@code MockOAuthProvider}. Producción requiere
 * App Review (Advanced Access + Business Verification) y el paso de selección multi-cuenta
 * cuando {@code /me/accounts} devuelve varias Páginas.
 */
@Component
@Order(100)
public class MetaOAuthProvider implements OAuthProvider {

    private final OAuthProperties props;
    private final RestClient http = RestClient.create();

    public MetaOAuthProvider(OAuthProperties props) {
        this.props = props;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK;
    }

    @Override
    public String buildAuthorizationUrl(Platform platform, String state) {
        OAuthProperties.Meta meta = props.getMeta();
        return UriComponentsBuilder.fromUriString(meta.getAuthorizeUrl())
                .queryParam("client_id", meta.getAppId())
                .queryParam("redirect_uri", redirectUri(platform))
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(",", meta.getScopes()))
                .encode()
                .toUriString();
    }

    @Override
    public OAuthExchangeResult exchangeCode(Platform platform, String code) {
        OAuthProperties.Meta meta = props.getMeta();
        try {
            // 1) code → user token corto
            Map<?, ?> shortTok = http.get().uri(meta.getGraphUrl() + "/oauth/access_token",
                    uri -> uri.queryParam("client_id", meta.getAppId())
                            .queryParam("client_secret", meta.getAppSecret())
                            .queryParam("redirect_uri", redirectUri(platform))
                            .queryParam("code", code).build())
                    .retrieve().body(Map.class);
            String shortToken = str(shortTok, "access_token");

            // 2) corto → long-lived (60 d)
            Map<?, ?> longTok = http.get().uri(meta.getGraphUrl() + "/oauth/access_token",
                    uri -> uri.queryParam("grant_type", "fb_exchange_token")
                            .queryParam("client_id", meta.getAppId())
                            .queryParam("client_secret", meta.getAppSecret())
                            .queryParam("fb_exchange_token", shortToken).build())
                    .retrieve().body(Map.class);
            String userToken = str(longTok, "access_token");
            Instant expiresAt = expiresFrom(longTok);

            // 3) /me/accounts → Páginas + IG vinculado. Sin Páginas ⇒ cuenta personal.
            Map<?, ?> accounts = http.get().uri(meta.getGraphUrl() + "/me/accounts",
                    uri -> uri.queryParam("access_token", userToken).build())
                    .retrieve().body(Map.class);
            List<?> data = accounts.get("data") instanceof List<?> l ? l : List.of();
            if (data.isEmpty()) {
                // personal: el callback la marca UNSUPPORTED sin credencial
                return new OAuthExchangeResult("me", null, null, AccountType.PERSONAL,
                        "{}", userToken, null, "bearer", String.join(",", meta.getScopes()), expiresAt);
            }
            Map<?, ?> page = (Map<?, ?>) data.get(0);
            String externalId = str(page, "id");
            String pageToken = str(page, "access_token");
            String name = str(page, "name");
            return new OAuthExchangeResult(externalId, name, name, AccountType.BUSINESS,
                    "{\"source\":\"meta\"}", pageToken != null ? pageToken : userToken, null,
                    "bearer", String.join(",", meta.getScopes()), expiresAt);
        } catch (RestClientException e) {
            throw new OAuthException("Falló el canje con Meta", e);
        }
    }

    @Override
    public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
        OAuthProperties.Meta meta = props.getMeta();
        try {
            // Re-exchange del user token de larga duración (renueva los Page tokens derivados).
            Map<?, ?> longTok = http.get().uri(meta.getGraphUrl() + "/oauth/access_token",
                    uri -> uri.queryParam("grant_type", "fb_exchange_token")
                            .queryParam("client_id", meta.getAppId())
                            .queryParam("client_secret", meta.getAppSecret())
                            .queryParam("fb_exchange_token", accessToken).build())
                    .retrieve().body(Map.class);
            return new OAuthRefreshResult(str(longTok, "access_token"), null, "bearer",
                    null, expiresFrom(longTok));
        } catch (RestClientException e) {
            throw new OAuthException("Falló el refresh con Meta", e);
        }
    }

    private String redirectUri(Platform platform) {
        return props.getRedirectBaseUri() + "/api/v1/oauth/callback/" + Platforms.path(platform);
    }

    private static String str(Map<?, ?> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : v.toString();
    }

    private static Instant expiresFrom(Map<?, ?> tok) {
        Object exp = tok == null ? null : tok.get("expires_in");
        long seconds = exp instanceof Number n ? n.longValue() : 60L * 24 * 3600; // default 60 d
        return Instant.now().plusSeconds(seconds);
    }
}
