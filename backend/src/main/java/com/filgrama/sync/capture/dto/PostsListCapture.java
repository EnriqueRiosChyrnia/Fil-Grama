package com.filgrama.sync.capture.dto;

import java.util.List;

/**
 * Lista de publicaciones de la cuenta (scope {@code POSTS_LIST}). El raw (texto JSON) se guarda una
 * vez; los insights por post se piden aparte con {@code fetchPostInsights}.
 */
public record PostsListCapture(String endpoint, String rawJson, List<RawPost> posts) {
}
