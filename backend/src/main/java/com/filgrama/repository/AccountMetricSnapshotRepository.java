package com.filgrama.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.AccountMetricSnapshot;

public interface AccountMetricSnapshotRepository extends JpaRepository<AccountMetricSnapshot, Long> {

    List<AccountMetricSnapshot> findByAccountIdAndMetricKeyOrderByCapturedAtAsc(Long accountId, String metricKey);

    Optional<AccountMetricSnapshot> findByAccountIdAndMetricKeyAndCaptureDate(
            Long accountId, String metricKey, LocalDate captureDate);
}
