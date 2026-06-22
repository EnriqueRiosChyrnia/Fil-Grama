# Spec — Capa 2: Modelo de Datos

> Estado: **CERRADA**.
> Depende de: [01-definiciones-alto-nivel.md](01-definiciones-alto-nivel.md).
> Motor: PostgreSQL. PKs `bigint generated always as identity` salvo que se indique.
> Convención: `snake_case`, timestamps en `timestamptz` (UTC).

## Principios

- **Jerarquía multi-tenant:** `clients` → `social_accounts` (**N por red**) → `snapshots` / `posts`.
  `client_id` por encima de `account_id`. Un cliente puede tener **varias cuentas de la misma red**
  (p. ej. dos Instagram, tres Facebook): cada cuenta es una fila independiente en `social_accounts`.
- **Snapshots append-only, formato largo:** una fila por (métrica, captura). Nunca UPDATE.
- **`client_id` denormalizado** en posts y snapshots para filtrar por cliente sin joins.
- **Soft-delete** (estado/activo) en entidades con historial; no borrado físico.
- **Tokens cifrados** a nivel aplicación; nunca en logs ni en respuestas de API.

---

## Tablas

### users
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| email | text UNIQUE NOT NULL | login |
| password_hash | text NOT NULL | bcrypt/argon2 |
| full_name | text NOT NULL | |
| role | text NOT NULL | `ADMIN` \| `EMPLEADO` (enum/check) |
| is_active | boolean NOT NULL default true | soft-delete |
| created_at | timestamptz NOT NULL default now() | |
| updated_at | timestamptz NOT NULL default now() | |

### clients
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | tenant raíz |
| name | text NOT NULL | |
| plan | text NULL | **v1:** etiqueta de texto **libre y estética** (ej. "Plan Premium", "Mensual reels x4"). La escribe la agencia; sin enum, sin lógica de negocio |
| timezone | text NOT NULL default 'America/Asuncion' | zona horaria del cliente; para derivar hora/día local de publicación |
| status | text NOT NULL default 'ACTIVE' | `ACTIVE` \| `ARCHIVED` |
| notes | text NULL | |
| created_at | timestamptz NOT NULL default now() | |
| updated_at | timestamptz NOT NULL default now() | |

> **Plan comercial (v1 = solo estético).** `plan` es una etiqueta de **texto libre** que la agencia
> escribe por cliente (planes personalizados; contratos se manejan por fuera de la app). **No** dispara
> lógica ni valida formato. El volumen de publicaciones en los reportes es **dinámico** (1 o N), no fijo.
>
> **Diferido (versión futura):**
> - `plan_deliverables` (jsonb) → entregables comprometidos por mes (`{"reels":4,...}`) para mostrar
>   "producido vs comprometido".
> - Marcar qué publicaciones produjo la agencia (la API devuelve **todas** las de la cuenta y no
>   distingue autoría) → requeriría marcado manual/convención.

### employee_client_priority
Flag informativo "cliente prioritario" para un empleado. No restringe acceso (todos los
empleados ven todos los clientes).
| Columna | Tipo | Notas |
|---|---|---|
| user_id | bigint FK → users(id) | |
| client_id | bigint FK → clients(id) | |
| created_at | timestamptz NOT NULL default now() | |
| | | PK compuesta (user_id, client_id) |

### social_accounts
Cuentas conectadas de un cliente. **Un cliente puede tener varias cuentas de la misma red**
(N por red); cada una es una fila. La unicidad es por `(platform, external_account_id)` —no por
`(client_id, platform)`—, así que el esquema ya admite multi-cuenta sin cambios. Para distinguir
cuentas de la misma red en la UI se usan `handle` / `display_name` (deberían mostrarse siempre que
un cliente tenga >1 cuenta de esa red).
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | = `account_id` en snapshots |
| client_id | bigint FK → clients(id) NOT NULL | |
| platform | text NOT NULL | `INSTAGRAM` \| `FACEBOOK` \| `TIKTOK` |
| external_account_id | text NOT NULL | id de la cuenta en la red |
| handle | text NULL | @usuario |
| display_name | text NULL | |
| account_type | text NULL | `PERSONAL` \| `CREATOR` \| `BUSINESS` (detectado, re-evaluado en cada sync) |
| capabilities | jsonb NULL | métricas/endpoints que esta cuenta soporta hoy (resultado de la detección de capacidades) |
| capabilities_checked_at | timestamptz NULL | última detección de capacidades |
| status | text NOT NULL default 'CONNECTED' | `CONNECTED` \| `DISCONNECTED` \| `ERROR` \| `UNSUPPORTED` |
| connected_by | bigint FK → users(id) | admin o empleado que conectó |
| connected_at | timestamptz NOT NULL default now() | |
| | | UNIQUE (platform, external_account_id) |
| | | INDEX (client_id, platform) |

