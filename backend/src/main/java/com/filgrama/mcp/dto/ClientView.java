package com.filgrama.mcp.dto;

/** Cliente visible para el usuario del token (salida de {@code list_clients}). Sin datos sensibles. */
public record ClientView(Long id, String name, String timezone, String plan, String status) {
}
