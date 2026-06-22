package com.filgrama.sync.capture;

import java.math.BigDecimal;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Soporte HTTP/JSON compartido por los providers de insights reales ({@link MetaInsightsProvider},
 * {@link TikTokInsightsProvider}). Centraliza (spec/10 §Rate limits y robustez):
 * <ul>
 *   <li><b>Timeout por request</b> (una cuenta colgada no frena la corrida).</li>
 *   <li><b>Clasificación de errores</b>: 5xx/429/timeout ⇒ {@link TransientInsightsException}
 *       (el {@code Retrier} del job reintenta con backoff); 4xx ⇒ {@link InsightsException}
 *       (terminal para la cuenta).</li>
 *   <li><b>Autorregulación</b> leyendo {@code X-App-Usage} / {@code X-Business-Use-Case-Usage} de
 *       Meta para frenar antes del 429.</li>
 *   <li>Parseo Jackson 3 y extracción robusta de valores numéricos.</li>
 * </ul>
 *
 * <p>Helper estático sin estado; el {@link JsonMapper} compartido es inmutable y thread-safe.
 * El backoff fino lo hace el {@code Retrier} del job: acá no reintentamos (evita doble retry).
 */
final class InsightsHttpSupport {

    private static final Logger log = LoggerFactory.getLogger(InsightsHttpSupport.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** Umbral de uso (%) de Meta a partir del cual frenamos antes de seguir pegándole a la API. */
    private static final double USAGE_THROTTLE_PCT = 90.0;
    private static final long THROTTLE_SLEEP_MILLIS = 2000;

    private static final JsonMapper JSON = JsonMapper.shared();

    private InsightsHttpSupport() {
    }

    /** {@link RestClient.Builder} con baseUrl + timeouts; los tests le inyectan un factory mock. */
    static RestClient.Builder builder(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
    }

    /** GET → {@code ResponseEntity<String>} (body + headers). {@code bearer} nulo ⇒ sin Authorization. */
    static ResponseEntity<String> get(RestClient http, String uri, String bearer) {
        try {
            return http.get().uri(uri)
                    .headers(h -> {
                        if (bearer != null) {
                            h.setBearerAuth(bearer);
                        }
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().toEntity(String.class);
        } catch (RestClientResponseException e) {
            throw classify(e, uri);
        } catch (ResourceAccessException e) {
            throw new TransientInsightsException("timeout/IO consultando " + uri, e);
        }
    }

    /** POST con body JSON (string literal) → {@code ResponseEntity<String>}. */
    static ResponseEntity<String> postJson(RestClient http, String uri, String jsonBody, String bearer) {
        try {
            return http.post().uri(uri)
                    .headers(h -> {
                        if (bearer != null) {
                            h.setBearerAuth(bearer);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve().toEntity(String.class);
        } catch (RestClientResponseException e) {
            throw classify(e, uri);
        } catch (ResourceAccessException e) {
            throw new TransientInsightsException("timeout/IO consultando " + uri, e);
        }
    }

    /** 5xx/429 ⇒ transitorio (reintentable); el resto ⇒ terminal de la cuenta. Sin volcar el body. */
    private static InsightsException classify(RestClientResponseException e, String uri) {
        HttpStatusCode status = e.getStatusCode();
        if (status.is5xxServerError() || status.value() == 429) {
            return new TransientInsightsException("API respondió " + status.value() + " en " + uri);
        }
        return new InsightsException("API respondió " + status.value() + " en " + uri);
    }

    /**
     * Autorregulación (spec/10): si {@code X-App-Usage} (o el business use case) reporta uso alto,
     * dormimos un poco para no chocar contra el 429. Best-effort: cualquier error de parseo se ignora.
     */
    static void throttleOnUsage(HttpHeaders headers) {
        double usage = Math.max(maxPct(headers.getFirst("X-App-Usage")),
                maxBusinessPct(headers.getFirst("X-Business-Use-Case-Usage")));
        if (usage >= USAGE_THROTTLE_PCT) {
            log.debug("Uso de la API de Meta al {}% — autorregulando {}ms", usage, THROTTLE_SLEEP_MILLIS);
            sleep(THROTTLE_SLEEP_MILLIS);
        }
    }

    private static double maxPct(String appUsageHeader) {
        if (appUsageHeader == null || appUsageHeader.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = JSON.readTree(appUsageHeader);
            double max = 0;
            for (String field : new String[] {"call_count", "total_cputime", "total_time"}) {
                max = Math.max(max, node.path(field).asDouble(0));
            }
            return max;
        } catch (JacksonException e) {
            return 0;
        }
    }

    /** {@code X-Business-Use-Case-Usage}: objeto {businessId: [{call_count, total_time, ...}]}. */
    private static double maxBusinessPct(String header) {
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = JSON.readTree(header);
            double max = 0;
            for (JsonNode arr : root.values()) {
                for (JsonNode entry : arr) {
                    for (String field : new String[] {"call_count", "total_cputime", "total_time"}) {
                        max = Math.max(max, entry.path(field).asDouble(0));
                    }
                }
            }
            return max;
        } catch (JacksonException e) {
            return 0;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- parseo ----

    /** Body → árbol JSON; respuesta ilegible ⇒ terminal {@link InsightsException}. */
    static JsonNode tree(String body) {
        try {
            return JSON.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (JacksonException e) {
            throw new InsightsException("Respuesta de insights ilegible (JSON inválido)", e);
        }
    }

    /** Valor numérico robusto (acepta número o string numérica); {@code null} si falta/no numérico. */
    static BigDecimal number(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) { // ej. post_reactions_by_type_total → sumar los tipos
            BigDecimal sum = BigDecimal.ZERO;
            boolean any = false;
            for (JsonNode child : node.values()) {
                BigDecimal n = number(child);
                if (n != null) {
                    sum = sum.add(n);
                    any = true;
                }
            }
            return any ? sum : null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Lee un campo numérico de un objeto por nombre. */
    static BigDecimal number(JsonNode parent, String field) {
        return number(parent == null ? null : parent.path(field));
    }

    /**
     * Valor de un nodo de {@code data[]} de Meta insights: primero {@code total_value.value} (IG),
     * si no el último de {@code values[].value} (FB Page / IG media). Soporta valores objeto (suma).
     */
    static BigDecimal insightValue(JsonNode metric) {
        JsonNode totalValue = metric.path("total_value").path("value");
        if (!totalValue.isMissingNode() && !totalValue.isNull()) {
            return number(totalValue);
        }
        JsonNode values = metric.path("values");
        if (values.isArray()) {
            JsonNode last = null;
            for (JsonNode el : values) {
                last = el;
            }
            if (last != null) {
                return number(last.path("value"));
            }
        }
        return null;
    }
}
