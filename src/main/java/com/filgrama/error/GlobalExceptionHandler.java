package com.filgrama.error;

import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador global de errores en formato RFC 7807 ({@code application/problem+json}).
 * ÚNICO advice del proyecto — vive en {@code main}, lo comparten todos los tracks.
 *
 * <p>Reglas:
 * <ul>
 *   <li>Los tracks lanzan {@link ApiException} (404/409/422/400) en vez de crear su
 *       propio {@code @RestControllerAdvice}.</li>
 *   <li>Hereda de {@link ResponseEntityExceptionHandler}: las excepciones estándar de
 *       Spring MVC (body ilegible, 405, etc.) ya salen como {@link ProblemDetail}.</li>
 *   <li>Los códigos 401/403 NO pasan por acá: ocurren en el filtro de seguridad, antes
 *       del controller. Los formatea el track Auth (A) en su {@code AuthenticationEntryPoint}
 *       / {@code AccessDeniedHandler}, reutilizando este mismo formato.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Excepciones de negocio de los tracks → status + problem+json. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getTitle());
        return pd;
    }

    /** Argumentos inválidos no envueltos en ApiException → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        return pd;
    }

    /** Validación de Bean Validation (@Valid) → 400 con los campos que fallaron. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isBlank() ? "Validation failed" : detail);
        pd.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(pd);
    }

    /** Red de seguridad: cualquier otra excepción no contemplada → 500 sin filtrar internos. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // No exponemos el mensaje interno al cliente.
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
