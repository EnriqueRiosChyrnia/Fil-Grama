package com.filgrama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad — ESQUELETO del bootstrap.
 *
 * TODO (capa de implementación): reemplazar por autenticación JWT
 * (access + refresh rotado) según spec/03-contratos-api.md y spec/09-flujo-oauth.md.
 * Por ahora se permite el acceso para que el proyecto levante y se pueda
 * desarrollar; NO usar así en producción.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/**").permitAll()
                        // TODO: bloquear el resto cuando se implemente JWT.
                        .anyRequest().permitAll()
                );
        return http;
    }
}
