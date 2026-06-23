# Spec — Capa 3: Contratos de API

> Estado: **CERRADA**.
> Depende de: [02-modelo-de-datos.md](02-modelo-de-datos.md).
> Base path: `/api/v1`. Formato: JSON. Auth: `Authorization: Bearer <token>`.
> Errores: RFC 7807 `application/problem+json`. Timestamps ISO-8601 UTC.
> **Auth: JWT** (access + refresh) — stateless, habilita clientes programáticos futuros (CLI, móvil).

## Convenciones

- **RBAC:** rutas `[ADMIN]` solo admin. Resto: `ADMIN` y `EMPLEADO` (los empleados ven y
  gestionan cualquier cliente).
- **Paginación:** `?page=0&size=20&sort=campo,desc`. Respuesta:
  `{ content: [...], page, size, totalElements, totalPages }`.
- **Error (ejemplo):**
  ```json
  { "type": "about:blank", "title": "Not Found", "status": 404,
    "detail": "Client 42 not found", "instance": "/api/v1/clients/42" }
  ```
- Códigos: `200` ok, `201` creado, `204` sin contenido, `400` validación, `401` no auth,
  `403` sin permiso, `404` no existe, `409` conflicto, `422` regla de negocio.

---

## Auth (JWT)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/login` | `{email, password}` → `{accessToken, refreshToken, user}` |
| POST | `/auth/refresh` | `{refreshToken}` → `{accessToken, refreshToken}` |
| POST | `/auth/logout` | revoca el refresh token → `204` |
| GET | `/auth/me` | usuario actual `{id, email, fullName, role}` |

- **Access token:** JWT corto (~15 min), firmado, claims `sub`, `role`, `exp`. Stateless,
  validado por filtro de Spring Security.
- **Refresh token:** larga duración (~7-30 días), **persistido y rotado** en cada uso (rotación
  con detección de reuso). Su revocación habilita el logout real.
- **Clientes programáticos (futuro):** la rotación de refresh tokens y el `role` en el claim
  dejan lista la base para emitir tokens de servicio/CLI más adelante, sin rediseñar auth.

---

## Usuarios `[ADMIN]`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/users` | lista paginada `?role=&active=&q=` |
| POST | `/users` | `{email, fullName, role, password}` → `201` |
| GET | `/users/{id}` | detalle |
| PATCH | `/users/{id}` | `{fullName?, role?, isActive?}` |
| GET | `/users/{id}/priority-clients` | clientes prioritarios del empleado |
| POST | `/users/{id}/priority-clients` | `{clientId}` → marca prioritario `201` |
| DELETE | `/users/{id}/priority-clients/{clientId}` | quita prioritario `204` |

---

## Clientes

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/clients` | lista `?status=&q=&page=&size=` |
| POST | `/clients` | `{name, notes?, plan?, timezone?}` → `201` |
| GET | `/clients/{id}` | detalle + resumen de cuentas conectadas |
| PATCH | `/clients/{id}` | `{name?, notes?, plan?, timezone?}` |
| POST | `/clients/{id}/archive` | soft-delete (status `ARCHIVED`) → `204` |
| GET | `/me/priority-clients` | prioritarios del empleado autenticado |

---

## Cuentas sociales y onboarding OAuth

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/clients/{clientId}/accounts` | cuentas conectadas del cliente |
| GET | `/accounts/{id}` | detalle (sin exponer tokens) |
| POST | `/clients/{clientId}/accounts/connect/{platform}` | inicia OAuth → `{authorizationUrl, state}` |
| GET | `/oauth/callback/{platform}?code=&state=` | callback: canjea code, crea cuenta + credencial, redirige al front |
| POST | `/accounts/{id}/disconnect` | status `DISCONNECTED` → `204` |
| POST | `/accounts/{id}/refresh-token` `[ADMIN]` | fuerza refresh del token |

