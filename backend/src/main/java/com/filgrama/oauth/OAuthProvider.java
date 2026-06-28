package com.filgrama.oauth;

import java.util.List;
import java.util.Optional;

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
     * Igual que {@link #buildAuthorizationUrl(Platform, String)} pero pudiendo <b>forzar la pantalla
     * de consentimiento</b> ({@code forceConsent}). En TikTok agrega {@code disable_auto_auth=1} para
     * evitar el auto-grant silencioso de la sesión activa (reconexión / dev). El default delega en la
     * variante de 2 args para no romper a las redes que no lo soportan (Meta/Mock). spec/09 §TikTok.
     */
    default String buildAuthorizationUrl(Platform platform, String state, boolean forceConsent) {
        return buildAuthorizationUrl(platform, state);
    }

    /**
     * Canjea el {@code code} del callback por un token de larga duración y detecta
     * tipo de cuenta + capabilities. Lanza {@link OAuthException} si el canje falla
     * y {@link TokenRevokedException} si la autorización fue revocada.
     */
    OAuthExchangeResult exchangeCode(Platform platform, String code);

    /**
     * Igual que {@link #exchangeCode(Platform, String)} pero recibiendo el {@code state} de la
     * autorización, necesario para recuperar el {@code code_verifier} de PKCE (TikTok lo exige;
     * spec/09 §TikTok). El default delega en la variante de 2 args para las redes sin PKCE (Meta/Mock).
     */
    default OAuthExchangeResult exchangeCode(Platform platform, String code, String state) {
        return exchangeCode(platform, code);
    }

    /**
     * Canjea el {@code code} y devuelve <b>todas</b> las cuentas elegibles de ese consentimiento
     * (spec/09 §Multi-cuenta por red). El default envuelve {@link #exchangeCode(Platform, String, String)}
     * en una lista de un elemento (TikTok/Mock: un consentimiento = una cuenta). {@code MetaOAuthProvider}
     * lo sobreescribe devolviendo todas las Páginas (FB) o todas las Páginas con IG profesional (IG) de
     * {@code /me/accounts}, para que el callback ofrezca un <b>paso de selección</b> cuando hay más de una.
     */
    default List<OAuthExchangeResult> exchangeCandidates(Platform platform, String code, String state) {
        return List.of(exchangeCode(platform, code, state));
    }

    /**
     * Refresca/re-canjea el token. Meta usa el access token de larga duración
     * (re-exchange / ig_refresh_token); TikTok usa el refresh token (y puede rotar
     * uno nuevo). El refresh token puede ser {@code null} para redes que no lo usan.
     */
    OAuthRefreshResult refreshToken(Platform platform, String accessToken, String refreshToken);

    /**
     * Perfil público actualizado de la cuenta (nombre visible / {@code handle} / avatar) a partir del
     * access token vigente. <b>Best-effort</b>: el default no consulta nada ({@link Optional#empty()}),
     * y los providers que exponen un endpoint de user-info lo sobreescriben (TikTok). Lo invocan el
     * canje y el refresh/sync para corregir el nombre sin reconectar; si falla, no debe romper el flujo.
     */
    default Optional<OAuthProfile> fetchProfile(Platform platform, String accessToken) {
        return Optional.empty();
    }

    /**
     * Revoca el acceso en la red durante la baja de cuenta (spec/09 §Ciclo de vida). <b>Best-effort</b>:
     * cualquier falla (red, 4xx, envelope de error) se loguea y <b>no</b> se propaga — la baja borra la
     * credencial local igual. El default es no-op (Meta/Mock; revocar Meta es opcional en v1); TikTok lo
     * sobreescribe ({@code POST /v2/oauth/revoke/}).
     */
    default void revokeToken(Platform platform, String accessToken, String refreshToken) {
    }
}
