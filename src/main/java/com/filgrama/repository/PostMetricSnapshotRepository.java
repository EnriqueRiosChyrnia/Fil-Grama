package com.filgrama.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.PostMetricSnapshot;

public interface PostMetricSnapshotRepository extends JpaRepository<PostMetricSnapshot, Long> {

    List<PostMetricSnapshot> findByPostIdAndMetricKeyOrderByCapturedAtAsc(Long postId, String metricKey);

    Optional<PostMetricSnapshot> findByPostIdAndMetricKeyAndCaptureDate(
            Long postId, String metricKey, LocalDate captureDate);
}
