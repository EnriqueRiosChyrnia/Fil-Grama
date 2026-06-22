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

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/metrics` | catálogo `?platform=&level=` |
| GET | `/accounts/{id}/metrics` | serie temporal `?metric=ig_reach&from=&to=&granularity=day` |
| GET | `/clients/{clientId}/summary` | KPIs agregados `?from=&to=&platform=` |
| GET | `/accounts/{id}/posts` | publicaciones `?from=&to=&page=&size=&sort=` |
| GET | `/posts/{id}/metrics` | métricas del post `?metric=&from=&to=` |

`metric` usa el `metric_key` del catálogo, con prefijo de red (`ig_reach`, `fb_page_views`,
`tt_view_count`). [[05-catalogo-metricas]]

Ejemplo serie temporal:
```json
{ "accountId": 7, "metric": "ig_reach", "granularity": "day",
  "points": [ {"capturedAt":"2026-06-01T03:00:00Z","value":12450},
              {"capturedAt":"2026-06-02T03:00:00Z","value":13010} ] }
```

---

## Reportes

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/clients/{clientId}/reports` | genera reporte (síncrono) → `201` + recurso |
| GET | `/clients/{clientId}/reports/{reportId}` | metadatos del reporte |
| GET | `/clients/{clientId}/reports/{reportId}/download` | descarga el archivo |

Request:
```json
{ "reportType": "EXTENDED", "format": "PDF", "from": "2026-05-01", "to": "2026-05-31",
  "platforms": ["INSTAGRAM","TIKTOK"], "rankBy": "reach" }
```
`reportType` ∈ `SUMMARY` \| `EXTENDED`. `format` ∈ `MARKDOWN` \| `PDF`. `rankBy` = métrica para
ordenar destacadas. v1 síncrono. El recurso devuelve
`{id, reportType, status, format, downloadUrl, createdAt}`.

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
