package com.filgrama.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Clock} compartido para resolver "hoy" de forma testeable (default de rangos de fechas).
 * Los servicios inyectan {@code Clock} en vez de llamar a {@code LocalDate.now()} directo, así los
 * tests pueden fijar la fecha con {@link Clock#fixed}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
