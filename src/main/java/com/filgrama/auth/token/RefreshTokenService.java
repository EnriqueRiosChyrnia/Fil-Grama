package com.filgrama.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.auth.JwtProperties;
import com.filgrama.auth.JwtService;
import com.filgrama.domain.User;
import com.filgrama.repository.UserRepository;

/**
 * Emisión, rotación, detección de reuso y revocación de refresh tokens.
 *
 * <p>Se persiste el hash SHA-256 del token; el valor en claro solo se devuelve al
 * cliente. En cada uso se rota: se emite un token nuevo (misma {@code familyId}) y
 * se revoca el anterior. Si llega un token ya revocado => reuso => se revoca toda la
 * familia (invalida la sesión) y se responde 401.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final java.time.Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo, JwtService jwtService,
            UserRepository userRepository, JwtProperties props) {
        this.repo = repo;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTtl = props.getRefreshTtl();
    }

    /** Crea una familia nueva y emite su primer refresh token (login). Devuelve el valor en claro. */
    @Transactional
    public String issueNew(long userId) {
        return create(userId, UUID.randomUUID()).rawValue();
    }

    /**
     * Rota un refresh token: valida, emite uno nuevo (misma familia) y revoca el viejo.
     * Detecta reuso (token ya revocado) revocando toda la familia.
     *
     * <p>{@code noRollbackFor}: en el camino de reuso revocamos la familia y LUEGO lanzamos
     * el 401. Sin esto, el throw haría rollback de la revocación (mutar-y-tirar) y la sesión
     * comprometida seguiría viva. Las demás ramas de error no mutan antes de lanzar.
     *
     * @throws RefreshTokenException si es desconocido, expiró, está revocado (reuso) o el usuario no es válido.
     */
    @Transactional(noRollbackFor = RefreshTokenException.class)
    public RefreshResult rotate(String rawRefreshToken) {
        RefreshToken current = repo.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(() -> new RefreshTokenException("Unknown refresh token"));

        if (current.getRevokedAt() != null) {
            // Reuso de un token ya rotado => comprometido. Revocar toda la familia.
            repo.revokeFamily(current.getFamilyId(), Instant.now());
            throw new RefreshTokenException("Refresh token reuse detected; family revoked");
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenException("Refresh token expired");
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new RefreshTokenException("User no longer exists"));
        if (!user.isActive()) {
            throw new RefreshTokenException("User is inactive");
        }

        Issued next = create(user.getId(), current.getFamilyId());
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(next.entity().getId());
        repo.save(current);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        return new RefreshResult(accessToken, next.rawValue());
    }

    /** Revoca el refresh token (logout). Idempotente: no falla si no existe o ya estaba revocado. */
    @Transactional
    public void revoke(String rawRefreshToken) {
        repo.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                repo.save(token);
            }
        });
    }

    private Issued create(long userId, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256(rawValue));
        token.setFamilyId(familyId);
        token.setExpiresAt(Instant.now().plus(refreshTtl));
        return new Issued(rawValue, repo.save(token));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Resultado de un refresh: nuevo par de tokens. */
    public record RefreshResult(String accessToken, String refreshToken) {
    }

    private record Issued(String rawValue, RefreshToken entity) {
    }
}
