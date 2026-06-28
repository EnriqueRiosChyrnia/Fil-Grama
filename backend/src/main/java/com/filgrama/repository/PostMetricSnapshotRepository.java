package com.filgrama.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.filgrama.domain.PostMetricSnapshot;

public interface PostMetricSnapshotRepository extends JpaRepository<PostMetricSnapshot, Long> {

    List<PostMetricSnapshot> findByPostIdAndMetricKeyOrderByCapturedAtAsc(Long postId, String metricKey);

    Optional<PostMetricSnapshot> findByPostIdAndMetricKeyAndCaptureDate(
            Long postId, String metricKey, LocalDate captureDate);

    /** Borrado real de los snapshots de posts de una cuenta (compliance Meta: data-deletion). */
    @Modifying
    @Query("delete from PostMetricSnapshot s where s.accountId = ?1")
    void deleteByAccountId(Long accountId);
}
