package com.filgrama.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.SyncRun;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    Optional<SyncRun> findTopByOrderByStartedAtDesc();
}
