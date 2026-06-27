package com.filgrama.connectlink;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectLinkRepository extends JpaRepository<ConnectLink, Long> {

    Optional<ConnectLink> findByTokenHash(String tokenHash);

    List<ConnectLink> findByClientIdAndRevokedAtIsNull(Long clientId);

    /** Links no revocados atados a una cuenta esperada (los que invalida la baja de esa cuenta). */
    List<ConnectLink> findByExpectedAccountIdAndRevokedAtIsNull(Long expectedAccountId);
}
