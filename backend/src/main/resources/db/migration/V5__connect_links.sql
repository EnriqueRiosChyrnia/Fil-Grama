-- Fil-Grama — link compartible de conexión (tanda CV)
-- Fuente de verdad: spec/02-modelo-de-datos.md §connect_links · spec/09-flujo-oauth.md §"Link compartible".
-- Onboarding self-service: el cliente conecta su red desde su propio navegador, sin login en Fil-Grama.
-- NO guarda tokens; sólo el hash del token opaco (el raw va en la URL y se devuelve una sola vez).

CREATE TABLE connect_links (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id            BIGINT NOT NULL REFERENCES clients(id),
    token_hash           TEXT NOT NULL,
    platform             TEXT CHECK (platform IN ('INSTAGRAM', 'FACEBOOK', 'TIKTOK')),  -- NULL = el cliente elige
    expected_account_id  BIGINT REFERENCES social_accounts(id),                          -- reconexión puntual
    created_by           BIGINT NOT NULL REFERENCES users(id),
    expires_at           TIMESTAMPTZ NOT NULL,                                           -- TTL (default app: now()+72h)
    revoked_at           TIMESTAMPTZ,                                                    -- desactivación manual
    used_at              TIMESTAMPTZ,                                                    -- último uso (multi-uso hasta expirar/revocar)
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (token_hash)
);
CREATE INDEX idx_connect_links_client ON connect_links (client_id);
