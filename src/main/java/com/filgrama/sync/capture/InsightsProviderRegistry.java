package com.filgrama.sync.capture;

import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.domain.enums.Platform;

/**
 * Elige el {@link InsightsProvider} para una red. En {@code local}/{@code test} solo está cargado
 * el {@link MockInsightsProvider} (los reales quedan fuera por {@code @Profile}); en prod resuelve
 * el provider real que soporte la plataforma. Si hay un mock cargado, gana (defensa por si el set
 * de beans cambia).
 */
@Component
public class InsightsProviderRegistry {

    private final List<InsightsProvider> providers;

    public InsightsProviderRegistry(List<InsightsProvider> providers) {
        this.providers = providers;
    }

    public InsightsProvider resolve(Platform platform) {
        InsightsProvider real = null;
        for (InsightsProvider p : providers) {
            if (!p.supports(platform)) {
                continue;
            }
            if (p.mock()) {
                return p;
            }
            if (real == null) {
                real = p;
            }
        }
        if (real != null) {
            return real;
        }
        throw new InsightsException("Sin InsightsProvider para la plataforma " + platform);
    }
}
