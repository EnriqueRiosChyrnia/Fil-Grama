package com.filgrama.error;

import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio compartida por todos los tracks. La traduce a RFC 7807
 * {@link GlobalExceptionHandler}. Cada track lanza estas excepciones en vez de
 * armar su propio manejador de errores.
 *
 * <p>Dueño: terminal central (paquete {@code com.filgrama.error}). Los tracks la
 * USAN pero NO la modifican.
 *
 * <p>Uso típico:
 * <pre>
 *   throw ApiException.notFound("Client %d not found".formatted(id));
 *   throw ApiException.conflict("Email already in use");
 *   throw ApiException.unprocessable("metric_key no existe en el catálogo");
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    // ---- Fábricas para los códigos del contrato (spec/03) ----

    /** 400 — entrada inválida (más allá de la validación de Bean Validation). */
    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", detail);
    }

    /** 404 — recurso inexistente. */
    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "Not Found", detail);
    }

    /** 409 — conflicto (ej. email duplicado). */
    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    /** 422 — regla de negocio violada. */
    public static ApiException unprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", detail);
    }
}
