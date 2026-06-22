package com.filgrama.oauth;

import com.filgrama.domain.enums.Platform;

/**
 * Abstracción del proveedor OAuth de cada red. Permite mockear el flujo completo
 * en dev/test (sin App Review) detrás de una interfaz: {@code MockOAuthProvider}
 * simula el canje y la implementación real ({@code MetaOAuthProvider},
 * {@code TikTokOAuthProvider}) golpea la API oficial.
 *
 * <p>Todo el canje ocurre <b>server-side</b>; el {@code client_secret} jamás sale
 * de acá. Los tokens devueltos los cifra el servicio antes de persistir.
 */
public interface OAuthProvider {

    /** ¿Este proveedor atiende esta red? */
    boolean supports(Platform platform);

    /**
     * Arma la URL oficial de autorización (con scopes + {@code state}) que el front
     * abre para que el cliente autorice en la pantalla de la red.
     */
    String buildAuthorizationUrl(Platform platform, String state);

    /**
     * Canjea el {@code code} del callback por un token de larga duración y detecta
     * tipo de cuenta + capabilities. Lanza {@link OAuthException} si el canje falla
     * y {@link TokenRevokedException} si la autorización fue revocada.
     */
    OAuthExchangeResult exchangeCode(Platform platform, String code);

    /**
     * Refresca/re-canjea el token. Meta usa el access token de larga duración
     * (re-exchange / ig_refresh_token); TikTok usa el refresh token (y puede rotar
     * uno nuevo). El refresh token puede ser {@code null} para redes que no lo usan.
     */
    OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken);
}
