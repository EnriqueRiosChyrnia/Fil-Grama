package com.filgrama.mcp;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.http.HttpStatus;

import com.filgrama.domain.enums.Role;
import com.filgrama.error.ApiException;

import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Identidad autenticada de la sesión MCP, resuelta del {@link McpTransportContext} que dejó el
 * extractor JWT ({@link McpSecurityContextConfig}). Es la ÚNICA fuente de scope de las tools: nunca
 * se confía en un {@code userId}/{@code clientId} que venga en el prompt (spec/11 §aislamiento).
 */
record McpIdentity(Long userId, Role role) {

    boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Extrae la identidad del contexto de la request; 401 si la sesión no está autenticada. */
    static McpIdentity from(McpSyncRequestContext context) {
        McpTransportContext transport = context.transportContext();
        Object userId = transport.get(McpTransportKeys.USER_ID);
        Object role = transport.get(McpTransportKeys.ROLE);
        if (userId instanceof Long id && role instanceof Role r) {
            return new McpIdentity(id, r);
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Sesión MCP sin usuario autenticado (falta o es inválido el token Bearer)");
    }
}
