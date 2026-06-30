package com.filgrama.sync.derive;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.filgrama.domain.enums.MetricLevel;
import com.filgrama.domain.enums.MetricState;
import com.filgrama.domain.enums.MetricTier;
import com.filgrama.domain.enums.Platform;
import com.filgrama.repository.MetricRepository;

/**
 * Vista del catálogo {@code metrics} dirigida por datos: qué {@code metric_key} CORE/ACTIVE captura
 * cada red en cada nivel. El derive intersecta lo que trae el provider con este set, así una métrica
 * que no esté en el catálogo (o esté DEPRECATED/EXTENDED) nunca se persiste — sin tocar código.
 *
 * <p>El catálogo casi no cambia; se cachea por {@code (platform, level)}.
 */
@Component
public class MetricCatalog {

    private final MetricRepository metricRepository;
    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();

    public MetricCatalog(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    /** {@code metric_key}s CORE+ACTIVE del catálogo para esa red y nivel ({@code platform NULL} = todas). */
    public Set<String> coreActiveKeys(Platform platform, MetricLevel level) {
        return cache.computeIfAbsent(platform.name() + '|' + level.name(), k ->
                metricRepository.findByTier(MetricTier.CORE).stream()
                        .filter(m -> m.getState() == MetricState.ACTIVE)
                        .filter(m -> m.getLevel() == level)
                        .filter(m -> m.getPlatform() == null || m.getPlatform().equals(platform.name()))
                        .map(com.filgrama.domain.Metric::getKey)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * ¿La {@code key} está CORE+ACTIVE para esa red y nivel? Gateo data-driven: el job consulta esto
     * antes de pedir datos opcionales (ej. demografía vía {@code ig_follower_demographics}); apagarlo
     * = cambiar la fila del catálogo a EXTENDED/DEPRECATED, sin tocar código.
     */
    public boolean captures(Platform platform, MetricLevel level, String key) {
        return coreActiveKeys(platform, level).contains(key);
    }
}
