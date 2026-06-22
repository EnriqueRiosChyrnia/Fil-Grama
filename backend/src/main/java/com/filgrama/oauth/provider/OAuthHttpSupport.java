package com.filgrama.oauth.provider;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.filgrama.oauth.OAuthException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Soporte HTTP/JSON compartido por los providers OAuth reales ({@link MetaOAuthProvider},
 * {@link TikTokOAuthProvider}). Centraliza: {@link RestClient} con timeouts por request,
 * parseo con Jackson 3 ({@code tools.jackson}) y clasificación de errores transitorios.
 *
 * <p>No es un bean: helper estático sin estado (el {@link JsonMapper} compartido es inmutable y
 * thread-safe). Nunca loguea tokens ni {@code client_secret}/{@code app_secret}.
 */
final class OAuthHttpSupport {

    /** Timeouts conservadores: el canje/refresh es server-side y sincrónico (lo espera el callback). */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    /** Mapper Jackson 3 compartido (read-only) para leer respuestas como árbol. */
    private static final JsonMapper JSON = JsonMapper.shared();

    private OAuthHttpSupport() {
    }

    /** {@link RestClient.Builder} con factory de timeouts; los tests le inyectan otro factory (mock). */
    static RestClient.Builder builder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }

    /** Parsea el body a árbol JSON; respuesta ilegible ⇒ {@link OAuthException} (sin volcar el body). */
    static JsonNode tree(String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (JacksonException e) {
            throw new OAuthException("Respuesta OAuth ilegible (JSON inválido)", e);
        }
    }

    /** Valor de texto de un campo o {@code null} si falta/es null. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** {@code Instant} de expiración a partir de {@code expires_in} (segundos); usa default si falta. */
    static Instant expiresAt(JsonNode node, String field, long defaultSeconds) {
        JsonNode value = node.path(field);
        long seconds = value.isNumber() ? value.asLong() : defaultSeconds;
        return Instant.now().plusSeconds(seconds);
    }

    /** 5xx o 429 ⇒ fallo transitorio de la red (no es culpa del code/credenciales). */
    static boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }
}
