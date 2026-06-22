# Spec — Capa 4: Criterios de Aceptación (v1)

> Estado: **CERRADA**.
> Métricas: **abiertas y por red**. El set de cada plataforma se define en el catálogo `metrics`
> (campos `platform` + `level`), no en el esquema. IG, FB y TikTok pueden tener métricas distintas
> sin cambios de tablas. Se irán cargando por red de forma iterativa según lo que exponga cada API.
> Depende de: [01](01-definiciones-alto-nivel.md) · [02](02-modelo-de-datos.md) · [03](03-contratos-api.md).
> Formato: Dado / Cuando / Entonces. Cada criterio es verificable (test o demo).

## CU1 — Onboarding OAuth de un cliente

- **Dado** un usuario autenticado (admin o empleado) y un cliente existente,
  **cuando** inicia la conexión de una red (`POST /clients/{id}/accounts/connect/{platform}`),
  **entonces** recibe una `authorizationUrl` oficial de Meta/TikTok y un `state` de un solo uso.
- **Dado** que el cliente autorizó la app en la pantalla oficial,
  **cuando** la red redirige al callback con un `code` válido,
  **entonces** se canjea por un token de larga duración, se crea `social_accounts` +
  `account_credentials` (token **cifrado**) y se redirige al front con `accountId`.
- **Dado** un `state` inválido, expirado o reusado, **entonces** el callback responde error y
  **no** se crea ninguna cuenta ni credencial.
- En **ningún** momento se solicita ni almacena la contraseña del cliente.
- El token **nunca** se devuelve al front en ninguna respuesta de API.

## CU2 — Job diario de captura

- **Dado** cuentas en estado `CONNECTED`, **cuando** corre el job (scheduler o `POST /sync/run`),
  **entonces** por cada cuenta guarda el payload crudo en `raw_api_payloads` y deriva filas en
  `account_metric_snapshots` (y `post_metric_snapshots` cuando aplica), todas con `captured_at`.
- **Dado** un token próximo a expirar, **cuando** corre el job, **entonces** lo refresca
  automáticamente y actualiza `last_refreshed_at` antes de consultar.
- **Dado** que una cuenta falla (token revocado, error de API), **entonces** se registra en
  `sync_account_results` con `ERROR`, el job **continúa** con las demás y la corrida termina
  como `PARTIAL`.
- Las tablas de snapshots son **append-only**: el job nunca hace UPDATE/DELETE sobre capturas.
- Cada corrida deja un registro en `sync_runs` con `status`, totales y `error_summary`.

## CU3 — Dashboard por cliente

- **Dado** un cliente con datos capturados, **cuando** se consulta su dashboard,
  **entonces** se ven métricas filtrables **por red social** y por **rango de fechas**.
- **Dado** una métrica y un rango, **cuando** se pide la serie (`/accounts/{id}/metrics`),
  **entonces** devuelve puntos ordenados por `captured_at` con su `value`.
- **Dado** un rango sin datos, **entonces** la respuesta es una serie vacía (no error).
- **Dado** un `metric_key` inexistente en el catálogo, **entonces** responde `400/422`.
- El dashboard se arma **dirigido por el catálogo** `metrics`: muestra las métricas que esa red
  tiene cargadas, sin asumir paridad entre IG, FB y TikTok.

## CU4 — Gestión de usuarios y clientes prioritarios

- **Dado** un admin, **cuando** crea un empleado, **entonces** queda activo y puede loguearse.
- **Dado** un **empleado**, **cuando** intenta acceder a endpoints `[ADMIN]`, **entonces**
  recibe `403`.
- **Dado** un empleado, **cuando** lista clientes, **entonces** ve **todos** (sin restricción).
- **Dado** un admin que marca un cliente como prioritario para un empleado, **cuando** ese
  empleado consulta `/me/priority-clients`, **entonces** aparece ese cliente. El flag es
  **informativo** y no cambia qué puede ver o gestionar.
- **Dado** un usuario desactivado (`isActive=false`), **entonces** no puede autenticarse.

## CU5 — Reporte exportable

Dos tipos de reporte (`report_type`), ambos exportables a `MARKDOWN` / `PDF`, síncronos (escala v1):

**a) Resumen** — vista rápida para el cliente.
- KPIs del período, evolución del alcance, desglose por red y top 3 publicaciones. Limpio, 1 página.

**b) Extendido** — análisis publicación por publicación (idea del equipo).
- **Dado** un cliente y un período, **cuando** se genera el reporte extendido, **entonces** lista
  **cada publicación con su miniatura y sus métricas**, agrupada por red y por tipo (ej. los 2 Reels
  de "Molinos" por separado, los 6+ feed por separado), para poder **comparar entre sí**.
- Incluye **"Destacadas del mes" (top 3)** y **"Con más margen de mejora"** (bottom 3) — etiqueta en
  tono constructivo, no "peores". Aplica a publicaciones y, por separado, a **historias**.
