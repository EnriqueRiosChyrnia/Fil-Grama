package com.filgrama.metrics.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.filgrama.domain.Metric;
import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.MetricState;
import com.filgrama.domain.enums.MetricTier;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.MetricCatalogItem;
import com.filgrama.repository.MetricRepository;

/** Catálogo de métricas: listado filtrable y validación de {@code metric_key} contra el catálogo. */
@Service
public class MetricCatalogService {

    private final MetricRepository metricRepository;

    public MetricCatalogService(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    /**
     * Lista el catálogo CORE/ACTIVE, filtrable por {@code platform} (incluyendo las de
     * {@code platform IS NULL} = aplican a todas) y por {@code level}.
     */
    public List<MetricCatalogItem> list(String platform, String level) {
        String platformFilter = normalize(platform);
        MetricLevel levelFilter = parseLevel(level);
        return metricRepository.findByTier(MetricTier.CORE).stream()
                .filter(m -> m.getState() == MetricState.ACTIVE)
                .filter(m -> platformFilter == null
                        || m.getPlatform() == null
                        || m.getPlatform().equalsIgnoreCase(platformFilter))
                .filter(m -> levelFilter == null || m.getLevel() == levelFilter)
                .sorted(Comparator.comparing(Metric::getKey))
                .map(MetricCatalogService::toItem)
                .toList();
    }

    /**
     * Devuelve la métrica del catálogo o lanza {@code 422} si el {@code metric_key} no existe.
     * Falta del parámetro → {@code 400}.
     */
    public Metric requireMetric(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("Falta el parámetro 'metric'");
        }
        return metricRepository.findById(key.trim())
                .orElseThrow(() -> ApiException.unprocessable(
                        "metric_key '%s' no existe en el catálogo".formatted(key)));
    }

    /** Búsqueda cruda sin validar (para resolver el campo de orden de posts). */
    public Optional<Metric> find(String key) {
        return key == null || key.isBlank() ? Optional.empty() : metricRepository.findById(key.trim());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static MetricLevel parseLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        try {
            return MetricLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("level inválido: '%s' (use ACCOUNT|POST)".formatted(level));
        }
    }

    private static MetricCatalogItem toItem(Metric m) {
        return new MetricCatalogItem(
                m.getKey(), m.getDisplayName(), m.getPlatform(),
                m.getLevel().name(), m.getUnit(), m.getTier().name(), m.getState().name());
    }
}
