-- Fil-Grama — compliance Meta (track FG-META): guarda el id del usuario Meta que autoriza.
-- Sin esto, los callbacks de Deauthorize / Data Deletion de Meta no pueden ubicar las filas a
-- borrar/marcar (Meta manda un user_id en el signed_request, no el id de Página/IG).
-- Fuente de verdad: spec/09-flujo-oauth.md §Meta + §Ciclo de vida · tracks/FG-META-backend.md Fase 1.
-- Nullable: cuentas TikTok y filas previas quedan en NULL; el multi-Página denormaliza el mismo
-- meta_user_id en cada fila hermana del mismo consentimiento.

ALTER TABLE social_accounts ADD COLUMN meta_user_id TEXT;
CREATE INDEX idx_social_accounts_meta_user ON social_accounts (meta_user_id);
