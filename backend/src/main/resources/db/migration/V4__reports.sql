-- Fil-Grama — reportes exportables (track G: Reportes / CU5).
-- Único dueño de V4. Se aplica después de V1-V3 (no depende de objetos de V2/V3 más allá de
-- clients(id) y users(id), creados en V1). Multi-tenant: todo cuelga de client_id.
-- La narrativa IA es v2: las columnas narrative_* quedan nullable desde ya para no volver a migrar.

CREATE TABLE reports (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES clients(id),
    report_type   TEXT NOT NULL CHECK (report_type IN ('SUMMARY','EXTENDED')),
    format        TEXT NOT NULL CHECK (format IN ('MARKDOWN','PDF')),
    status        TEXT NOT NULL CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    period_from   DATE NOT NULL,
    period_to     DATE NOT NULL,
    platforms     JSONB,                       -- redes incluidas (["INSTAGRAM","TIKTOK"])
    rank_by       TEXT,                         -- métrica de orden de destacadas
    storage_path  TEXT,                         -- archivo generado (vía StoragePort de E)
    -- Narrativa IA (v2, nullable desde ya para no migrar de nuevo):
    narrative_md            TEXT,
    narrative_source        TEXT CHECK (narrative_source IN ('MCP','API','MANUAL')),
    narrative_model         TEXT,
    narrative_generated_at  TIMESTAMPTZ,
    created_by    BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_client ON reports (client_id, created_at);
