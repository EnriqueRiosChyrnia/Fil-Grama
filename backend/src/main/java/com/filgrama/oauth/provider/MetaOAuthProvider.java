package com.filgrama.oauth.provider;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
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
 * Proveedor real de Meta (Instagram + Facebook) — <b>Camino A: Facebook Login for Business</b>
 * (spec/09 §Meta). Flujo de canje: {@code code} → user token corto → long-lived (60 d) →
 * {@code /me/accounts} (Page tokens + IG Business id vinculado). Detecta cuenta personal
 * (sin Página / sin IG profesional vinculado) → {@code PERSONAL} para que el callback la marque
 * {@code UNSUPPORTED} sin crear credencial.
 *
 * <p>Activo solo fuera de {@code local}/{@code test} (perfiles dev/CI usan {@link MockOAuthProvider}).
 * El {@code app_secret} viaja solo en estas llamadas server-side y nunca se loguea.
 *
 * <p><b>Camino B (Instagram Login, IG-only sin Página)</b> queda pendiente: requiere credenciales y
 * scopes propios de IG ({@code oauth.instagram.*}, host {@code graph.instagram.com}) que la central
 * aún no cableó. Documentado en el reporte del track.
 */
@Component
@Order(100)
@Profile("!local & !test")
public class MetaOAuthProvider implements OAuthProvider {

    private final OAuthProperties props;
    private final RestClient http;

    @Autowired
    public MetaOAuthProvider(OAuthProperties props) {
        this(props, OAuthHttpSupport.builder());
    }

    /** Visible para tests: permite inyectar un {@link RestClient} apuntado a un servidor HTTP mock. */
    MetaOAuthProvider(OAuthProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.build();
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

        // 1) code → user token corto
        JsonNode shortTok = getJson(false, () -> meta.getGraphUrl() + "/oauth/access_token"
                + "?client_id=" + enc(meta.getAppId())
                + "&client_secret=" + enc(meta.getAppSecret())
                + "&redirect_uri=" + enc(redirectUri(platform))
                + "&code=" + enc(code));
        String shortToken = require(OAuthHttpSupport.text(shortTok, "access_token"),
                "Meta no devolvió access_token corto");

        // 2) corto → long-lived user token (60 d)
        JsonNode longTok = getJson(false, () -> meta.getGraphUrl() + "/oauth/access_token"
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + enc(meta.getAppId())
                + "&client_secret=" + enc(meta.getAppSecret())
                + "&fb_exchange_token=" + enc(shortToken));
        String userToken = require(OAuthHttpSupport.text(longTok, "access_token"),
                "Meta no devolvió long-lived token");
        Instant expiresAt = OAuthHttpSupport.expiresAt(longTok, "expires_in", 60L * 24 * 3600);

        // 3) /me/accounts → Páginas + IG Business vinculado por Página
        JsonNode accounts = getJson(false, () -> meta.getGraphUrl() + "/me/accounts"
                + "?fields=id,name,access_token,instagram_business_account"
                + "&access_token=" + enc(userToken));
        JsonNode data = accounts.path("data");

        return platform == Platform.INSTAGRAM
                ? resolveInstagram(meta, data, userToken, expiresAt)
                : resolveFacebook(data, userToken, expiresAt, meta);
    }

    /** Facebook: primera Página con su Page token (larga duración). Sin Páginas ⇒ personal. */
    private OAuthExchangeResult resolveFacebook(JsonNode pages, String userToken, Instant expiresAt,
            OAuthProperties.Meta meta) {
        if (!pages.isArray() || pages.isEmpty()) {
            return personal(meta, userToken, expiresAt);
        }
        JsonNode page = pages.path(0);
        String pageId = OAuthHttpSupport.text(page, "id");
        String pageToken = OAuthHttpSupport.text(page, "access_token");
        String name = OAuthHttpSupport.text(page, "name");
        return new OAuthExchangeResult(
                require(pageId, "Página sin id"), name, name, AccountType.BUSINESS,
                "{\"source\":\"meta\",\"login\":\"facebook_business\",\"pageId\":\"" + json(pageId) + "\"}",
                pageToken != null ? pageToken : userToken, null, "bearer",
                String.join(",", meta.getScopes()), expiresAt);
    }

