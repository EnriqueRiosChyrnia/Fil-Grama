package com.filgrama.sync.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.filgrama.sync.capture.InsightsException;
import com.filgrama.sync.capture.TransientInsightsException;

/** Unit test del backoff (sin sleep: baseBackoff=0). */
class RetrierTest {

    @Test
    void reintenta_transitorios_y_luego_tiene_exito() {
        Retrier retrier = new Retrier(3, 0);
        AtomicInteger calls = new AtomicInteger();

        String out = retrier.withRetry("t", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientInsightsException("flaky");
            }
            return "ok";
        });

        assertThat(out).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void agota_intentos_y_propaga_el_transitorio() {
        Retrier retrier = new Retrier(2, 0);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.withRetry("t", () -> {
            calls.incrementAndGet();
            throw new TransientInsightsException("siempre falla");
        })).isInstanceOf(TransientInsightsException.class);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void no_reintenta_errores_terminales() {
        Retrier retrier = new Retrier(3, 0);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.withRetry("t", () -> {
            calls.incrementAndGet();
            throw new InsightsException("error terminal");
        })).isInstanceOf(InsightsException.class);

        assertThat(calls.get()).isEqualTo(1);
    }
}
