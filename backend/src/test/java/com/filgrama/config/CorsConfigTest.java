package com.filgrama.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS de la API: preflight permitido (origen del front) vs denegado.
 *
 * <p>Monta solo el {@link CorsFilter} con el {@link CorsConfigurationSource} REAL de
 * {@link SecurityConfig} sobre un controller de prueba — sin levantar el contexto
 * completo (no toca BD ni el resto de la cadena de seguridad).
 */
class CorsConfigTest {

    private static final List<String> ALLOWED =
            List.of("http://localhost:5173", "http://localhost:3000");

    private MockMvc mvc() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(ALLOWED);
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilter(new CorsFilter(source))
                .build();
    }

    @Test
    void preflightDesdeOrigenPermitido_200ConHeadersCors() throws Exception {
        mvc().perform(options("/api/v1/clients")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightDesdeOrigenNoPermitido_sinAllowOrigin() throws Exception {
        mvc().perform(options("/api/v1/clients")
                        .header("Origin", "http://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/clients")
        String probe() {
            return "ok";
        }
    }
}
