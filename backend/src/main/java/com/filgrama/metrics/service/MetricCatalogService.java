package com.filgrama.metrics.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
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
     * Valida la lista {@code metrics} de un informe ({@code :report}/{@code :batchReport}) contra el
     * catálogo y devuelve las entidades en el orden pedido (de-duplicando, preservando la primera
     * aparición — útil para tomar la {@code unit} de cada métrica). Reglas del contrato (spec/03,
     * sección "Métricas y dashboard"): lista requerida 1..N; toda métrica inválida (ausente, vacía o
     * inexistente en el catálogo) es un error de <b>validación de la request</b> → {@code 400}.
     */
    public LinkedHashMap<String, Metric> requireReportMetrics(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw ApiException.badRequest("'metrics' es requerido (1..N metric_key)");
        }
        LinkedHashMap<String, Metric> resolved = new LinkedHashMap<>();
        for (String raw : keys) {
            if (raw == null || raw.isBlank()) {
                throw ApiException.badRequest("'metrics' contiene un metric_key vacío");
            }
            String key = raw.trim();
            if (resolved.containsKey(key)) {
                continue; // de-dup: no repetimos la serie
            }
            Metric metric = metricRepository.findById(key)
                    .orElseThrow(() -> ApiException.badRequest(
                            "metric_key '%s' no existe en el catálogo".formatted(key)));
            resolved.put(key, metric);
        }
        return resolved;
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
