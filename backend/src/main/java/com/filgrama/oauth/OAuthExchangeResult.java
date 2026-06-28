package com.filgrama.oauth;

import java.time.Instant;

import com.filgrama.domain.enums.AccountType;

/**
 * Resultado del canje del {@code code} server-side: identidad de la cuenta +
 * tokens en claro. El servicio los cifra antes de persistir; <b>nunca</b> se
 * serializan al front ni se loguean.
 *
 * @param externalAccountId id de la cuenta en la red (parte del UNIQUE)
 * @param handle            @usuario / nombre corto
 * @param displayName       nombre visible
 * @param accountType       PERSONAL / CREATOR / BUSINESS (gatea UNSUPPORTED en Meta)
 * @param capabilities      JSON con métricas/endpoints soportados
 * @param accessToken       token de acceso en claro (a cifrar)
 * @param refreshToken      refresh token en claro o {@code null}
 * @param tokenType         ej. "bearer"
 * @param scopes            scopes concedidos (csv)
 * @param expiresAt         expiración del access token
 * @param metaUserId        id del usuario Meta que autoriza (de {@code /me}); lo necesitan
 *                          Deauthorize/Data Deletion para ubicar las filas. {@code null} en TikTok/Mock.
 */
public record OAuthExchangeResult(
        String externalAccountId,
        String handle,
        String displayName,
        AccountType accountType,
        String capabilities,
        String accessToken,
        String refreshToken,
        String tokenType,
        String scopes,
        Instant expiresAt,
        String metaUserId) {

    /**
     * Back-compat para redes sin {@code meta_user_id} (TikTok/Mock): delega con {@code metaUserId = null}.
     * Meta usa el constructor canónico de 11 args.
     */
    public OAuthExchangeResult(String externalAccountId, String handle, String displayName,
            AccountType accountType, String capabilities, String accessToken, String refreshToken,
            String tokenType, String scopes, Instant expiresAt) {
        this(externalAccountId, handle, displayName, accountType, capabilities, accessToken,
                refreshToken, tokenType, scopes, expiresAt, null);
    }
}
