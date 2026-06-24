# Spec — Capa 10: Estrategia del Job Diario de Captura

> Estado: **EN REVISIÓN**.
> Depende de: [[02-modelo-de-datos]] (snapshots, raw_api_payloads, sync_runs), [[09-flujo-oauth]]
> (refresh), [[05-catalogo-metricas]] (qué capturar), [[04-criterios-aceptacion]] (CU2, CU6, CU8).

## Objetivo

Capturar, una vez al día y de forma confiable, los insights de cada cuenta conectada y guardarlos
como snapshots históricos. Es el corazón del producto: las APIs no dan historia, nosotros la construimos.

## Scheduling

- Worker 24/7 con `@Scheduled` (cron) — corre a **hora fija** (p. ej. **03:00**, hora baja) en v1;
  **configurable a futuro**. Spring Boot en el VPS.
- **Copy del estado vacío:** el "primer dato esperado" que muestra el Dashboard vacío ([[07-principios-ux]])
  **no debe prometer una hora exacta** (el resultado depende de la hora del job + hasta 48 h de delay de
  Meta). Usar lenguaje suave ("en la próxima captura, mañana por la mañana"), no un reloj puntual.
- **Una corrida diaria** para métricas de cuenta y post. Las **stories** necesitan captura
  **sub-diaria** (ver sección Stories).
- Disparo manual: `POST /sync/run` `[ADMIN]` para re-procesar o probar. [[03-contratos-api]]
- Cada corrida crea un `sync_runs` (`RUNNING`→`SUCCESS`/`PARTIAL`/`FAILED`) y un
  `sync_account_results` por cuenta. [[02-modelo-de-datos]]

## Pipeline por cuenta (orden estricto)

1. **Refrescar token si hace falta** (según `expires_at`). [[09-flujo-oauth]]
2. **Consultar la API** (insights de cuenta, lista de posts, insights de posts según catálogo CORE).
3. **Guardar el payload crudo** en `raw_api_payloads` (jsonb). [[02-modelo-de-datos]]
4. **Derivar e insertar** filas en `account_metric_snapshots` / `post_metric_snapshots`.
5. **Upsert de `posts`** (metadatos) por `UNIQUE(account_id, external_post_id)` — sin duplicar.
6. **Cachear miniaturas** en `media_assets`: bajar `remote_thumbnail_url` → `StoragePort` → fila
   `THUMBNAIL`. **Best-effort** (un fallo de red/storage NO aborta la captura ya hecha de la cuenta:
   corre en la misma tx pero traga el error sin marcar rollback) e **idempotente** (no re-baja si el
   post ya tiene miniatura). Aplica a posts del feed **y** stories; así el reporte muestra miniaturas
   reales (no solo el `remote_thumbnail_url` que puede expirar). [[02-modelo-de-datos]]
7. Registrar resultado en `sync_account_results`.

Si una cuenta falla, se registra `ERROR`, **el job sigue con las demás** y la corrida termina
`PARTIAL`. Nunca un fallo individual tumba la corrida completa.

## Escaneo al conectar (sync por-cuenta)

Además de la corrida diaria, hay un **sync de una sola cuenta** que se dispara tras un connect OAuth
exitoso (mismo pipeline por-cuenta, una corrida de 1 cuenta). Lo lanza un evento `AFTER_COMMIT` del
callback, **asíncrono** (pool del job) y **best-effort**: si el scan falla, la cuenta queda conectada
igual. Trae posts + métricas + miniaturas al instante, sin esperar al job diario. [[09-flujo-oauth]]

## Idempotencia (clave)

Reintentar el mismo día **no debe corromper la serie**. Mecanismo:

- Cada snapshot lleva `capture_date` (date, en timezone del cliente) además de `captured_at`.
- **Unique** `(account_id, metric_key, capture_date)` en `account_metric_snapshots`; idem para post
  (`post_id, metric_key, capture_date`).
- El insert usa `ON CONFLICT (... capture_date) DO UPDATE SET value = EXCLUDED.value` → un re-run del
  mismo día **corrige el valor del día**, no agrega una fila nueva. La serie histórica (una fila por
  día) queda intacta. El crudo (`raw_api_payloads`) sí es append puro (guarda cada llamada).

> Esto refina "append-only": **una fila inmutable por día hacia el futuro**; reintentos del mismo día
> actualizan solo ese día. Ajuste aplicado en [[02-modelo-de-datos]].

## Stories (captura sub-diaria)

- Las stories de IG y sus métricas **solo viven 24 h**. Un job 1×/día las perdería.
- **Estrategia v1: webhook-first.** El webhook `story_insights` de Meta **avisa** cuando hay datos
  (push) → casi no hace falta polear. El **polling es solo red de seguridad liviana**, y **solo para
  cuentas que publican stories** (no a ciegas para todas).
- Al capturarla: guardar métricas (finales, sin serie) + **cachear la miniatura**. [[02-modelo-de-datos]]
- Sin webhook disponible (o TikTok/FB) → no aplica; stories = feature de Instagram. [[04-criterios-aceptacion]] (CU8)

## Rate limits y robustez

- Límites por red (con margen de sobra a escala ~10 clientes):
  - **Meta / Instagram:** **200 llamadas/hora por cuenta** (con 10 cuentas ≈ 2.000/h) + límite diario
    por business use case (escala con impresiones). Usar **batching** (hasta 50 ops/request) y leer
    headers `X-App-Usage` / `X-Business-Use-Case-Usage` para autorregular antes del 429.
  - **TikTok:** ~600 req/min por endpoint.
- Procesar cuentas **secuencialmente o con concurrencia acotada** + pausas.
- **Reintentos con backoff** ante errores transitorios (timeout, 5xx, rate limit) — N intentos por
  cuenta antes de marcar `ERROR`.
- **Timeout por cuenta** para que una cuenta colgada no frene la corrida.
- Datos de Meta tienen hasta **48 h de delay** → la serie puede ajustarse; por eso el upsert del día.

## Bootstrap histórico

- Las APIs **no dan historia** → al conectar una cuenta nueva, la serie **empieza ese día**. No hay backfill.
- Sí se puede traer la **lista de posts existentes** (con sus métricas actuales) al conectar, para
  poblar `posts` y tener un punto de partida.

## Observabilidad

- `sync_runs` + `sync_account_results` = historial auditable (visible en Admin). [[07-principios-ux]]
- Alerta si la corrida falla o no se ejecutó (uptime/health). [[06-infra-deploy-v15]]
- Logs sin tokens ni datos sensibles.

## Decisiones abiertas

- Frecuencia exacta del polling de stories (3 h vs 6 h) y si se activa solo para cuentas que postean stories.
- Concurrencia del job (secuencial vs pool de N) — afinar según volumen y límites reales.
- ¿Idempotencia del día = "último valor gana" (upsert) o "primero gana"? Recomendado: último gana.
