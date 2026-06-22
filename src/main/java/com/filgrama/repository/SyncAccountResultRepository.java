package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.SyncAccountResult;

public interface SyncAccountResultRepository extends JpaRepository<SyncAccountResult, Long> {

    List<SyncAccountResult> findByRunId(Long runId);
}
