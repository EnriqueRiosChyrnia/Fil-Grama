package com.filgrama.account.dto;

import java.util.List;

/**
 * Lista de candidatos de un consentimiento Meta multi-cuenta para el paso de selección
 * (spec/09 §Multi-cuenta por red). <b>Nunca</b> incluye tokens: sólo datos públicos de cada cuenta.
 */
public record SelectionResponse(String clientName, List<CandidateView> candidates) {

    /** Vista pública de una cuenta candidata (sin token). */
    public record CandidateView(String externalAccountId, String handle, String displayName,
            String platform, String accountType) {
    }
}
