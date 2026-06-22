package com.filgrama.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.Platform;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    List<SocialAccount> findByClientId(Long clientId);

    List<SocialAccount> findByStatus(AccountStatus status);

    Optional<SocialAccount> findByPlatformAndExternalAccountId(Platform platform, String externalAccountId);
}
