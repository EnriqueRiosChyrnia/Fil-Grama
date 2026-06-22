package com.filgrama.sync.capture;

/**
 * Fallo al capturar insights de una cuenta. Es <b>terminal</b> para esa cuenta:
 * el orquestador marca {@code sync_account_results=ERROR} y sigue con las demás.
 * Para errores transitorios reintentables usar {@link TransientInsightsException}.
 */
public class InsightsException extends RuntimeException {

    public InsightsException(String message) {
        super(message);
    }

    public InsightsException(String message, Throwable cause) {
        super(message, cause);
    }
}
