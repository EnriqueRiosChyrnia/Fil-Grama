package com.filgrama.sync.capture;

/**
 * Error transitorio de la API (timeout, 5xx, 429 rate limit): el
 * {@link com.filgrama.sync.job.Retrier} lo reintenta con backoff antes de
 * propagarlo como fallo terminal de la cuenta.
 */
public class TransientInsightsException extends InsightsException {

    public TransientInsightsException(String message) {
        super(message);
    }

    public TransientInsightsException(String message, Throwable cause) {
        super(message, cause);
    }
}
