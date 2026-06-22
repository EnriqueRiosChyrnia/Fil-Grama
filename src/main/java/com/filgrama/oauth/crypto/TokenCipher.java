package com.filgrama.oauth.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado AES-GCM a nivel app para los tokens guardados en {@code account_credentials}
 * (columnas {@code bytea}). Formato del payload: {@code IV(12 bytes) || ciphertext+tag}.
 *
 * <p>La clave viene de {@code security.token-encryption-key} (base64, 128/192/256 bits).
 * Si no está configurada se usa una clave DEV insegura (solo para que la app levante
 * en local) y se loguea un warning. La central debe configurarla en prod.
 *
 * <p>Los tokens en claro nunca se loguean ni se exponen en DTOs.
 */
@Component
public class TokenCipher {

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // 96-bit nonce (recomendado para GCM)
    private static final int TAG_LENGTH_BITS = 128;
    /** Clave DEV de 256 bits — NO usar en prod (la central sobreescribe la config). */
    private static final String DEV_FALLBACK_KEY = "MjciXevgsHmpyP3wf6vOZRS17GPYQGc7EJwFbeuW9YM=";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${security.token-encryption-key:}") String base64Key) {
        String effective = base64Key;
        if (effective == null || effective.isBlank()) {
            log.warn("security.token-encryption-key no configurada — usando clave DEV insegura. "
                    + "Configurala en prod (base64 de 32 bytes).");
            effective = DEV_FALLBACK_KEY;
        }
        byte[] raw = Base64.getDecoder().decode(effective);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "security.token-encryption-key debe ser 128/192/256 bits en base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Cifra texto plano → {@code IV || ciphertext+tag}. */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el token", e);
        }
    }

    /** Descifra {@code IV || ciphertext+tag} → texto plano. */
    public String decrypt(byte[] payload) {
        try {
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el token", e);
        }
    }
}