> **Detección de capacidades / degradación elegante:** `account_type` y `capabilities` se detectan
> al conectar y se **re-evalúan en cada corrida** del job. El sistema pide solo las métricas que la
> cuenta soporta hoy; si el cliente cambia de tipo (personal↔business), la captura se ajusta sola
> desde ese día (sin backfill — las APIs no dan historia). Los snapshots viejos se conservan.
> **IG/FB:** las cuentas **personales no se pueden onboardear** para insights (la Graph API solo
> admite profesionales). Se detecta en el onboarding y la cuenta queda `UNSUPPORTED` con aviso, en
> vez de fallar. **TikTok:** cualquier tipo se conecta; Business habilita métricas extra vía Business API.

### account_credentials
Token OAuth de larga duración. 1:1 con `social_accounts`. Separada para rotación/auditoría.
| Columna | Tipo | Notas |
|---|---|---|
| account_id | bigint PK, FK → social_accounts(id) | 1:1 |
| access_token_enc | bytea NOT NULL | cifrado a nivel app |
| refresh_token_enc | bytea NULL | si la red lo provee |
| token_type | text NULL | |
| scopes | text NULL | scopes otorgados |
| expires_at | timestamptz NULL | para refresh proactivo |
| last_refreshed_at | timestamptz NULL | |
| created_at | timestamptz NOT NULL default now() | |
| updated_at | timestamptz NOT NULL default now() | |

### account_metric_snapshots  *(append-only)*
Métricas a nivel cuenta. Una fila por (cuenta, métrica, captura).
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| client_id | bigint NOT NULL | denormalizado |
| account_id | bigint FK → social_accounts(id) NOT NULL | |
| metric_key | text NOT NULL | FK lógica → metrics(key) |
| value | numeric(20,4) NOT NULL | enteros y tasas |
| period | text NULL | granularidad del insight (ej. `day`) |
| captured_at | timestamptz NOT NULL | momento exacto de la captura (job) |
| capture_date | date NOT NULL | día de la captura (timezone del cliente); para idempotencia |
| | | **UNIQUE (account_id, metric_key, capture_date)** → re-run del día hace upsert, no duplica |
| | | INDEX (client_id, account_id, metric_key, captured_at) |
| | | INDEX (account_id, captured_at) |

### posts
Publicaciones detectadas por el job. Metadatos estables.
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| client_id | bigint NOT NULL | denormalizado |
| account_id | bigint FK → social_accounts(id) NOT NULL | |
| platform | text NOT NULL | |
| external_post_id | text NOT NULL | |
| post_type | text NULL | `IMAGE` \| `VIDEO` \| `REEL` \| `CAROUSEL` \| `STORY` |
| permalink | text NULL | link al post original (preview "barata") |
| caption | text NULL | |
| remote_media_url | text NULL | URL del media en la red (puede expirar — no fiarse para historia) |
| remote_thumbnail_url | text NULL | miniatura en la red |
| is_ephemeral | boolean NOT NULL default false | true para STORY (desaparece a las 24h) |
| published_at | timestamptz NULL | instante exacto (UTC). **Fuente única** para derivar hora/día/día-de-semana local (con `clients.timezone`) |
| expires_at | timestamptz NULL | para stories: published_at + 24h |
| first_seen_at | timestamptz NOT NULL default now() | |
| | | UNIQUE (account_id, external_post_id) |
| | | INDEX (account_id, published_at) |

> **Hora/día/día-de-semana NO se guardan como columnas** (serían redundantes y propensas a
> inconsistencia). Se derivan de `published_at` + `clients.timezone` en consulta:
> `EXTRACT(DOW FROM published_at AT TIME ZONE c.timezone)`, `EXTRACT(HOUR ...)`, etc.
> Esto habilita el análisis de **mejores horas/días para publicar** (cruzar publish time vs
> rendimiento) cuando se acumule historia. El análisis es de v2, pero `published_at` se captura
> desde **v1** para que la historia exista. Ver MCP en [[08-ia-reportes]].

### media_assets
Miniaturas cacheadas. **Solo binarios livianos; nunca videos completos en v1.** Se cachean
sobre todo para **stories** (cuyo media desaparece a las 24h). Para posts normales no hace falta:
el media vive en la red y se usa `permalink`/`remote_thumbnail_url` de forma diferida.
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| post_id | bigint FK → posts(id) NOT NULL | |
| client_id | bigint NOT NULL | denormalizado |
| kind | text NOT NULL | `THUMBNAIL` (v1) \| `CLIP` (opcional, futuro) |
| storage_path | text NOT NULL | ruta en object storage (carpeta local v1; Cloudflare R2 / Bunny.net v1.5+) |
| content_type | text NULL | `image/jpeg`, etc. |
| bytes | int NULL | tamaño, para control de consumo |
| captured_at | timestamptz NOT NULL | |
| purge_after | timestamptz NULL | política de retención (purga/baja resolución) |
| | | INDEX (post_id) |

