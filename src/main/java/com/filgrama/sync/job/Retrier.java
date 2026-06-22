package com.filgrama.sync.job;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.filgrama.sync.capture.TransientInsightsException;

/**
 * Reintenta una llamada ante errores <b>transitorios</b> ({@link TransientInsightsException}:
 * timeout/5xx/429) con backoff exponencial. Los errores terminales se propagan al primer intento.
 * Tras agotar los intentos, propaga el último transitorio (lo trata como terminal de la cuenta).
 */
@Component
public class Retrier {

    private static final Logger log = LoggerFactory.getLogger(Retrier.class);

    private final int maxAttempts;
    private final long baseBackoffMillis;

    public Retrier(@Value("${sync.retry.max-attempts:3}") int maxAttempts,
                   @Value("${sync.retry.backoff-millis:500}") long baseBackoffMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMillis = Math.max(0, baseBackoffMillis);
    }

    public <T> T withRetry(String label, Supplier<T> action) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return action.get();
            } catch (TransientInsightsException e) {
                if (attempt >= maxAttempts) {
                    log.warn("'{}' agotó {} intentos: {}", label, maxAttempts, e.getMessage());
                    throw e;
                }
                long wait = baseBackoffMillis * (1L << (attempt - 1));
                log.debug("'{}' transitorio intento {}/{}, backoff {}ms: {}",
                        label, attempt, maxAttempts, wait, e.getMessage());
                sleep(wait);
            }
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TransientInsightsException("interrumpido durante backoff", ie);
        }
    }
}
