package com.filgrama.oauth.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.filgrama.domain.enums.AccountType;
import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthException;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.OAuthProvider;
import com.filgrama.oauth.OAuthRefreshResult;
import com.filgrama.oauth.Platforms;
import com.filgrama.oauth.TokenRevokedException;
import com.filgrama.oauth.config.OAuthProperties;

import tools.jackson.databind.JsonNode;

/**
 * Proveedor real de TikTok (spec/09 §TikTok). Canje: {@code POST /v2/oauth/token/}
 * ({@code grant_type=authorization_code}) → access token (24 h) + refresh token (365 d) + open_id.
 * Refresh: {@code grant_type=refresh_token}; si la API devuelve un refresh token nuevo se
 * <b>rota</b> (el servicio lo re-cifra y persiste). Cualquier tipo de cuenta se conecta.
 *
 * <p>Activo solo fuera de {@code local}/{@code test} (dev/CI usan {@link MockOAuthProvider}). El
 * {@code client_secret} viaja solo en el body server-side y nunca se loguea. El endpoint de token de
 * TikTok puede responder el error tanto como HTTP 4xx como con {@code 200} + cuerpo de error: se
 * cubren ambos.
 */
@Component
@Order(100)
@Profile("!local & !test")
public class TikTokOAuthProvider implements OAuthProvider {

    private static final long ACCESS_TTL_SECONDS = 24L * 3600;

    private final OAuthProperties props;
    private final RestClient http;

    @Autowired
    public TikTokOAuthProvider(OAuthProperties props) {
        this(props, OAuthHttpSupport.builder());
    }

    /** Visible para tests: {@link RestClient} apuntado a un servidor HTTP mock. */
    TikTokOAuthProvider(OAuthProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.TIKTOK;
    }

    @Override
    public String buildAuthorizationUrl(Platform platform, String state) {
        OAuthProperties.TikTok tk = props.getTiktok();
        return UriComponentsBuilder.fromUriString(tk.getAuthorizeUrl())
                .queryParam("client_key", tk.getClientKey())
                .queryParam("scope", String.join(",", tk.getScopes()))
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri(platform))
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    @Override
    public OAuthExchangeResult exchangeCode(Platform platform, String code) {
        OAuthProperties.TikTok tk = props.getTiktok();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", tk.getClientKey());
        form.add("client_secret", tk.getClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri(platform));

        JsonNode tok = post(tk.getTokenUrl(), form, false);
        String openId = require(OAuthHttpSupport.text(tok, "open_id"), "TikTok no devolvió open_id");
        return new OAuthExchangeResult(
                openId,
                openId,
                "TikTok " + openId,
                AccountType.CREATOR,
                "{\"source\":\"tiktok\"}",
                require(OAuthHttpSupport.text(tok, "access_token"), "TikTok no devolvió access_token"),
                OAuthHttpSupport.text(tok, "refresh_token"),
                "bearer",
                OAuthHttpSupport.text(tok, "scope"),
                OAuthHttpSupport.expiresAt(tok, "expires_in", ACCESS_TTL_SECONDS));
    }

    @Override
    public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
        OAuthProperties.TikTok tk = props.getTiktok();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", tk.getClientKey());
        form.add("client_secret", tk.getClientSecret());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        JsonNode tok = post(tk.getTokenUrl(), form, true);
        return new OAuthRefreshResult(
                require(OAuthHttpSupport.text(tok, "access_token"), "TikTok no devolvió access_token"),
                OAuthHttpSupport.text(tok, "refresh_token"), // rotado → el servicio lo re-cifra y guarda
                "bearer",
                OAuthHttpSupport.text(tok, "scope"),
                OAuthHttpSupport.expiresAt(tok, "expires_in", ACCESS_TTL_SECONDS));
    }

    // ---- HTTP / errores ----

    /** POST form-urlencoded con 1 reintento ante transitorios; valida el envelope de error de TikTok. */
    private JsonNode post(String url, MultiValueMap<String, String> form, boolean authIsRevoked) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String body = http.post().uri(url)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve().body(String.class);
                JsonNode tree = OAuthHttpSupport.tree(body);
                RuntimeException mapped = mapError(tree, authIsRevoked); // 200 + cuerpo de error
                if (mapped != null) {
                    throw mapped;
                }
                return tree;
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                if (OAuthHttpSupport.isTransient(status) && attempt < 2) {
                    continue;
                }
                RuntimeException mapped = mapError(safeTree(e.getResponseBodyAsString()), authIsRevoked);
                throw mapped != null ? mapped
                        : new OAuthException("TikTok respondió " + status.value() + " en el flujo OAuth");
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    continue;
                }
                throw new OAuthException("TikTok: timeout/IO en el flujo OAuth", e);
            }
        }
    }

    /**
     * Mapea el error de TikTok a excepción, o {@code null} si no hay error. El token endpoint usa
     * {@code error} (string) + {@code error_description}; los endpoints de negocio, {@code error.code}.
     * Un error de auth en el refresh ⇒ {@link TokenRevokedException}.
     */
    private RuntimeException mapError(JsonNode tree, boolean authIsRevoked) {
        JsonNode err = tree.path("error");
        String code = null;
        if (err.isObject()) {
            code = OAuthHttpSupport.text(err, "code");
            if (code == null || code.isBlank() || "ok".equalsIgnoreCase(code)) {
                return null;
            }
        } else if (!err.isMissingNode() && !err.isNull()) {
            code = err.asText();
            if (code == null || code.isBlank()) {
                return null;
            }
        } else {
            return null;
        }
        if (authIsRevoked && isAuthError(code)) {
            return new TokenRevokedException("TikTok: refresh token inválido o revocado");
        }
        return new OAuthException("TikTok rechazó el canje/refresh (" + code + ")");
    }

    private boolean isAuthError(String code) {
        String c = code.toLowerCase();
        return c.contains("invalid_grant") || c.contains("invalid_token")
                || c.contains("access_token_invalid") || c.contains("refresh_token_invalid")
                || c.contains("token_revoked") || c.contains("unauthorized");
    }

    private JsonNode safeTree(String body) {
        try {
            return OAuthHttpSupport.tree(body);
        } catch (RuntimeException e) {
            return OAuthHttpSupport.tree("{}");
        }
    }

    private String redirectUri(Platform platform) {
        return props.getRedirectBaseUri() + "/api/v1/oauth/callback/" + Platforms.path(platform);
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OAuthException(message);
        }
        return value;
    }
}
