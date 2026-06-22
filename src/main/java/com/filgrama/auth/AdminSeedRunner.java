package com.filgrama.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.repository.UserRepository;

/**
 * Seed de DESARROLLO: inserta un admin si no existe, para poder probar Auth
 * end-to-end sin depender del CRUD de usuarios (track B). Idempotente.
 *
 * <p><b>Removible:</b> es solo para dev. Borrar esta clase (o gatearla por perfil)
 * cuando el track B provea alta de usuarios / seed productivo.
 *
 * <p>Credencial: {@value #SEED_EMAIL} / {@code Admin123!} (documentada en el reporte).
 */
@Component
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
