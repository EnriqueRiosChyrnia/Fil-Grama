package com.filgrama.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.AccountCredential;

public interface AccountCredentialRepository extends JpaRepository<AccountCredential, Long> {
}
