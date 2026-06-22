package com.filgrama.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provee un {@link PasswordEncoder} para hashear contraseñas en {@code POST /users}.
 *
 * <p>{@code @ConditionalOnMissingBean}: si el track A (Auth) ya define su propio
 * {@code PasswordEncoder}, este NO se registra y se usa el de A. Así no chocan al
 * mergear. (Reportado en la "definición de terminado".)
 */
@Configuration
public class UserPasswordEncoderConfig {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
