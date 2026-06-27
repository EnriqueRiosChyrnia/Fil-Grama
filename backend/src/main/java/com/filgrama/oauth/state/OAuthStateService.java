package com.filgrama.oauth.state;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.filgrama.domain.enums.Platform;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite y consume el {@code state} OAuth como un <b>JWT firmado de un solo uso</b>
 * (sin tabla, sin tocar el esquema). Claims: {@code clientId}, {@code platform},
 * {@code userId}, {@code jti=nonce}, {@code exp} (TTL corto, ~10 min).
 *
 * <p>El single-use se garantiza con un set en memoria de nonces consumidos
 * ({@link ConcurrentHashMap} con purga por expiración). Reuso/expirado/firma
 * inválida → {@link InvalidStateException}.
 *
 * <p>La clave viene de {@code oauth.state-secret} (base64, ≥256 bits). En multi-instancia
 * el set de nonces es por-instancia; basta porque el callback vuelve a la misma instancia
 * vía sticky/LB y el TTL corto acota la ventana. Si se requiere estricto cross-instancia,
 * migrar el set a un store compartido (no cambia el contrato).
 */
@Component
public class OAuthStateService {

    private final SecretKey key;
    private final long ttlSeconds;
    /** nonce → expiración; entrada presente ⇒ ya consumido. */
    private final Map<String, Instant> consumed = new ConcurrentHashMap<>();

    @Autowired
    public OAuthStateService(
            @Value("${oauth.state-secret:wmKWIhsMq9NtiXJnItX8oVx4u01AQ6MkjccOO0OtD50=}") String base64Secret,
            @Value("${oauth.state-ttl-seconds:600}") long ttlSeconds) {
        this(Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret)), ttlSeconds);
    }

    /** Constructor directo (tests). */
    OAuthStateService(SecretKey key, long ttlSeconds) {
        this.key = key;
        this.ttlSeconds = ttlSeconds;
    }

    /** Emite un {@code state} firmado con TTL corto (connect nuevo, sin cuenta esperada). */
    public String issue(Long clientId, Platform platform, Long userId) {
        return issue(clientId, platform, userId, null);
    }

    /**
     * Emite un {@code state} firmado con TTL corto. {@code expectedExternalAccountId} no nulo marca
     * una <b>reconexión</b>: el callback exigirá que el open_id devuelto coincida (TAREA B).
     */
    public String issue(Long clientId, Platform platform, Long userId, String expectedExternalAccountId) {
        return issue(clientId, platform, userId, expectedExternalAccountId, OAuthOrigin.APP);
    }

    /**
     * Emite un {@code state} firmado con TTL corto fijando el {@link OAuthOrigin}. {@code APP}: la
     * agencia inició el flujo desde la app; {@code LINK}: vino de un link compartible (el callback
     * redirige a una página pública). Los overloads existentes delegan con {@code origin = APP}
     * (back-compat: nada que ya emite cambia). spec/09 §Link compartible.
     */
    public String issue(Long clientId, Platform platform, Long userId, String expectedExternalAccountId,
            OAuthOrigin origin) {
        purgeExpired();
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("clientId", clientId)
                .claim("platform", platform.name())
                .claim("userId", userId)
                .claim("expectedExt", expectedExternalAccountId)
                .claim("origin", origin.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Valida firma + expiración + no-reuso y lo marca consumido. Lanza
     * {@link InvalidStateException} si algo falla; en ese caso no se marca nada usado
     * por firma/expiración, y si ya estaba usado se rechaza.
     */
    public OAuthState consume(String state) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(state)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidStateException("state inválido o expirado");
        }

        String nonce = claims.getId();
        if (nonce == null || nonce.isBlank()) {
            throw new InvalidStateException("state sin nonce");
        }
        Instant exp = claims.getExpiration() != null
                ? claims.getExpiration().toInstant()
                : Instant.now().plusSeconds(ttlSeconds);
        if (consumed.putIfAbsent(nonce, exp) != null) {
            throw new InvalidStateException("state ya utilizado");
        }

        Object rawClient = claims.get("clientId");
        Object rawUser = claims.get("userId");
        Long clientId = rawClient == null ? null : ((Number) rawClient).longValue();
        Long userId = rawUser == null ? null : ((Number) rawUser).longValue();
        Platform platform = Platform.valueOf(claims.get("platform", String.class));
        String expectedExt = claims.get("expectedExt", String.class);
        String rawOrigin = claims.get("origin", String.class);
        OAuthOrigin origin = rawOrigin == null ? OAuthOrigin.APP : OAuthOrigin.valueOf(rawOrigin);
        return new OAuthState(clientId, platform, userId, nonce, expectedExt, origin);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        consumed.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
