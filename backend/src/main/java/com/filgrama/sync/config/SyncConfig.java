package com.filgrama.sync.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Config del track Sync. Habilita {@code @Scheduled} (el app principal no lo trae) y expone el pool
 * acotado que da el <b>timeout por cuenta</b>: el orquestador corre cada cuenta como tarea y la
 * abandona si excede el límite, para que una cuenta colgada no frene la corrida.
 *
 * <p>Concurrencia por defecto = 1 (secuencial, lo más seguro frente a rate limits). Subir
 * {@code sync.concurrency} la acota sin pasar de un pool fijo.
 */
@Configuration
@EnableScheduling
public class SyncConfig {

    @Bean(name = "syncExecutor", destroyMethod = "shutdown")
    public ExecutorService syncExecutor(@Value("${sync.concurrency:1}") int concurrency) {
        int size = Math.max(1, concurrency);
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "sync-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
