-- Fil-Grama — Track A (Auth/JWT): refresh tokens persistidos y rotados.
-- Dueño: track Auth. Depende de V1 (tabla users).
-- Rotación con detección de reuso: cada uso emite un token nuevo (misma family_id)
-- y revoca el anterior. Si llega un token ya revocado => reuso => se revoca toda la familia.

CREATE TABLE refresh_tokens (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    token_hash    TEXT NOT NULL UNIQUE,          -- hash (SHA-256) del token, nunca el token en claro
    family_id     UUID NOT NULL,                 -- familia de rotación (detección de reuso)
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,                    -- NULL = vigente
    replaced_by   BIGINT REFERENCES refresh_tokens(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
