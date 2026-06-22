# Reporte — Track F: Sync / Job diario (`feat/sync`)

Implementación del job diario de captura: worker que cada día toma insights de cada cuenta
conectada y construye la historia (snapshots), de forma **idempotente** y **tolerante a fallos**.
Construido contra `MockInsightsProvider`; reutiliza credenciales/refresh del track C y `MediaService`
del track E vía sus beans públicos. Sin migración. Todo el código nuevo vive en `com.filgrama.sync.**`.

---

## 1. Build

```
mvn clean package        # Docker arriba (Testcontainers levanta Postgres real)
→ BUILD SUCCESS
```

- **Tests totales del backend:** 146 (0 failures, 0 errors).
- **Tests nuevos de este track:** 13
  - `SyncJobIntegrationTest` — 6 (Testcontainers + Postgres real + `MockInsightsProvider`)
  - `SyncControllerTest` — 4 (HTTP de punta a punta, RBAC real)
  - `RetrierTest` — 3 (unit, backoff)
- Artefacto: `target/filgrama-backend-0.1.0-SNAPSHOT.jar`.
- **Sin dependencias nuevas** (no se tocó `pom.xml`). Se usa `RestClient` (spring-web) y el
  `JdbcTemplate` autoconfigurado; nada más.

> Nota Boot 4: el `ObjectMapper` autoconfigurado es **Jackson 3** (`tools.jackson`), no
> `com.fasterxml` (Jackson 2), y **no existe** un bean `RestClient.Builder` por defecto. El track
> evita ambos: arma el crudo como texto JSON sin Jackson y construye el `RestClient` con el builder
> estático. (Útil para los demás tracks.)

## 2. Tests vs. "definición de terminado"

| Criterio DoD | Test | Qué verifica |
|---|---|---|
| **Idempotencia** (2× mismo día no duplica; último gana; crudo acumula) | `SyncJobIntegrationTest.idempotencia_rerun_mismo_dia_no_duplica_y_ultimo_gana` | 2ª corrida con valores nuevos → misma cantidad de snapshots (1 fila/día), `ig_reach` actualizado al valor nuevo, `raw_api_payloads` pasa de 5 a 10 filas |
| **Append-only hacia el futuro** | `…append_only_hacia_el_futuro_no_toca_dias_pasados` | Fila de ayer (valor 999) queda intacta; aparece fila nueva para hoy; 2 días = 2 filas |
| **Tolerancia a fallos** | `…tolerancia_a_fallos_una_cuenta_error_no_tumba_la_corrida` | Cuenta que falla → `sync_account_results=ERROR`, las demás `OK`, corrida `PARTIAL`; la cuenta fallida se revierte por completo (atómica) |
| **Posts: upsert sin duplicar** | idempotencia + tiktok | `findByAccountId` estable entre corridas (UNIQUE `account_id+external_post_id`) |
| **Refresh de token** (CU2) | `…refresca_token_proximo_a_expirar_antes_de_consultar` | Token que vence en 1h → se refresca vía track C; `last_refreshed_at` queda seteado |
| **Stories (CU8) + miniatura** | `…captura_story_de_instagram_y_cachea_miniatura` | Post efímero (`is_ephemeral`, `expires_at`), métricas de story, miniatura cacheada vía `MediaService` |
| **Multi-red** | `…tiktok_captura_cuenta_y_videos_sin_stories_ni_miniaturas` | TikTok captura cuenta+videos, sin stories ni miniaturas |
| **/sync/run ADMIN → 202 + runId; EMPLEADO → 403; detalle; 401; 404** | `SyncControllerTest` (4) | RBAC real con `@EnableMethodSecurity`; detalle de corrida; problem+json |
| **Retry/backoff** | `RetrierTest` (3) | Reintenta transitorios, agota intentos, no reintenta terminales |

## 3. Prueba manual

```bash
docker compose up -d            # perfil 'local' → MockInsightsProvider activo
# (sembrar al menos un client + social_account CONNECTED + account_credentials)

TOKEN=$(curl -s localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@filgrama.local","password":"Admin123!"}' | jq -r .accessToken)

# Dispara la corrida
curl -i -X POST localhost:8080/api/v1/sync/run -H "Authorization: Bearer $TOKEN"
# → 202 {"runId": N}

# Ver la corrida y su detalle por cuenta
curl -s localhost:8080/api/v1/sync/runs            -H "Authorization: Bearer $TOKEN"   # historial paginado
curl -s localhost:8080/api/v1/sync/runs/N          -H "Authorization: Bearer $TOKEN"   # detalle + results
```

Tras correr: `sync_runs` (SUCCESS/PARTIAL), `sync_account_results` por cuenta,
`account_metric_snapshots` / `post_metric_snapshots` poblados (1 fila/día), `raw_api_payloads`
con el crudo, y `media_assets` con la miniatura de la story.

> El admin de dev (`admin@filgrama.local` / `Admin123!`) lo siembra el `AdminSeedRunner` del track A.
> Las credenciales (`access_token_enc`) las cifra el `TokenCipher` del track C en el onboarding OAuth.

## 4. Archivos creados (31 — todos en `com.filgrama.sync`)

