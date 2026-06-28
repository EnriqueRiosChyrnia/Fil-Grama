package com.filgrama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import com.filgrama.auth.JwtProperties;
import com.filgrama.auth.security.JwtAuthenticationFilter;
import com.filgrama.auth.security.RestAccessDeniedHandler;
import com.filgrama.auth.security.RestAuthenticationEntryPoint;

/**
 * Seguridad real del backend (dueño: track A / Auth).
 *
 * <p>Stateless + JWT: sin sesión, CSRF off, filtro JWT antes del de usuario/clave.
 * RBAC por método habilitado ({@code @EnableMethodSecurity}) para que B/C/D usen
 * {@code @PreAuthorize("hasRole('ADMIN')")}. Los 401/403 se formatean como
 * {@code application/problem+json} vía el entry point / access denied handler.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/health",
                                "/actuator/health",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/oauth/callback/**",
                                // Paso de selección multi-cuenta: público, la credencial es el selectionToken
                                // de alta entropía (no la sesión). spec/09 §Multi-cuenta por red.
                                "/api/v1/oauth/select/**",
                                // Callbacks de compliance de Meta (deauthorize / data-deletion): Meta llama
                                // sin auth, igual que el callback; los protege el signed_request firmado.
                                "/api/v1/meta/**",
                                // Link compartible de conexión de cuentas (CV): endpoints públicos sin auth.
                                "/api/v1/public/**",
                                // OpenAPI / Swagger UI (springdoc) — públicos para el codegen del front (orval).
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS para la SPA del front (Vite :5173 / CRA :3000). Orígenes configurables por
     * {@code cors.allowed-origins} (lista separada por comas; override por env
     * {@code CORS_ALLOWED_ORIGINS}). Solo se aplica a {@code /api/v1/**}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
