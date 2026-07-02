package com.filgrama.mcp;

/**
 * Claves con las que la identidad autenticada viaja en el {@code McpTransportContext} desde el
 * extractor ({@link McpSecurityContextConfig}) hasta cada tool ({@link McpIdentity}).
 */
final class McpTransportKeys {

    /** {@code Long} — id del usuario del token (claim {@code sub}). */
    static final String USER_ID = "fg.userId";

    /** {@link com.filgrama.domain.enums.Role} — rol del token (claim {@code role}). */
    static final String ROLE = "fg.role";

    private McpTransportKeys() {
    }
}
