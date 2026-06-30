-- Fil-Grama — v1.1 (FG-T1): tabla de demografía de audiencia para el bloque "Público" del reporte.
-- Fuente: spec/02-modelo-de-datos.md §audience_demographics y spec/05-catalogo-metricas.md §v1.1.
-- Snapshot diario, formato largo, append-only hacia el futuro (re-run del día = upsert, no duplica).
-- Solo IG/FB exponen demografía por API estándar; TikTok no (queda fuera).

CREATE TABLE audience_demographics (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       BIGINT NOT NULL,                                    -- denormalizado
    account_id      BIGINT NOT NULL REFERENCES social_accounts(id),
    scope           TEXT NOT NULL CHECK (scope IN ('FOLLOWER', 'REACHED', 'ENGAGED')),
    breakdown_type  TEXT NOT NULL CHECK (breakdown_type IN ('AGE', 'GENDER', 'CITY', 'COUNTRY')),
    breakdown_value TEXT NOT NULL,                                      -- ej. 25-34, F, Encarnación, PY
    value           NUMERIC(20, 4) NOT NULL,                           -- conteo del segmento
    captured_at     TIMESTAMPTZ NOT NULL,
    capture_date    DATE NOT NULL,                                     -- idempotencia diaria (tz del cliente)
    UNIQUE (account_id, scope, breakdown_type, breakdown_value, capture_date)
);
CREATE INDEX idx_aud_demo_lookup ON audience_demographics (client_id, account_id, scope, capture_date);
