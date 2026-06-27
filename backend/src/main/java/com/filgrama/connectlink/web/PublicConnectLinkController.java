package com.filgrama.connectlink.web;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.connectlink.ConnectLinkService;
import com.filgrama.connectlink.dto.AuthorizationUrlResponse;
import com.filgrama.connectlink.dto.PublicLinkInfo;
import com.filgrama.error.ApiException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Endpoints <b>públicos</b> del connect-link (cubiertos por {@code permitAll /api/v1/public/**}; los
 * añade la central en {@code SecurityConfig}). Un tercero <b>sin login</b> resuelve los metadatos del
 * link y arranca el OAuth, todo acotado al cliente del token. Ver spec/09 §"Link compartible".
 *
 * <p><b>Rate-limit</b> simple en memoria por (token + IP) — ventana fija de 1 min. Suficiente para v1
 * sin dependencias nuevas. TODO: limiter robusto (distribuido/sliding-window) cuando haya multi-instancia.
 */
@RestController
@RequestMapping("/api/v1/public/connect-links")
public class PublicConnectLinkController {

    private static final int MAX_PER_WINDOW = 10;
    private static final long WINDOW_SECONDS = 60;

    private final ConnectLinkService service;
    /** key (token|ip) → ventana de conteo. ConcurrentHashMap: sin locks externos. */
    private final Map<String, Window> hits = new ConcurrentHashMap<>();

    public PublicConnectLinkController(ConnectLinkService service) {
        this.service = service;
    }

    /** Metadatos públicos del link. 404 si no existe; 410 si venció/revocado. */
    @GetMapping("/{token}")
    public PublicLinkInfo resolve(@PathVariable String token, HttpServletRequest request) {
        rateLimit(token, request);
        return service.resolvePublic(token);
    }

    /** Arranca el OAuth acotado al cliente del token → {@code authorizationUrl}. */
    @PostMapping("/{token}/connect/{platform}")
    public AuthorizationUrlResponse connect(@PathVariable String token, @PathVariable String platform,
                                            HttpServletRequest request) {
        rateLimit(token, request);
        return service.startOauth(token, platform);
    }

    /** Ventana fija por (token+IP); supera el cupo → 429. TODO: limiter robusto a futuro. */
    private void rateLimit(String token, HttpServletRequest request) {
        String key = token + "|" + request.getRemoteAddr();
        Instant now = Instant.now();
        Window window = hits.compute(key, (k, current) -> {
            if (current == null || current.startedAt.plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Window(now, new AtomicInteger(0));
            }
            return current;
        });
        if (window.count.incrementAndGet() > MAX_PER_WINDOW) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    "Demasiados intentos; probá de nuevo en un minuto");
        }
    }

    private record Window(Instant startedAt, AtomicInteger count) {
    }
}
