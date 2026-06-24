package com.filgrama.sync.capture;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impl real (prod): baja la miniatura por HTTP con {@link HttpClient} de la JDK (sin deps nuevas).
 * Acota timeout y tamaño, exige 2xx + content-type de imagen. Cualquier desvío → {@code empty}
 * (best-effort: ver {@link ThumbnailFetcher}). Nunca loguea la URL completa con query (puede traer
 * tokens de CDN); solo el host.
 */
@Component
@Profile("!local & !test")
public class HttpThumbnailFetcher implements ThumbnailFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpThumbnailFetcher.class);

    /** Tope de tamaño: miniaturas son ~50-200 KB; cortamos bien por encima para no bajar videos. */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final HttpClient http;
    private final Duration requestTimeout;

    public HttpThumbnailFetcher(
            @Value("${sync.thumbnail.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${sync.thumbnail.request-timeout-seconds:10}") long requestTimeoutSeconds) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    @Override
    public Optional<Fetched> fetch(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() / 100 != 2) {
                log.warn("Miniatura {}: status {}", host(url), response.statusCode());
                return Optional.empty();
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                log.warn("Miniatura {}: tamaño inválido ({} bytes)", host(url), body == null ? 0 : body.length);
                return Optional.empty();
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .map(ct -> ct.split(";", 2)[0].trim().toLowerCase())
                    .orElse("image/jpeg");
            if (!contentType.startsWith("image/")) {
                log.warn("Miniatura {}: content-type no-imagen '{}'", host(url), contentType);
                return Optional.empty();
            }
            return Optional.of(new Fetched(body, contentType));
        } catch (Exception e) {
            // InterruptedException incluida: best-effort, no propagamos.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("No se pudo bajar la miniatura {}: {}", host(url), e.getMessage());
            return Optional.empty();
        }
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return "(url inválida)";
        }
    }
}
