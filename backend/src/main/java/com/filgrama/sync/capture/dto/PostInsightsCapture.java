package com.filgrama.sync.capture.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Insights de UNA publicación (scope {@code POST}). {@code metrics} = metric_key -> value. */
public record PostInsightsCapture(String endpoint, String rawJson, Map<String, BigDecimal> metrics) {
}
