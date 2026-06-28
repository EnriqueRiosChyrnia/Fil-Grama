package com.filgrama.oauth.select;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.filgrama.domain.enums.Platform;
import com.filgrama.oauth.OAuthExchangeResult;
import com.filgrama.oauth.state.OAuthOrigin;

/**
 * Store en memoria de los candidatos de un consentimiento Meta multi-cuenta, a la espera de que el
 * usuario elija cuáles dar de alta (spec/09 §Multi-cuenta por red). Mismo patrón que {@code PkceStore}
 * / {@code OAuthStateService}: {@link java.util.concurrent.ConcurrentHashMap} con TTL corto y purga por
 * expiración. La clave es un {@code selectionToken} de <b>alta entropía</b> (32 bytes, base64url) que es
 * la credencial de los endpoints de selección.
 *
 * <p>Guarda los <b>tokens en claro</b> de cada candidato server-side (nunca se serializan al front): el
 * front sólo ve el {@code selectionToken} y, vía {@code GET}, los datos públicos de cada cuenta. El
 * {@code POST} consume el token (un solo uso). En multi-instancia el callback debe volver a la misma
 * instancia (sticky/LB) — aceptable por el TTL corto, igual que el {@code PkceStore}.
 */
public class OAuthSelectionStore {

    /** Candidatos pendientes de un consentimiento, atados al cliente + usuario iniciador + origen. */
    public record PendingSelection(Long clientId, Long userId, Platform platform, OAuthOrigin origin,
            List<OAuthExchangeResult> candidates, Instant exp) {
    }

    private static final java.security.SecureRandom RNG = new java.security.SecureRandom();

    private final Map<String, PendingSelection> byToken = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public OAuthSelectionStore() {
        this(600);
    }

    OAuthSelectionStore(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /** Guarda los candidatos y devuelve un {@code selectionToken} nuevo de alta entropía. */
    public String stash(Long clientId, Long userId, Platform platform, OAuthOrigin origin,
            List<OAuthExchangeResult> candidates) {
        purgeExpired();
        String token = newToken();
        byToken.put(token, new PendingSelection(clientId, userId, platform, origin,
                List.copyOf(candidates), Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    /** Lee los candidatos sin consumir el token (para el {@code GET} de la lista). */
    public Optional<PendingSelection> peek(String token) {
        purgeExpired();
        return token == null ? Optional.empty() : Optional.ofNullable(byToken.get(token));
    }

    /** Lee y elimina los candidatos (un solo uso; para el {@code POST} que crea las cuentas). */
    public Optional<PendingSelection> consume(String token) {
        purgeExpired();
        return token == null ? Optional.empty() : Optional.ofNullable(byToken.remove(token));
    }

    /** {@code selectionToken} aleatorio (32 bytes → base64url sin padding). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        byToken.values().removeIf(e -> e.exp().isBefore(now));
    }
}