**`com.filgrama.sync`**
- `SnapshotUpsertRepository.java` — upsert idempotente `ON CONFLICT` (JdbcTemplate, repo propio del track)

**`…sync.capture`** (clientes de insights, abstracción mockeable)
- `InsightsProvider.java`, `InsightsProviderRegistry.java`
- `MockInsightsProvider.java` (perfil `local`/`test`, determinista, seedable, sentinela de fallo)
- `MetaInsightsProvider.java`, `TikTokInsightsProvider.java` (scaffolding real, perfil `!local & !test`)
- `InsightsException.java`, `TransientInsightsException.java`
- `dto/`: `AccountCapture`, `PostsListCapture`, `PostInsightsCapture`, `StoryCapture`, `RawPost`

**`…sync.derive`**
- `MetricCatalog.java` (CORE/ACTIVE por red+nivel, cacheado sobre `MetricRepository`)
- `SnapshotDeriver.java` (filtra por catálogo, upsert idempotente)

**`…sync.job`**
- `SyncService.java` (orquestador: lifecycle de la corrida, timeout por cuenta, SUCCESS/PARTIAL/FAILED)
- `AccountSyncProcessor.java` (pipeline por cuenta, `@Transactional(REQUIRES_NEW)`)
- `TokenAccessor.java` (adaptador a credenciales/refresh del track C)
- `Retrier.java` (backoff ante transitorios)
- `SyncScheduler.java` (`@Scheduled` cron, desactivable)

**`…sync.config`**
- `SyncConfig.java` (`@EnableScheduling` + pool acotado para el timeout por cuenta)

**`…sync.web`**
- `SyncController.java` (`/api/v1/sync/run`, `/runs`, `/runs/{id}`, todo `[ADMIN]`)
- `dto/`: `SyncRunTriggerResponse`, `SyncRunResponse`, `SyncAccountResultResponse`,
  `SyncRunDetailResponse`, `PageResponse`

**Tests** (`src/test/.../sync`)
- `SyncTestSupport.java` (base Testcontainers, perfil `test`, seeding, helpers HTTP)
- `SyncJobIntegrationTest.java`, `SyncControllerTest.java`, `job/RetrierTest.java`

## 5. Métodos que faltaron del track C (para coordinar con la central)

El track C cubre lo necesario; **no hubo que editarlo**. Dos huecos menores, resueltos desde F sin
tocar C, que C podría exponer para simplificar:

1. **No hay un método público "obtener access token descifrado de una cuenta".**
   Workaround en `TokenAccessor`: `AccountCredentialRepository.findById(accountId)` +
   `TokenCipher.decrypt(cred.getAccessTokenEnc())` (ambos públicos del track C).
   *Sugerencia:* un `AccountCredentialService.getDecryptedAccessToken(Long accountId)`.

2. **No hay un "¿hace falta refrescar?" (chequeo de `expires_at`).**
   `AccountService.refreshToken(Long accountId)` (público) hace el refresh completo y actualiza
   `last_refreshed_at`, pero **no decide** si toca refrescar. La decisión (buffer sobre `expires_at`,
   default 24h, `sync.token.refresh-buffer-minutes`) la toma `TokenAccessor`.
   *Sugerencia:* que C exponga `ensureFreshAccessToken(accountId, buffer)` que decida + refresque + devuelva el token.

Reutilizado de C sin cambios: `TokenCipher`, `AccountService.refreshToken`, `AccountCredentialRepository`,
`OAuthProviderRegistry`/`MockOAuthProvider` (el refresh real lo ejercita el mock en `test`/`local`).
Reutilizado de E sin cambios: `MediaService.cacheThumbnail(Post, byte[], String)`.

## 6. Confirmaciones de coordinación

- ✅ **Solo se tocó `com.filgrama.sync.**`** (`git status`: únicamente `src/{main,test}/java/com/filgrama/sync/`).
- ✅ **No se tocó** `pom.xml`, `application.yml`, `SecurityConfig`, `com.filgrama.domain.**`,
  `com.filgrama.repository.**`, ni paquetes de otros tracks.
- ✅ **Sin migración nueva** (las tablas y los UNIQUE de idempotencia ya existían en V1).
- ✅ **Sin dependencias nuevas.**
- ✅ Errores RFC 7807 vía el handler compartido `com.filgrama.error` (sin advice propio); `404` para corrida inexistente.
- ✅ Logs sin tokens.

### Pendientes documentados (no bloquean F)
- **Webhook `story_insights` de Meta** (`POST /api/v1/webhooks/meta`): queda como mejora (TODO).
  v1 captura stories por polling liviano en la corrida diaria.
- **Rate limits / batching reales** (Meta `X-App-Usage`, TikTok req/min): hooks `TODO` en los
  providers reales; no se ejercitan sin App Review.
- **Concurrencia del job:** default secuencial (`sync.concurrency=1`); el pool acotado ya soporta subirla.
- **`@Async` en `/sync/run`:** v1 es síncrono (crea la corrida, la ejecuta y devuelve `202 {runId}`);
  el `SyncService` ya separa `createRun()` / `executeRun()` para volverlo async sin refactor.