> **Almacenamiento de binarios:** nunca en Postgres. Detrás de una interfaz `StoragePort` →
> v1: carpeta local; v1.5+: **Cloudflare R2** (free tier ~10 GB, sin egress, S3-compatible) o
> **Bunny.net** (storage + CDN, pricing simple). S3-compatible = código portable, sin atarse a AWS.
> En la base solo va la ruta + metadata. **Solo miniaturas** (~50-200 KB). Estimado con ~10 clientes:
> miniaturas ≈ 2-3 GB/año (trivial); guardar videos completos sería ~50+ GB/año → **no se hace**.
> Retención: `purge_after` permite borrar o degradar miniaturas viejas y mantener el consumo acotado.

### post_metric_snapshots  *(append-only)*
Métricas por publicación. Una fila por (post, métrica, captura).
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| client_id | bigint NOT NULL | denormalizado |
| account_id | bigint NOT NULL | denormalizado |
| post_id | bigint FK → posts(id) NOT NULL | |
| metric_key | text NOT NULL | |
| value | numeric(20,4) NOT NULL | |
| captured_at | timestamptz NOT NULL | |
| capture_date | date NOT NULL | día de la captura; para idempotencia |
| | | **UNIQUE (post_id, metric_key, capture_date)** → re-run del día hace upsert, no duplica |
| | | INDEX (post_id, metric_key, captured_at) |
| | | INDEX (client_id, captured_at) |

### metrics  *(catálogo de referencia)*
Define las métricas que se capturan y cómo mostrarlas en reportes.
| Columna | Tipo | Notas |
|---|---|---|
| key | text PK | ej. `reach`, `followers`, `likes` |
| display_name | text NOT NULL | etiqueta para UI/reporte |
| platform | text NULL | NULL = aplica a todas |
| level | text NOT NULL | `ACCOUNT` \| `POST` |
| unit | text NULL | `count` \| `ratio` \| `percent` |
| description | text NULL | |

### sync_runs  *(log del job diario)*
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| started_at | timestamptz NOT NULL | |
| finished_at | timestamptz NULL | |
| status | text NOT NULL | `RUNNING` \| `SUCCESS` \| `PARTIAL` \| `FAILED` |
| accounts_total | int NULL | |
| accounts_ok | int NULL | |
| accounts_failed | int NULL | |
| error_summary | text NULL | |

### sync_account_results  *(detalle por cuenta de cada corrida)*
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| run_id | bigint FK → sync_runs(id) NOT NULL | |
| account_id | bigint FK → social_accounts(id) NOT NULL | |
| status | text NOT NULL | `OK` \| `ERROR` |
| metrics_captured | int NULL | |
| error_message | text NULL | |
| | | INDEX (run_id) |

### raw_api_payloads  *(append-only)*
Respuesta cruda de la API por cada captura. Permite reprocesar y recuperar métricas no parseadas
hoy. Es la fuente desde la que se derivan las filas de `*_metric_snapshots`.
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint PK | |
| run_id | bigint FK → sync_runs(id) NULL | corrida que lo generó |
| client_id | bigint NOT NULL | denormalizado |
| account_id | bigint FK → social_accounts(id) NOT NULL | |
| platform | text NOT NULL | |
| scope | text NOT NULL | `ACCOUNT` \| `POST` \| `POSTS_LIST` |
| post_id | bigint FK → posts(id) NULL | si el payload es de un post puntual |
| endpoint | text NULL | endpoint/edge consultado |
| payload | jsonb NOT NULL | respuesta cruda |
| captured_at | timestamptz NOT NULL | |
| | | INDEX (account_id, scope, captured_at) |
| | | INDEX (client_id, captured_at) |

> Flujo del job: guarda primero el crudo en `raw_api_payloads`, luego deriva e inserta las filas
> en `account_metric_snapshots` / `post_metric_snapshots`. Si cambia el parseo, se puede reprocesar
> desde el crudo sin volver a llamar a la API.

---

## Notas de escala

- Las tablas `*_snapshots` y `raw_api_payloads` crecen sin parar (append-only). Para v1 con ~10
  clientes el volumen es trivial. Optimización futura (v1.5+): particionar por rango de `captured_at`
  (mensual) y/o política de retención del crudo (ej. comprimir o archivar payloads > N meses).
- Cifrado de tokens: clave desde variable de entorno / KMS; rotación contemplada en
  `account_credentials.last_refreshed_at`.