    /** Instagram: primera Página con {@code instagram_business_account}. Sin IG profesional ⇒ personal. */
    private OAuthExchangeResult resolveInstagram(OAuthProperties.Meta meta, JsonNode pages,
            String userToken, Instant expiresAt) {
        if (pages.isArray()) {
            for (JsonNode page : pages) {
                JsonNode ig = page.path("instagram_business_account");
                String igId = OAuthHttpSupport.text(ig, "id");
                if (igId == null) {
                    continue;
                }
                String pageId = OAuthHttpSupport.text(page, "id");
                String pageToken = OAuthHttpSupport.text(page, "access_token");
                String token = pageToken != null ? pageToken : userToken;
                String username = igUsername(meta, igId, token);
                String handle = username != null ? "@" + username : OAuthHttpSupport.text(page, "name");
                return new OAuthExchangeResult(
                        igId, handle, username != null ? username : OAuthHttpSupport.text(page, "name"),
                        AccountType.BUSINESS,
                        "{\"source\":\"meta\",\"login\":\"facebook_business\",\"pageId\":\"" + json(pageId)
                                + "\",\"igUserId\":\"" + json(igId) + "\"}",
                        token, null, "bearer", String.join(",", meta.getScopes()), expiresAt);
            }
        }
        return personal(meta, userToken, expiresAt);
    }

    /** {@code username} de la cuenta IG (best-effort); si falla, no rompe el canje. */
    private String igUsername(OAuthProperties.Meta meta, String igId, String token) {
        try {
            JsonNode node = getJson(false, () -> meta.getGraphUrl() + "/" + enc(igId)
                    + "?fields=username&access_token=" + enc(token));
            return OAuthHttpSupport.text(node, "username");
        } catch (OAuthException e) {
            return null;
        }
    }

    /** Cuenta personal (sin Página/IG profesional): id estable del usuario, sin token operable. */
    private OAuthExchangeResult personal(OAuthProperties.Meta meta, String userToken, Instant expiresAt) {
        JsonNode me = getJson(false, () -> meta.getGraphUrl() + "/me"
                + "?fields=id,name&access_token=" + enc(userToken));
        String userId = OAuthHttpSupport.text(me, "id");
        String name = OAuthHttpSupport.text(me, "name");
        return new OAuthExchangeResult(
                require(userId, "Meta no devolvió el id del usuario"), name, name,
                AccountType.PERSONAL, "{\"source\":\"meta\",\"accountType\":\"personal\"}",
                userToken, null, "bearer", String.join(",", meta.getScopes()), expiresAt);
    }

    @Override
    public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
        OAuthProperties.Meta meta = props.getMeta();
        // Re-exchange del user token de larga duración (renueva los Page tokens derivados).
        JsonNode longTok = getJson(true, () -> meta.getGraphUrl() + "/oauth/access_token"
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + enc(meta.getAppId())
                + "&client_secret=" + enc(meta.getAppSecret())
                + "&fb_exchange_token=" + enc(accessToken));
        String renewed = require(OAuthHttpSupport.text(longTok, "access_token"),
                "Meta no devolvió token en el refresh");
        return new OAuthRefreshResult(renewed, null, "bearer", null,
                OAuthHttpSupport.expiresAt(longTok, "expires_in", 60L * 24 * 3600));
    }

    // ---- HTTP / errores ----

    /**
     * GET → árbol JSON con 1 reintento ante transitorios (5xx/429/timeout). {@code authIsRevoked}:
     * en el refresh, un error de auth de Meta (code 190, etc.) significa token revocado.
     */
    private JsonNode getJson(boolean authIsRevoked, Supplier<String> urlSupplier) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String body = http.get().uri(urlSupplier.get()).retrieve().body(String.class);
                return OAuthHttpSupport.tree(body);
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                if (OAuthHttpSupport.isTransient(status) && attempt < 2) {
                    continue;
                }
                if (authIsRevoked && isAuthError(e)) {
                    throw new TokenRevokedException("Meta: autorización inválida o revocada");
                }
                throw new OAuthException("Meta respondió " + status.value() + " en el flujo OAuth");
            } catch (ResourceAccessException e) {
                if (attempt < 2) {
                    continue;
                }
                throw new OAuthException("Meta: timeout/IO en el flujo OAuth", e);
            }
        }
    }

    /** ¿El error de Meta es de autorización (token inválido/expirado/permiso)? code 190 / 102 / 10 / 2xx. */
    private boolean isAuthError(RestClientResponseException e) {
        try {
            int code = OAuthHttpSupport.tree(e.getResponseBodyAsString()).path("error").path("code").asInt(0);
            return code == 190 || code == 102 || code == 10 || (code >= 200 && code <= 299);
        } catch (RuntimeException ignore) {
            return e.getStatusCode().value() == 401;
        }
    }

    private String redirectUri(Platform platform) {
        return props.getRedirectBaseUri() + "/api/v1/oauth/callback/" + Platforms.path(platform);
    }

    private static String enc(String raw) {
        return raw == null ? "" : java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Escapa un id para incrustarlo en el JSON de capabilities (ids Graph son simples, pero por las dudas). */
    private static String json(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OAuthException(message);
        }
        return value;
    }
}
