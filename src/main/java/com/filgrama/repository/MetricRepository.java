package com.filgrama.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.Metric;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.MetricTier;

public interface MetricRepository extends JpaRepository<Metric, String> {

    List<Metric> findByTier(MetricTier tier);

    List<Metric> findByPlatformAndLevel(String platform, MetricLevel level);
}
