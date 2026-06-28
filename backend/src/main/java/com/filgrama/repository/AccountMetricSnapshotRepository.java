package com.filgrama.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.filgrama.domain.AccountMetricSnapshot;

public interface AccountMetricSnapshotRepository extends JpaRepository<AccountMetricSnapshot, Long> {

    List<AccountMetricSnapshot> findByAccountIdAndMetricKeyOrderByCapturedAtAsc(Long accountId, String metricKey);

    Optional<AccountMetricSnapshot> findByAccountIdAndMetricKeyAndCaptureDate(
            Long accountId, String metricKey, LocalDate captureDate);

    /** Borrado real de los snapshots de cuenta (compliance Meta: data-deletion). */
    @Modifying
    @Query("delete from AccountMetricSnapshot s where s.accountId = ?1")
    void deleteByAccountId(Long accountId);
}
