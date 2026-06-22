-- Fil-Grama — esquema núcleo (v1)
-- Fuente de verdad del modelo: spec/02-modelo-de-datos.md
-- Convención: snake_case, timestamptz (UTC), PK bigint identity.

-- ============================ Usuarios y clientes ============================

CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    full_name       TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'EMPLEADO')),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE clients (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL,
    plan        TEXT,                         -- etiqueta libre, estética (sin lógica)
    timezone    TEXT NOT NULL DEFAULT 'America/Asuncion',
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE employee_client_priority (
    user_id     BIGINT NOT NULL REFERENCES users(id),
    client_id   BIGINT NOT NULL REFERENCES clients(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, client_id)
);

-- ============================ Cuentas y credenciales ============================

CREATE TABLE social_accounts (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id                BIGINT NOT NULL REFERENCES clients(id),
    platform                 TEXT NOT NULL CHECK (platform IN ('INSTAGRAM', 'FACEBOOK', 'TIKTOK')),
    external_account_id      TEXT NOT NULL,
    handle                   TEXT,
    display_name             TEXT,
    account_type             TEXT CHECK (account_type IN ('PERSONAL', 'CREATOR', 'BUSINESS')),
    capabilities             JSONB,
    capabilities_checked_at  TIMESTAMPTZ,
    status                   TEXT NOT NULL DEFAULT 'CONNECTED'
                                 CHECK (status IN ('CONNECTED', 'DISCONNECTED', 'ERROR', 'UNSUPPORTED')),
    connected_by             BIGINT REFERENCES users(id),
    connected_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform, external_account_id)
);
CREATE INDEX idx_social_accounts_client_platform ON social_accounts (client_id, platform);

CREATE TABLE account_credentials (
    account_id          BIGINT PRIMARY KEY REFERENCES social_accounts(id),
    access_token_enc    BYTEA NOT NULL,
    refresh_token_enc   BYTEA,
    token_type          TEXT,
    scopes              TEXT,
    expires_at          TIMESTAMPTZ,
    last_refreshed_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================ Catálogo de métricas ============================

CREATE TABLE metrics (
    key          TEXT PRIMARY KEY,            -- ej. ig_reach, fb_page_views, tt_view_count
    display_name TEXT NOT NULL,
    platform     TEXT,                        -- NULL = aplica a todas
    level        TEXT NOT NULL CHECK (level IN ('ACCOUNT', 'POST')),
    unit         TEXT,                        -- count | ratio | percent | seconds
    tier         TEXT NOT NULL DEFAULT 'CORE' CHECK (tier IN ('CORE', 'EXTENDED')),
    state        TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE', 'DEPRECATED', 'MIGRATING')),
    description  TEXT
);

-- ============================ Posts y snapshots ============================

CREATE TABLE posts (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id            BIGINT NOT NULL,                    -- denormalizado
    account_id           BIGINT NOT NULL REFERENCES social_accounts(id),
    platform             TEXT NOT NULL,
    external_post_id     TEXT NOT NULL,
    post_type            TEXT CHECK (post_type IN ('IMAGE', 'VIDEO', 'REEL', 'CAROUSEL', 'STORY')),
    permalink            TEXT,
    caption              TEXT,
    remote_media_url     TEXT,
    remote_thumbnail_url TEXT,
    is_ephemeral         BOOLEAN NOT NULL DEFAULT FALSE,
    published_at         TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ,
    first_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, external_post_id)
);
CREATE INDEX idx_posts_account_published ON posts (account_id, published_at);

CREATE TABLE account_metric_snapshots (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     BIGINT NOT NULL,                          -- denormalizado
    account_id    BIGINT NOT NULL REFERENCES social_accounts(id),
    metric_key    TEXT NOT NULL,
    value         NUMERIC(20, 4) NOT NULL,
    period        TEXT,
    captured_at   TIMESTAMPTZ NOT NULL,
    capture_date  DATE NOT NULL,                            -- idempotencia diaria
    UNIQUE (account_id, metric_key, capture_date)
);
CREATE INDEX idx_acct_snap_lookup ON account_metric_snapshots (client_id, account_id, metric_key, captured_at);
CREATE INDEX idx_acct_snap_acct_time ON account_metric_snapshots (account_id, captured_at);

CREATE TABLE post_metric_snapshots (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     BIGINT NOT NULL,                          -- denormalizado
    account_id    BIGINT NOT NULL,                          -- denormalizado
    post_id       BIGINT NOT NULL REFERENCES posts(id),
    metric_key    TEXT NOT NULL,
    value         NUMERIC(20, 4) NOT NULL,
    captured_at   TIMESTAMPTZ NOT NULL,
    capture_date  DATE NOT NULL,                            -- idempotencia diaria
    UNIQUE (post_id, metric_key, capture_date)
);
CREATE INDEX idx_post_snap_lookup ON post_metric_snapshots (post_id, metric_key, captured_at);
CREATE INDEX idx_post_snap_client_time ON post_metric_snapshots (client_id, captured_at);

-- ============================ Media (miniaturas) ============================

CREATE TABLE media_assets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id       BIGINT NOT NULL REFERENCES posts(id),
    client_id     BIGINT NOT NULL,
    kind          TEXT NOT NULL CHECK (kind IN ('THUMBNAIL', 'CLIP')),
    storage_path  TEXT NOT NULL,                            -- carpeta local v1; R2 v1.5+
    content_type  TEXT,
    bytes         INT,
    captured_at   TIMESTAMPTZ NOT NULL,
    purge_after   TIMESTAMPTZ
);
CREATE INDEX idx_media_assets_post ON media_assets (post_id);

-- ============================ Job diario (logs) ============================

CREATE TABLE sync_runs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    status           TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    accounts_total   INT,
    accounts_ok      INT,
    accounts_failed  INT,
    error_summary    TEXT
);

CREATE TABLE sync_account_results (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id            BIGINT NOT NULL REFERENCES sync_runs(id),
    account_id        BIGINT NOT NULL REFERENCES social_accounts(id),
    status            TEXT NOT NULL CHECK (status IN ('OK', 'ERROR')),
    metrics_captured  INT,
    error_message     TEXT
);
CREATE INDEX idx_sync_acct_results_run ON sync_account_results (run_id);

-- ============================ Payloads crudos ============================

CREATE TABLE raw_api_payloads (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id       BIGINT REFERENCES sync_runs(id),
    client_id    BIGINT NOT NULL,
    account_id   BIGINT NOT NULL REFERENCES social_accounts(id),
    platform     TEXT NOT NULL,
    scope        TEXT NOT NULL CHECK (scope IN ('ACCOUNT', 'POST', 'POSTS_LIST')),
    post_id      BIGINT REFERENCES posts(id),
    endpoint     TEXT,
    payload      JSONB NOT NULL,
    captured_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_raw_payloads_acct_scope ON raw_api_payloads (account_id, scope, captured_at);
CREATE INDEX idx_raw_payloads_client_time ON raw_api_payloads (client_id, captured_at);
