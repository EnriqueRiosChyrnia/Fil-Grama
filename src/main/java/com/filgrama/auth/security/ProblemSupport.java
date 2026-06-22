package com.filgrama.auth.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Helpers RFC 7807 ({@code application/problem+json}) con el MISMO formato que
 * {@code com.filgrama.error.GlobalExceptionHandler} (campos
 * {@code type, title, status, detail, instance}).
 *
 * <p>El controller usa {@link #problem} (lo serializa el message converter de MVC).
 * Los handlers del filtro de seguridad (401/403) usan {@link #writeProblemJson},
 * que escribe el JSON directamente para no depender de qué {@code ObjectMapper}
 * (Jackson 2 vs 3) esté cableado en el contexto.
 */
public final class ProblemSupport {

    private ProblemSupport() {
    }

    /** ProblemDetail para devolver como {@code ResponseEntity} desde un controller. */
    public static ProblemDetail problem(HttpStatus status, String title, String detail, String instance) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setInstance(URI.create(instance));
        return pd;
    }

    /** Escribe el problem+json directamente en la respuesta (para el filtro de seguridad). */
    public static void writeProblemJson(HttpServletResponse response, HttpStatus status,
            String detail, String instance) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = "{"
                + "\"type\":\"about:blank\","
                + "\"title\":\"" + escape(status.getReasonPhrase()) + "\","
                + "\"status\":" + status.value() + ","
                + "\"detail\":\"" + escape(detail) + "\","
                + "\"instance\":\"" + escape(instance) + "\""
                + "}";
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
