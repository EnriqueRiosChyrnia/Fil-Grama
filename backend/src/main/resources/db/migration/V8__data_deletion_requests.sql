-- Fil-Grama — compliance Meta (track FG-META): registro de pedidos de borrado de datos.
-- El endpoint Data Deletion request (POST /api/v1/meta/data-deletion) debe devolver un
-- confirmation_code único y persistido + una status_url pública para que el usuario consulte el
-- estado por código (formato exigido por Meta). Fuente: spec/09 §Meta · tracks/FG-META-backend.md Fase 1.

CREATE TABLE data_deletion_requests (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    confirmation_code  TEXT NOT NULL UNIQUE,                 -- alta entropía; se devuelve a Meta y al usuario
    meta_user_id       TEXT NOT NULL,                        -- usuario Meta del signed_request
    status             TEXT NOT NULL DEFAULT 'COMPLETED'
                           CHECK (status IN ('RECEIVED', 'COMPLETED')),
    accounts_removed   INT NOT NULL DEFAULT 0,               -- cuántas cuentas Meta se dieron de baja
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ
);
CREATE INDEX idx_data_deletion_code ON data_deletion_requests (confirmation_code);
