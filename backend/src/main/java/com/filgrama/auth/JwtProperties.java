package com.filgrama.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuración del JWT del track Auth. Se enlaza desde {@code application.yml}
 * bajo el prefijo {@code security.jwt} si la terminal central agrega las claves;
 * si no, usa estos defaults (solo aptos para desarrollo).
 *
 * <p>Claves esperadas (env-overridable):
 * <ul>
 *   <li>{@code security.jwt.secret} — secreto HS256, mínimo 32 bytes.</li>
 *   <li>{@code security.jwt.access-ttl} — TTL del access token (ej. {@code 15m}).</li>
 *   <li>{@code security.jwt.refresh-ttl} — TTL del refresh token (ej. {@code 30d}).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Secreto HS256 (>= 32 bytes). En prod sobrescribir con env SECURITY_JWT_SECRET. */
    private String secret = "dev-only-insecure-jwt-secret-change-me-please-min-32-bytes-0123456789";

    /** Vida del access token (corto). */
    private Duration accessTtl = Duration.ofMinutes(15);

    /** Vida del refresh token (largo). */
    private Duration refreshTtl = Duration.ofDays(30);
}
