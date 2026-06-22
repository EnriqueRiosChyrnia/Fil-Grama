package com.filgrama.oauth;

import java.util.Locale;
import java.util.Optional;

import com.filgrama.domain.enums.Platform;

/** Mapeo entre el segmento de ruta (minúsculas) y el enum {@link Platform}. */
public final class Platforms {

    private Platforms() {
    }

    /** {@code INSTAGRAM} → {@code "instagram"}. */
    public static String path(Platform platform) {
        return platform.name().toLowerCase(Locale.ROOT);
    }

    /** {@code "tiktok"} → {@code TIKTOK}; desconocido → vacío (no lanza). */
    public static Optional<Platform> fromPath(String segment) {
        if (segment == null || segment.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Platform.valueOf(segment.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
