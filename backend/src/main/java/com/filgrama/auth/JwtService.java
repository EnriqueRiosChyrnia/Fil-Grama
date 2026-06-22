package com.filgrama.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.filgrama.domain.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emisión y validación de access tokens JWT (HS256, stateless).
 * Claims: {@code sub} = userId, {@code role}, {@code iat}, {@code exp}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = props.getAccessTtl();
    }

    /** Emite un access token firmado para el usuario y rol dados. */
    public String issueAccessToken(long userId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida la firma y la expiración del token y extrae los claims.
     *
     * @throws io.jsonwebtoken.JwtException si la firma es inválida, el token expiró o está malformado.
     */
    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        long userId = Long.parseLong(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        return new ParsedToken(userId, role);
    }

    /** Datos extraídos de un access token válido. */
    public record ParsedToken(long userId, Role role) {
    }
}
