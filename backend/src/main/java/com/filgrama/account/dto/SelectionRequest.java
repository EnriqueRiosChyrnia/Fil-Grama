package com.filgrama.account.dto;

import java.util.List;

/** Cuentas elegidas en el paso de selección (por {@code externalAccountId}). */
public record SelectionRequest(List<String> externalAccountIds) {
}
