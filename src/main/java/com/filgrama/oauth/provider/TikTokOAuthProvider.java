package com.filgrama.oauth.provider;

import java.time.Instant;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
 * Proveedor real de TikTok. spec/09 §TikTok: code → {@code POST /v2/oauth/token/}
 * (access 24 h + refresh 365 d + open_id). Refresh con {@code grant_type=refresh_token}
 * (rotando el refresh si la API devuelve uno nuevo). Cualquier tipo de cuenta se conecta.
 *
 * <p>Scaffolding: en dev/test lo intercepta {@code MockOAuthProvider}. Producción requiere
 * app client auditado.
 */
@Component
@Order(100)
public class TikTokOAuthProvider implements OAuthProvider {

    private final OAuthProperties props;
    private final RestClient http = RestClient.create();

    public TikTokOAuthProvider(OAuthProperties props) {
        this.props = props;
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
        Map<?, ?> tok = post(tk.getTokenUrl(), form);

        String openId = str(tok, "open_id");
        return new OAuthExchangeResult(
                openId,
                openId,
                "TikTok " + openId,
                AccountType.CREATOR,
                "{\"source\":\"tiktok\"}",
                str(tok, "access_token"),
                str(tok, "refresh_token"),
                "bearer",
                str(tok, "scope"),
                expiresFrom(tok, "expires_in", 24L * 3600));
    }

    @Override
    public OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken) {
        OAuthProperties.TikTok tk = props.getTiktok();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", tk.getClientKey());
        form.add("client_secret", tk.getClientSecret());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        Map<?, ?> tok = post(tk.getTokenUrl(), form);

        return new OAuthRefreshResult(
                str(tok, "access_token"),
                str(tok, "refresh_token"), // rotado → el servicio lo re-cifra y guarda
                "bearer",
                str(tok, "scope"),
                expiresFrom(tok, "expires_in", 24L * 3600));
    }

    private Map<?, ?> post(String url, MultiValueMap<String, String> form) {
        try {
            return http.post().uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new OAuthException("Falló el canje/refresh con TikTok", e);
        }
    }

    private String redirectUri(Platform platform) {
        return props.getRedirectBaseUri() + "/api/v1/oauth/callback/" + Platforms.path(platform);
    }

    private static String str(Map<?, ?> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : v.toString();
    }

    private static Instant expiresFrom(Map<?, ?> tok, String key, long defaultSeconds) {
        Object exp = tok == null ? null : tok.get(key);
        long seconds = exp instanceof Number n ? n.longValue() : defaultSeconds;
        return Instant.now().plusSeconds(seconds);
    }
}