- Las **miniaturas** (de posts e historias) se muestran para facilitar la lectura. Para historias se
  usa la miniatura cacheada. [[02-modelo-de-datos]]
- Objetivo accionable: ver qué formato/publicación funcionó para **repetir lo que funciona y
  descartar lo que no** el mes siguiente.

**Comunes:**
- **Dado** un `reportId`, **cuando** se descarga, **entonces** el archivo tiene el `format` pedido y
  refleja exactamente el período, las redes y el `report_type` solicitados.
- El criterio de orden (qué es "destacada") es configurable por métrica (alcance, engagement, etc.).
- **Secciones separadas por tipo** (Reels / Feed / Stories): métricas no comparables entre tipos →
  se agrupan para comparar "como con como".
- **Orden:** en las secciones por tipo, **cronológico del más nuevo al más antiguo** por defecto
  (lo último publicado primero; con opción de reordenar por métrica). En "Destacadas" y "Con más
  margen de mejora", por **rendimiento** (ranking).
- **Layout: grilla de miniaturas** (no lista) — Reels en 9:16, Feed en 1:1, como se ven en la red.
  Métrica principal sobre la miniatura + fecha debajo; estrella para destacadas. Compacto y
  print-friendly. El detalle completo de cada publicación se abre al tocar la miniatura.
- **Fecha de publicación siempre visible** en cada publicación e historia.
- Aunque sea extendido, respeta la legibilidad: agrupado, miniaturas como ancla visual; nada de
  muros de datos. [[07-principios-ux]]

## CU6 — Métricas por post

- **Dado** una cuenta con publicaciones, **cuando** corre el job, **entonces** se detectan e
  insertan/actualizan filas en `posts` (sin duplicar: `UNIQUE (account_id, external_post_id)`).
- **Dado** un cliente/cuenta y un rango, **cuando** se consultan los posts, **entonces** se
  pueden ordenar por métrica (ej. engagement) para ver los top.
- **Dado** un post, **cuando** se pide su métrica en el tiempo, **entonces** devuelve la serie
  de `post_metric_snapshots`.
- **Dado** la vista de publicaciones, **cuando** se ordena por una métrica (likes, reach, views),
  **entonces** se ven los **top posts** con su **miniatura** y link al original (`permalink`).
- **Dado** un post, **cuando** se abre su previsualización, **entonces** se muestra la miniatura
  cacheada (o `remote_thumbnail_url` de forma diferida) sin almacenar el video completo.

## CU7 — Tipo de cuenta y degradación elegante

- **Dado** el onboarding de una cuenta, **cuando** se conecta, **entonces** el sistema detecta
  `account_type` y `capabilities` y guarda solo lo soportado.
- **Dado** una cuenta **personal de IG/FB**, **cuando** se intenta onboardear, **entonces** queda
  `UNSUPPORTED` con aviso claro (pasar a profesional), **sin** romper el flujo.
- **Dado** una cuenta de TikTok personal, **cuando** corre el job, **entonces** captura los
  conteos disponibles (followers, likes, video counts y por video) sin error; si es Business,
  además captura las métricas extra.
- **Dado** un cliente que cambia de tipo (personal↔business), **cuando** corre el job, **entonces**
  ajusta qué métricas pide desde ese día, **sin** backfill y **sin** romper, conservando snapshots previos.

## CU8 — Captura de Stories (Instagram)

- **Dado** una story publicada, **cuando** está dentro de la ventana de **24h**, **entonces** el
  sistema captura sus métricas (vía webhook `story_insights` o polling) y cachea su **miniatura**.
- **Dado** que pasaron >24h, **entonces** la story y sus insights ya no son consultables en la API;
  el sistema muestra lo que **alcanzó a capturar** (incluida la miniatura cacheada).
- **Dado** que el media original expiró, **cuando** se previsualiza una story histórica, **entonces**
  se sirve desde la miniatura cacheada, no desde la red.

## Transversales

- **Auth:** sin `accessToken` válido, todo endpoint protegido responde `401`.
- **JWT:** access token expirado → `401`; con refresh válido se obtiene uno nuevo (refresh
  rotado). Refresh revocado/reusado → `401` y sesión invalidada.
- **Multi-tenant:** toda consulta de métricas/posts/reportes filtra por `client_id`; no hay fuga
  de datos entre clientes.
- **Seguridad:** tokens y credenciales nunca aparecen en respuestas ni en logs.
- **Errores:** las respuestas de error siguen RFC 7807 con `status` correcto.

## Criterios no funcionales (v1)

- Corre **100% local** (backend + Postgres) con un comando documentado (ej. `docker compose up`).
- Tests automatizados cubren: flujo OAuth (mock de la red), job de captura (idempotencia y
  append-only), RBAC admin vs empleado, y generación de reporte.
- El job diario es **idempotente** ante reintentos del mismo día (no corrompe series).
