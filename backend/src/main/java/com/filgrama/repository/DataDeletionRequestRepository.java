package com.filgrama.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.DataDeletionRequest;

public interface DataDeletionRequestRepository extends JpaRepository<DataDeletionRequest, Long> {

    Optional<DataDeletionRequest> findByConfirmationCode(String confirmationCode);
}
