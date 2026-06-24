package com.filgrama.sync.job;

import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.filgrama.account.event.AccountConnectedEvent;
import com.filgrama.repository.SocialAccountRepository;

/**
 * Escaneo inmediato al conectar (TAREA A): escucha {@link AccountConnectedEvent} y dispara un sync
 * SOLO de esa cuenta.
 *
 * <p><b>AFTER_COMMIT:</b> corre recién cuando la tx del callback (cuenta + credencial) commitea, así
 * el sync ve los datos ya persistidos y un fallo del scan nunca revierte ni rompe el connect.
 * Se ejecuta en el pool del job ({@code syncExecutor}) para no bloquear la respuesta/redirect del
 * callback. Best-effort de punta a punta: cualquier error se loguea y se traga.
 */
@Component
public class ConnectScanListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectScanListener.class);

    private final SocialAccountRepository accountRepository;
    private final SyncService syncService;
    private final ExecutorService executor;

    public ConnectScanListener(SocialAccountRepository accountRepository, SyncService syncService,
            @Qualifier("syncExecutor") ExecutorService executor) {
        this.accountRepository = accountRepository;
        this.syncService = syncService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountConnected(AccountConnectedEvent event) {
        executor.submit(() -> {
            try {
                accountRepository.findById(event.accountId()).ifPresentOrElse(
                        syncService::syncAccountNow,
                        () -> log.warn("Scan al conectar: cuenta {} no encontrada", event.accountId()));
            } catch (RuntimeException e) {
                log.warn("Scan al conectar falló para la cuenta {}: {}", event.accountId(), e.getMessage());
            }
        });
    }
}