`platform` ∈ `instagram` \| `facebook` \| `tiktok`. El front nunca recibe tokens; el `state`
es opaco y de un solo uso (anti-CSRF). El callback redirige a una URL del front con resultado
(`?accountId=` o `?error=`).

---

## Métricas y dashboard

Patrón de la industria (GA4 Data API `runReport`/`batchRunReports`, Adobe Analytics): **un endpoint
de informe flexible que recibe varias métricas + rango de fechas en una sola request**, más una
variante **batch** para traer varios informes en una llamada. No existe el patrón "una métrica por
request" (eliminado, sin legacy). Convención de ruta: custom methods con `:` (Google AIP-136).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/metrics` | catálogo `?platform=&level=` |
| POST | `/accounts/{id}/metrics:report` | informe de series de cuenta: N métricas + rango en una request |
| POST | `/metrics:batchReport` | batch: varios informes (cuentas/rangos) en una sola request |
| POST | `/posts/{id}/metrics:report` | informe de series de un post (mismo shape) |
| GET | `/clients/{clientId}/summary` | KPIs agregados `?from=&to=&platform=` |
| GET | `/accounts/{id}/posts` | publicaciones `?from=&to=&page=&size=&sort=` |

`metric` usa el `metric_key` del catálogo, con prefijo de red (`ig_reach`, `fb_page_views`,
`tt_view_count`). [[05-catalogo-metricas]]

### `POST /accounts/{id}/metrics:report`

Request:
```json
{
  "metrics": ["ig_reach", "ig_followers_count", "ig_total_interactions"],
  "dateRange": { "from": "2026-03-24", "to": "2026-06-22" },
  "granularity": "day"
}
```
- `metrics` — **requerido**, 1..N `metric_key` válidos del catálogo (validados contra `metrics`).
- `dateRange` — opcional; default = últimos 90 días. `from > to` → `400`.
- `granularity` — opcional, default `day` (v1 solo `day`; `week`/`month` reservados, sin reescritura).

Response (una serie por métrica, lista para graficar):
```json
{
  "accountId": 7,
  "dateRange": { "from": "2026-03-24", "to": "2026-06-22" },
  "granularity": "day",
  "series": [
    { "metric": "ig_reach", "unit": "count",
      "points": [ {"date":"2026-06-01","value":12450}, {"date":"2026-06-02","value":13010} ] },
    { "metric": "ig_followers_count", "unit": "count", "points": [ /* ... */ ] }
  ]
}
```
Rango sin datos → `series[].points` vacío (NO error). Métrica inexistente en el catálogo → `400`.

### `POST /metrics:batchReport`

Varios informes en una llamada (un round-trip para un dashboard multi-cuenta). Espejo de
`batchRunReports` de GA4.
```json
{
  "requests": [
    { "accountId": 7,  "metrics": ["ig_reach"],                      "granularity": "day" },
    { "accountId": 12, "metrics": ["tt_view_count","tt_follower_count"],
      "dateRange": { "from": "2026-03-24", "to": "2026-06-22" } }
  ]
}
```
Response: `{ "reports": [ { /* shape de :report */ }, … ] }`, en el mismo orden de `requests`.
Multi-tenant: cada `accountId` se resuelve a su `client_id` y se valida acceso de forma
independiente. Límite v1: máx. 20 requests por batch (`400` si se excede).

---

## Reportes

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/clients/{clientId}/reports:preview` | vista previa: devuelve el `ReportData` (datos), sin archivo |
| POST | `/clients/{clientId}/reports` | genera reporte (síncrono) → `201` + recurso |
| GET | `/clients/{clientId}/reports/{reportId}` | metadatos del reporte |
| GET | `/clients/{clientId}/reports/{reportId}/download` | descarga el archivo |

Request (de `:report` y de `:preview`, este último **sin `format`**):
```json
{ "reportType": "EXTENDED", "format": "PDF", "from": "2026-05-01", "to": "2026-05-31",
  "platforms": ["INSTAGRAM","TIKTOK"], "rankBy": "reach" }
