package com.filgrama.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.filgrama.auth.JwtProperties;
import com.filgrama.auth.JwtService;
import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repo;
    @Mock
    UserRepository userRepository;

    JwtService jwtService;
    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-test-secret-test-secret-test-secret-0123456789-abc");
        props.setRefreshTtl(Duration.ofDays(30));
        jwtService = new JwtService(props);
        service = new RefreshTokenService(repo, jwtService, userRepository, props);
    }

    /** repo.save(...) simula IDENTITY: asigna id si falta y devuelve la misma entidad. */
    private void stubSaveAssignsId() {
        AtomicLong seq = new AtomicLong(100L);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(seq.getAndIncrement());
            }
            return t;
        });
    }

    private User activeUser() {
        User u = new User();
        u.setId(7L);
        u.setEmail("a@b.com");
        u.setFullName("A B");
        u.setRole(Role.EMPLEADO);
        u.setActive(true);
        return u;
    }

    @Test
    void issueNewReturnsRawAndPersistsHash() {
        stubSaveAssignsId();
        String raw = service.issueNew(7L);

        assertThat(raw).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getTokenHash()).isNotBlank().isNotEqualTo(raw); // se guarda el hash, no el claro
        assertThat(saved.getFamilyId()).isNotNull();
    }

    @Test
    void rotateValidTokenRotatesAndRevokesOld() {
        stubSaveAssignsId();
        UUID family = UUID.randomUUID();
        RefreshToken current = new RefreshToken();
        current.setId(5L);
        current.setUserId(7L);
        current.setFamilyId(family);
        current.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        current.setTokenHash("hash-of-current");

        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(current));
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeUser()));

        RefreshTokenService.RefreshResult result = service.rotate("raw-refresh");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        // el viejo quedó revocado y apuntando al nuevo
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedBy()).isNotNull();
        // nuevo token comparte la familia
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(t -> family.equals(t.getFamilyId()) && t.getRevokedAt() == null);
    }

    @Test
    void rotateRevokedTokenDetectsReuseAndRevokesFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken();
        revoked.setId(5L);
        revoked.setUserId(7L);
        revoked.setFamilyId(family);
        revoked.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        revoked.setRevokedAt(Instant.now().minusSeconds(60)); // ya revocado => reuso
        revoked.setTokenHash("hash");

        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("raw-refresh"))
                .isInstanceOf(RefreshTokenException.class);

        verify(repo).revokeFamily(eq(family), any(Instant.class));
        verify(repo, never()).save(any(RefreshToken.class)); // no emite token nuevo
    }

    @Test
    void rotateExpiredTokenThrows() {
        RefreshToken expired = new RefreshToken();
        expired.setId(5L);
        expired.setUserId(7L);
        expired.setFamilyId(UUID.randomUUID());
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        expired.setTokenHash("hash");

        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("raw-refresh"))
                .isInstanceOf(RefreshTokenException.class);
        verify(repo, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotateUnknownTokenThrows() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("raw-refresh"))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void revokeMarksTokenRevoked() {
        RefreshToken token = new RefreshToken();
        token.setId(5L);
        token.setUserId(7L);
        token.setFamilyId(UUID.randomUUID());
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        token.setTokenHash("hash");
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke("raw-refresh");

        assertThat(token.getRevokedAt()).isNotNull();
        verify(repo).save(token);
    }
}
