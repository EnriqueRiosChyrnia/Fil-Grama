package com.filgrama.sync.job;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.filgrama.account.service.AccountService;
import com.filgrama.domain.AccountCredential;
import com.filgrama.domain.SocialAccount;
import com.filgrama.oauth.crypto.TokenCipher;
import com.filgrama.repository.AccountCredentialRepository;
import com.filgrama.sync.capture.InsightsException;

/**
 * Adaptador a la lógica de credenciales del track C (NO se edita C). Resuelve el access token en
 * claro para una cuenta y, si está por expirar, lo refresca antes de consultar la API.
 *
 * <p>Reusa: {@link TokenCipher#decrypt(byte[])} (AES-GCM) + {@link AccountCredentialRepository}
 * para el token descifrado, y {@link AccountService#refreshToken(Long)} para el refresh completo
 * (vuelve a llamar al provider, re-cifra y actualiza {@code last_refreshed_at}).
 *
 * <p><b>Gap documentado:</b> C no expone un "obtener token descifrado" ni un "¿hay que refrescar?"
 * como API pública, así que la decisión de refrescar (buffer sobre {@code expires_at}) la toma este
 * adaptador. Si C agrega esos métodos, este wrapper se simplifica. Logs sin token.
 */
@Component
public class TokenAccessor {

    private final AccountCredentialRepository credentials;
    private final TokenCipher cipher;
    private final AccountService accountService;
    private final Duration refreshBuffer;

    public TokenAccessor(AccountCredentialRepository credentials,
                         TokenCipher cipher,
                         AccountService accountService,
                         @Value("${sync.token.refresh-buffer-minutes:1440}") long refreshBufferMinutes) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.accountService = accountService;
        this.refreshBuffer = Duration.ofMinutes(refreshBufferMinutes);
    }

    /** Devuelve el access token en claro, refrescándolo primero si {@code expires_at} está próximo. */
    public String resolveAccessToken(SocialAccount account) {
        AccountCredential cred = credentials.findById(account.getId())
                .orElseThrow(() -> new InsightsException("Cuenta " + account.getId() + " sin credenciales"));
        if (needsRefresh(cred)) {
            accountService.refreshToken(account.getId());
            cred = credentials.findById(account.getId())
                    .orElseThrow(() -> new InsightsException(
                            "Credenciales ausentes tras refresh para la cuenta " + account.getId()));
        }
        return cipher.decrypt(cred.getAccessTokenEnc());
    }

    private boolean needsRefresh(AccountCredential cred) {
        Instant expiresAt = cred.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(Instant.now().plus(refreshBuffer));
    }
}