```
`reportType` ∈ `SUMMARY` \| `EXTENDED`. `format` ∈ `MARKDOWN` \| `PDF`. `rankBy` = métrica para
ordenar destacadas. v1 síncrono. El recurso devuelve
`{id, reportType, status, format, downloadUrl, createdAt}`.

Errores comunes a ambos (validados al armar el `ReportData`, multi-tenant por `client_id`): cliente
inexistente → `404`; rango inválido (`from > to`, faltantes) → `400`; `rankBy` desconocido → `422`.
La generación es robusta: una falla inesperada al armar (p. ej. una miniatura cacheada cuyo objeto no
está en storage) **nunca** sale como `500` crudo — se mapea a una `ApiException` controlada.

### `POST /clients/{clientId}/reports:preview`

Vista previa para la pantalla: devuelve el **`ReportData`** (los MISMOS números que usa el renderer
del export) **sin generar ni persistir archivo**. El front usa `:preview` para la vista en pantalla y
`POST /reports` para exportar el PDF/MD → preview y export salen del **mismo armado** y no divergen
(SSOT). Request = el de arriba **sin `format`** (es sólo datos). Custom method `:` (Google AIP-136).

Response (`200`) — `ReportData` serializado:
```json
{
  "reportType": "EXTENDED", "format": null,
  "client": { "id": 1, "name": "La Cabrera Asunción", "timezone": "America/Asuncion", "plan": "Pro" },
  "period": { "from": "2026-05-01", "to": "2026-05-31", "previousFrom": "2026-03-31", "previousTo": "2026-04-30" },
  "platforms": ["FACEBOOK","INSTAGRAM"],
  "rankBy": "reach",
  "kpis": [ { "platform": "INSTAGRAM",
              "metrics": [ { "key": "ig_reach", "displayName": "Alcance", "unit": "count",
                             "value": 125400, "delta": 4200 } ],
              "engagementRate": 0.074, "followerGrowth": 180,
              "reach": { "current": 125400, "previous": 121200, "deltaPct": 3.5 } } ],
  "topPosts": [ { "id": 42, "platform": "INSTAGRAM", "postType": "REEL", "displayType": "Reels",
                  "publishedAt": "2026-05-20T13:00:00Z", "publishedAtLocal": "20 may 2026",
                  "permalink": "https://…", "caption": "…",
                  "thumbnailDataUri": "data:image/jpeg;base64,…", "thumbnailUrl": "https://…",
                  "story": false, "metricKey": "ig_post_reach", "metricName": "Alcance", "metricValue": 42000 } ],
  "postGroups": [ { "platform": "INSTAGRAM", "displayType": "Reels", "posts": [ /* ReportPost[] */ ] } ],
  "storyGroups": [],
  "postHighlights": { "top": [ /* ReportPost[] */ ], "bottom": [ /* ReportPost[] */ ] },
  "storyHighlights": { "top": [], "bottom": [] },
  "narrativeMd": null
}
```
`format` es `null` (la vista no exporta). En `SUMMARY` sólo se llena `topPosts`+`kpis`; en `EXTENDED`
además `postGroups`/`storyGroups`/`postHighlights`/`storyHighlights`. **Rango sin datos → estructura
vacía amable** (`kpis` por red con métricas en cero, `topPosts`/grupos vacíos), NO un error. No
inventa cifras: dato faltante = `null`. `:preview` no persiste fila ni archivo.

---

## Sincronización (job diario) `[ADMIN]`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/sync/run` | dispara corrida manual → `{runId}` `202` |
| GET | `/sync/runs` | historial paginado |
| GET | `/sync/runs/{id}` | detalle + resultados por cuenta |

---

## Notas

- El job diario corre por scheduler interno (`@Scheduled`); `POST /sync/run` es para disparo
  manual / re-procesar.
- Toda escritura sobre tokens y credenciales ocurre server-side; nunca se serializan al cliente.
- Validación de rango de fechas y `metric_key` contra el catálogo `metrics`.
