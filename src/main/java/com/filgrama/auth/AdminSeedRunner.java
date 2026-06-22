package com.filgrama.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.repository.UserRepository;

/**
 * Seed de DESARROLLO: inserta un admin si no existe, para poder probar Auth
 * end-to-end sin depender del CRUD de usuarios (track B). Idempotente.
 *
 * <p><b>Gateado por perfil ({@code @Profile("!prod")}):</b> corre en cualquier perfil que
 * NO sea {@code prod} — esto incluye el perfil por defecto (tests e2e/integración), {@code local}
 * y {@code test}, que loguean con este admin. En {@code prod} el bean ni se carga, así que jamás
 * se siembra una credencial conocida. Se usa un denylist ({@code !prod}) en vez de un allowlist
 * ({@code {local,test}}) a propósito: los tests e2e arrancan sin perfil activo (perfil "default"),
 * y un allowlist los dejaría sin admin → login 401 → suite rota.
 *
 * <p>El primer admin de prod se crea fuera de este runner (ver README → "Crear el primer admin
 * en prod").
 *
 * <p>Credencial dev: {@value #SEED_EMAIL} / {@code Admin123!} (documentada en el README/reporte).
 */
@Component
@Profile("!prod")
public class AdminSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    static final String SEED_EMAIL = "admin@filgrama.local";
    static final String SEED_PASSWORD = "Admin123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_EMAIL)) {
            return;
        }
        User admin = new User();
        admin.setEmail(SEED_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
        admin.setFullName("Admin Fil-Grama (dev seed)");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.warn("[dev-seed] Admin sembrado: {} (cambiar/remover antes de prod)", SEED_EMAIL);
    }
}
