-- Fil-Grama — ciclo de vida de cuenta (tanda CV): agrega 'REMOVED' al estado de social_accounts.
-- Bug: CV1 agregó REMOVED al enum Java y CV2 implementó la baja (DELETE /accounts/{id} →
-- status='REMOVED'), pero la CHECK constraint de V1 no lo incluía → el UPDATE fallaba con 500.
-- Fuente de verdad: spec/02-modelo-de-datos.md §social_accounts · spec/09-flujo-oauth.md §"Ciclo de vida".

ALTER TABLE social_accounts DROP CONSTRAINT IF EXISTS social_accounts_status_check;

ALTER TABLE social_accounts ADD CONSTRAINT social_accounts_status_check
    CHECK (status IN ('CONNECTED', 'DISCONNECTED', 'ERROR', 'UNSUPPORTED', 'REMOVED'));
