package com.filgrama.sync.capture.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Captura de una story de Instagram (ventana 24 h). Trae sus métricas finales (sin serie) y,
 * opcionalmente, los bytes de la miniatura para cachearla vía {@code MediaService} (track E).
 *
 * @param meta                 metadatos del post efímero (ephemeral=true, expiresAt).
 * @param rawJson              payload crudo (texto JSON).
 * @param thumbnailBytes       bytes de la miniatura ya descargada, o {@code null} si no hay.
 * @param thumbnailContentType content-type de la miniatura (ej. {@code image/jpeg}).
 */
public record StoryCapture(
        RawPost meta,
        String endpoint,
        String rawJson,
        Map<String, BigDecimal> metrics,
        byte[] thumbnailBytes,
        String thumbnailContentType) {
}
