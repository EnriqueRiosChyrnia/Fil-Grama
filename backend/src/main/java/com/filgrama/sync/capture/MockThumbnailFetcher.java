package com.filgrama.sync.capture;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impl mock para {@code local}/{@code test}: no toca la red (las URLs del {@code MockInsightsProvider}
 * son ficticias, ej. {@code https://cdn.mock/...}). Devuelve bytes deterministas para cualquier URL no
 * vacía, así el pipeline de cacheo de miniaturas corre end-to-end offline.
 */
@Component
@Profile({"local", "test"})
public class MockThumbnailFetcher implements ThumbnailFetcher {

    @Override
    public Optional<Fetched> fetch(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        byte[] bytes = ("MOCK_THUMB:" + url).getBytes(StandardCharsets.UTF_8);
        return Optional.of(new Fetched(bytes, "image/jpeg"));
    }
}
