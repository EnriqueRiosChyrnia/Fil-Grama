package com.filgrama.oauth.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store en memoria del {@code code_verifier} de PKCE, asociado al {@code state} de cada autorización
 * (spec/09 §TikTok — PKCE OBLIGATORIO). {@link #put} en {@code buildAuthorizationUrl}, {@link #consume}
 * en {@code exchangeCode}. El verifier NUNCA viaja en la URL ni en el {@code state}; solo el challenge.
 *
 * <p>TTL corto con purga por expiración (mismo patrón que {@code OAuthStateService.consumed}). En
 * multi-instancia el callback debe volver a la misma instancia (sticky/LB) — aceptable por el TTL corto;
 * si se requiere estricto cross-instancia, migrar a un store compartido sin cambiar el contrato.
 */
class PkceStore {

    private record Entry(String verifier, Instant exp) {
    }

    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, Entry> byState = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    PkceStore() {
        this(900);
    }

    PkceStore(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /** Guarda el verifier para este {@code state}. */
    void put(String state, String verifier) {
        purgeExpired();
        byState.put(state, new Entry(verifier, Instant.now().plusSeconds(ttlSeconds)));
    }

    /** Devuelve y elimina el verifier de este {@code state} (un solo uso); {@code null} si no hay. */
    String consume(String state) {
        purgeExpired();
        if (state == null) {
            return null;
        }
        Entry e = byState.remove(state);
        return e == null ? null : e.verifier();
    }

    /** {@code code_verifier} aleatorio (64 bytes → base64url sin padding ≈ 86 chars, dentro de 43–128). */
    static String newVerifier() {
        byte[] bytes = new byte[64];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** {@code code_challenge = BASE64URL(SHA256(verifier))} (method {@code S256}). */
    static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        byState.values().removeIf(e -> e.exp().isBefore(now));
    }
}
