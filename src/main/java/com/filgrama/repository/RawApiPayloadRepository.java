package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.RawApiPayload;

public interface RawApiPayloadRepository extends JpaRepository<RawApiPayload, Long> {

    List<RawApiPayload> findByAccountId(Long accountId);
}
