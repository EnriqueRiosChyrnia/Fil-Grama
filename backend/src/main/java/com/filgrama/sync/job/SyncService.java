package com.filgrama.sync.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.SyncAccountResult;
import com.filgrama.domain.SyncRun;
import com.filgrama.domain.enums.AccountStatus;
import com.filgrama.domain.enums.SyncAccountStatus;
import com.filgrama.domain.enums.SyncRunStatus;
import com.filgrama.repository.SocialAccountRepository;
import com.filgrama.repository.SyncAccountResultRepository;
import com.filgrama.repository.SyncRunRepository;
import com.filgrama.sync.capture.InsightsException;

/**
 * Orquestador del job diario. Crea la corrida ({@code sync_runs}), procesa cada cuenta
 * {@code CONNECTED} de forma aislada (timeout por cuenta, tolerancia a fallos) y cierra la corrida:
 * {@code SUCCESS} si todas OK, {@code PARTIAL} si hubo ≥1 error, {@code FAILED} ante fallo global.
 *
 * <p>No es transaccional a propósito: cada cuenta corre en su propia tx ({@link AccountSyncProcessor}
 * con {@code REQUIRES_NEW}) y cada {@code sync_account_results}/cierre se persiste por separado, así
 * un fallo individual nunca tumba la corrida ni revierte lo ya capturado por otras cuentas.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int ERROR_SUMMARY_MAX = 4_000;

    private final SocialAccountRepository accountRepository;
    private final SyncRunRepository runRepository;
    private final SyncAccountResultRepository resultRepository;
    private final AccountSyncProcessor processor;
    private final ExecutorService executor;
    private final long accountTimeoutSeconds;

    public SyncService(SocialAccountRepository accountRepository, SyncRunRepository runRepository,
            SyncAccountResultRepository resultRepository, AccountSyncProcessor processor,
            @Qualifier("syncExecutor") ExecutorService executor,
            @Value("${sync.account.timeout-seconds:120}") long accountTimeoutSeconds) {
        this.accountRepository = accountRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.processor = processor;
        this.executor = executor;
        this.accountTimeoutSeconds = accountTimeoutSeconds;
    }

    /** Crea la corrida y la ejecuta de punta a punta. Devuelve el id de {@code sync_runs}. */
    public Long runOnce() {
        Long runId = createRun();
        executeRun(runId);
        return runId;
    }

    /**
     * Escaneo inmediato de UNA sola cuenta (no el job completo): scan al conectar (TAREA A). Crea su
     * propia corrida {@code sync_runs} de 1 cuenta y la procesa en su tx aislada. Best-effort: si la
     * cuenta falla, la corrida queda {@code PARTIAL} pero el método <b>no relanza</b> (quien dispara el
     * scan —el listener del connect— no debe romperse por esto). Devuelve el id de la corrida.
     */
    public Long syncAccountNow(SocialAccount account) {
        Long runId = createRun();
        try {
            int captured = processor.process(account, runId);
            saveResult(runId, account.getId(), SyncAccountStatus.OK, captured, null);
            finishRun(runId, SyncRunStatus.SUCCESS, 1, 1, 0, null);
            log.info("Scan al conectar cuenta {} OK (corrida {}, {} métricas)", account.getId(), runId, captured);
        } catch (Exception e) {
            String message = rootMessage(e);
            saveResult(runId, account.getId(), SyncAccountStatus.ERROR, null, message);
            finishRun(runId, SyncRunStatus.PARTIAL, 1, 0, 1, "cuenta " + account.getId() + ": " + message);
            log.warn("Scan al conectar cuenta {} ERROR (corrida {}): {}", account.getId(), runId, message);
        }
        return runId;
    }

    /** Crea {@code sync_runs} en {@code RUNNING} y devuelve su id (síncrono, para responder rápido). */
    public Long createRun() {
        SyncRun run = new SyncRun();
        run.setStartedAt(Instant.now());
        run.setStatus(SyncRunStatus.RUNNING);
        return runRepository.save(run).getId();
    }

    /** Procesa todas las cuentas CONNECTED de la corrida y cierra su estado/ totales. */
    public void executeRun(Long runId) {
        List<SocialAccount> accounts;
        try {
            accounts = accountRepository.findByStatus(AccountStatus.CONNECTED);
        } catch (RuntimeException e) {
            log.error("Corrida {} FAILED: no se pudieron listar cuentas", runId, e);
            finishRun(runId, SyncRunStatus.FAILED, 0, 0, 0, "No se pudieron listar las cuentas: " + rootMessage(e));
            return;
        }

        int total = accounts.size();
        int ok = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (SocialAccount account : accounts) {
            try {
                int captured = runWithTimeout(account, runId);
                saveResult(runId, account.getId(), SyncAccountStatus.OK, captured, null);
                ok++;
            } catch (Exception e) {
                String message = rootMessage(e);
                saveResult(runId, account.getId(), SyncAccountStatus.ERROR, null, message);
                errors.add("cuenta " + account.getId() + ": " + message);
                failed++;
                log.warn("Cuenta {} ERROR en corrida {}: {}", account.getId(), runId, message);
            }
        }

        SyncRunStatus status = failed == 0 ? SyncRunStatus.SUCCESS : SyncRunStatus.PARTIAL;
        finishRun(runId, status, total, ok, failed, errors.isEmpty() ? null : summarize(errors));
        log.info("Corrida {} cerrada: {} ({} total, {} ok, {} error)", runId, status, total, ok, failed);
    }

    private int runWithTimeout(SocialAccount account, Long runId) throws Exception {
        Future<Integer> future = executor.submit(() -> processor.process(account, runId));
        try {
            return future.get(accountTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new InsightsException("timeout tras " + accountTimeoutSeconds + "s", te);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new InsightsException(String.valueOf(cause.getMessage()), cause);
        }
    }

    private void saveResult(Long runId, Long accountId, SyncAccountStatus status,
            Integer metricsCaptured, String errorMessage) {
        SyncAccountResult result = new SyncAccountResult();
        result.setRunId(runId);
        result.setAccountId(accountId);
        result.setStatus(status);
        result.setMetricsCaptured(metricsCaptured);
        result.setErrorMessage(errorMessage);
        resultRepository.save(result);
    }

    private void finishRun(Long runId, SyncRunStatus status, int total, int ok, int failed, String errorSummary) {
        SyncRun run = runRepository.findById(runId).orElseThrow(
                () -> new IllegalStateException("sync_run " + runId + " desapareció"));
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        run.setAccountsTotal(total);
        run.setAccountsOk(ok);
        run.setAccountsFailed(failed);
        run.setErrorSummary(errorSummary);
        runRepository.save(run);
    }

    private String summarize(List<String> errors) {
        String joined = String.join(" | ", errors);
        return joined.length() <= ERROR_SUMMARY_MAX ? joined : joined.substring(0, ERROR_SUMMARY_MAX) + "…";
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }
}
