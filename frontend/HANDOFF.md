# Fil-Grama — Handoff de Frontend (v1)

> Documento autocontenido para implementar el frontend con Claude. Fuente de verdad: la spec en
> `../spec/`. Backend ya implementado (Spring Boot 4, 167 tests verdes). Este doc traduce la spec a lo
> que el front necesita: stack, contratos de API, pantallas, flujos, UX y punto de unión.

---

## 1. Visión y alcance

Herramienta web **interna de la agencia** (no de cara al cliente final) para medir, analizar y
reportar el rendimiento **orgánico** de redes (Instagram, Facebook, TikTok) de los clientes. El valor:
**series históricas propias** (el backend captura snapshots a diario) + **reportes claros** para
mostrar al cliente. Norte a largo plazo: nivel Metricool; v1 es acotado pero el modelo crece sin reescrituras.

**Fuera del v1:** publicador/scheduler (v2), IA/recomendaciones/benchmarking (v2), ads, X/Twitter,
portal para el cliente final, facturación/CRM.

## 2. Stack y deploy (recomendación)

- **App web SPA.** El deploy previsto es **Cloudflare Pages** (estático, cacheado en Asunción) → apunta
  a una **web React**, no React Native, para v1. (Tu stack habitual incluye ambos; acá el target de
  deploy define web.)
- **Backend** corre en VPS (São Paulo). El front consume su API REST. Base URL por entorno (env var).
- Sugerido: React + Vite + React Router + un cliente HTTP (fetch/axios) con interceptor para el JWT y
  el refresh. Librería de charts a elección (Recharts/Chart.js) para las tendencias.
- **Decisión tuya:** confirmá React web + la lib de charts antes de scaffoldear.

## 3. Roles y gating (RBAC)

- **Admin:** acceso total. CRUD de clientes, cuentas, **usuarios**; marca prioritarios; monitorea el job.
- **Empleado:** ve y gestiona **cualquier** cliente (sin restricción); consulta métricas, genera
  reportes. **No** gestiona usuarios ni configuración. Se le marcan clientes **prioritarios** (flag
  informativo, no limita acceso).
- **Cliente (tercero):** **no tiene login.** Solo participa una vez en el onboarding OAuth.

En el front: ocultar/deshabilitar la sección **Administración** (usuarios, prioritarios, job) para
empleados. El rol viene en el JWT y en `GET /auth/me` (`role: ADMIN | EMPLEADO`).

## 4. Autenticación y sesión

- **JWT** access (~15 min) + refresh rotado. El refresh habilita **"recordar sesión"**: no se vuelve a
  loguear mientras el refresh viva.
- **Sin recuperación self-service** en v1. El link "¿Olvidaste tu contraseña?" → "contactá al admin"
  (o se omite). El admin resetea desde Administración.
- **Sin SSO.**
- **Manejo en el cliente HTTP:**
  - Guardar access + refresh (memoria + persistencia para "recordar"; ver nota de seguridad abajo).
  - Mandar `Authorization: Bearer <access>` en cada request protegido.
  - En `401` por access expirado → llamar `POST /auth/refresh` con el refresh → reintentar. Si el
    refresh falla (revocado/reusado/expirado) → limpiar sesión y mandar a Login.
  - `POST /auth/logout` revoca el refresh.
  - **Nota seguridad:** el refresh es sensible. Decisión tuya: `localStorage` (simple, vulnerable a
    XSS) vs cookie httpOnly (requiere ajuste backend). Para v1 interno, documentá la que elijas.

## 5. Modelos de dominio (vista del front)

- **Client:** `{id, name, plan?, timezone, status: ACTIVE|ARCHIVED, notes?, createdAt}` + en el detalle,
  **resumen de cuentas conectadas**. `plan` es etiqueta libre estética.
- **SocialAccount:** `{id, clientId, platform: INSTAGRAM|FACEBOOK|TIKTOK, handle?, displayName?,
  accountType?, status: CONNECTED|DISCONNECTED|ERROR|UNSUPPORTED, connectedAt}`. **Un cliente puede
  tener varias cuentas de la misma red** (ver §10 multi-cuenta). Tokens **nunca** llegan al front.
- **Metric (catálogo):** `{key, displayName, platform?, level: ACCOUNT|POST, unit, description}`. El
  `description` alimenta los **tooltips ⓘ**. El front se arma **dirigido por el catálogo**, sin asumir
  paridad entre redes.
- **Serie temporal:** `{accountId, metric, granularity, points: [{capturedAt, value}]}`.
- **Post:** `{id, accountId, clientId, platform, postType: IMAGE|VIDEO|REEL|CAROUSEL|STORY, permalink?,
  caption?, remoteThumbnailUrl?, isEphemeral, publishedAt}` + sus métricas.
- **Report:** `{id, reportType: SUMMARY|EXTENDED, status, format: MARKDOWN|PDF, downloadUrl, createdAt}`.
- **User:** `{id, email, fullName, role, isActive}` (admin).
- **SyncRun:** `{id, startedAt, finishedAt?, status: RUNNING|SUCCESS|PARTIAL|FAILED, accountsTotal,
  accountsOk, accountsFailed}` + resultados por cuenta (admin).

## 6. Contratos de API

Base: `/api/v1`. JSON. Auth: `Authorization: Bearer <token>`. Errores: **RFC 7807**
(`application/problem+json`: `{type, title, status, detail, instance}`). Timestamps ISO-8601 UTC.
Paginación: `?page=0&size=20&sort=campo,desc` → `{content:[...], page, size, totalElements, totalPages}`.

**Auth**
- `POST /auth/login` `{email, password}` → `{accessToken, refreshToken, user:{id,email,fullName,role}}`. Inválido/inactivo → 401.
- `POST /auth/refresh` `{refreshToken}` → `{accessToken, refreshToken}`. Reuso/revocado → 401.
- `POST /auth/logout` `{refreshToken}` → 204.
- `GET /auth/me` → `{id, email, fullName, role}`.

**Usuarios [ADMIN]**
- `GET /users` `?role=&active=&q=&page=&size=` → página.
- `POST /users` `{email, fullName, role, password}` → 201. Email duplicado → 409.
- `GET /users/{id}` · `PATCH /users/{id}` `{fullName?, role?, isActive?}`.
- `GET /users/{id}/priority-clients` · `POST` `{clientId}` 201 · `DELETE /users/{id}/priority-clients/{clientId}` 204.

**Clientes**
- `GET /clients` `?status=&q=&page=&size=` → página.
- `POST /clients` `{name, notes?, plan?, timezone?}` → 201.
- `GET /clients/{id}` → detalle + resumen de cuentas conectadas.
- `PATCH /clients/{id}` `{name?, notes?, plan?, timezone?}`.
- `POST /clients/{id}/archive` → 204 (status ARCHIVED).
- `GET /me/priority-clients` → prioritarios del empleado autenticado.

**Cuentas y onboarding OAuth**
- `GET /clients/{clientId}/accounts` → cuentas del cliente (sin tokens).
- `GET /accounts/{id}` → detalle (sin tokens).
- `POST /clients/{clientId}/accounts/connect/{platform}` → `{authorizationUrl, state}`. `platform` ∈ `instagram|facebook|tiktok`.
- `GET /oauth/callback/{platform}?code=&state=` → **redirige** al front con `?accountId=` o `?error=` (no es para fetch).
- `POST /accounts/{id}/disconnect` → 204.
- `POST /accounts/{id}/refresh-token` [ADMIN] → fuerza refresh.

**Métricas y dashboard**
- `GET /metrics` `?platform=&level=` → catálogo.
- `GET /accounts/{id}/metrics` `?metric=ig_reach&from=&to=&granularity=day` → serie temporal. **Rango sin datos → `points: []` (no error).** metric inexistente → 400/422.
- `GET /clients/{clientId}/summary` `?from=&to=&platform=` → KPIs agregados por red (incluye engagement_rate y follower_growth derivados).
- `GET /accounts/{id}/posts` `?from=&to=&page=&size=&sort=` → posts (ordenables por métrica).
- `GET /posts/{id}/metrics` `?metric=&from=&to=` → serie del post.

**Reportes**
- `POST /clients/{clientId}/reports` `{reportType, format, from, to, platforms[], rankBy}` → 201 + `{id, reportType, status, format, downloadUrl, createdAt}`.
- `GET /clients/{clientId}/reports/{reportId}` → metadatos.
- `GET /clients/{clientId}/reports/{reportId}/download` → archivo (PDF/MD).

**Sincronización [ADMIN]**
- `POST /sync/run` → 202 `{runId}`.
- `GET /sync/runs` → historial paginado · `GET /sync/runs/{id}` → detalle + resultados por cuenta.

## 7. Estados y errores transversales

- **Carga / vacío / error** por pantalla. **Estados vacíos amables**: "Aún no hay datos para este
  rango", nunca tabla vacía ni error crudo.
- **Estado vacío del Dashboard** (cliente conectado pero el job aún no capturó): bloque cálido, sin
  KPIs ni charts vacíos; mostrar las cuentas conectadas como señal de que todo está bien. **"Generar
  reporte" deshabilitado (no oculto)** con tooltip "Disponible cuando haya datos". El copy del primer
  dato **no promete hora exacta**. Si **todas las cuentas están en error** → enlazar a Reconexión.
- **Reconexión de token:** cuando una cuenta queda `ERROR`/`DISCONNECTED`, pantalla/flujo para volver a
  conectarla (re-dispara el connect OAuth).
- **Errores de API** uniformes (problem+json): mostrar `detail` en lenguaje humano; 401 → refresh/relogin.

## 8. Pantallas del v1 (cerradas en alta fidelidad)

| Pantalla | Intención (una por vista) | Consume | Acciones | Notas |
|---|---|---|---|---|
| **Login** | Entrar | `POST /auth/login` | login | Sin barra/breadcrumb. Error sin revelar si el email existe. |
| **Home / lista de clientes** | "¿Cómo vienen mis clientes?" | `GET /clients`, `GET /clients/{id}/summary` | ir a cliente | Tarjeta con **un número hero** (selector métrica: Alcance/Seguidores/Interacciones/Engagement), redes (íconos), tendencia + sparkline. Rango **7 días**. Recordar elección (localStorage). |
| **Dashboard de cliente** | "¿Cómo viene este cliente?" | `GET /clients/{id}`, `/summary`, `/accounts/{id}/metrics` | filtrar red/cuenta y rango, generar reporte | 3-5 KPIs CORE arriba + 1 tendencia principal; selectores discretos. Estado vacío (§7). |
| **Detalle por red/cuenta** | rendimiento de esa cuenta | `/accounts/{id}/metrics`, `/accounts/{id}/posts` | drill-down a post | Métricas de la red + top posts (miniatura + métrica). |
| **Detalle de post / story** | "¿cómo le fue a esto?" | `GET /posts/{id}/metrics` | abrir permalink | Preview (miniatura/`remoteThumbnailUrl`) + métricas en el tiempo. |
| **Todas las publicaciones** | explorar/ordenar posts | `/accounts/{id}/posts` | ordenar por métrica | Grilla de miniaturas; orden cronológico por defecto. |
| **Comparar cuentas** | comparar hasta 4 cuentas | `/summary` o `/accounts/{id}/metrics` por cuenta | elegir cuentas/métrica, toggle Tabla↔Barras | Ver §11 (alcance cerrado). |
| **Reporte** | vista en vivo + exportar | `POST /clients/{id}/reports:preview` (vista), `POST /clients/{id}/reports` + `/download` (exportar) | refrescar preview, exportar MD/PDF | Se VE en pantalla (`:preview` → `ReportData`); el archivo se genera sólo al Exportar. SUMMARY (top 3) / EXTENDED (grilla por tipo). |
| **Administración** [ADMIN] | gestión | `/users`, prioritarios, `/sync/runs` | CRUD usuarios, marcar prioritarios, ver/disparar job | Fuera del uso diario; oculto a empleados. |
| **Mapa de flujo** | navegación/overview | — | — | Pantalla de orientación (cerrada en diseño). |

> **Navegación de cliente — por PESTAÑAS (track FE nav/reporte; SUPERSEDE el breadcrumb suelto).** Las
> pantallas client-scoped ya no se navegan sólo con el breadcrumb "‹": viven dentro de un **workspace de
> cliente** (`features/clients/ClientWorkspace`) con **header persistente** (nombre del cliente, chips de
> redes con estado + botón "Reconectar {red}" si hay cuentas en error, breadcrumb "‹ Clientes") y una
> **barra de pestañas** `Dashboard · Publicaciones · Comparar · Reporte` → paths `clients/:id`,
> `clients/:id/publicaciones`, `clients/:id/compare`, `clients/:id/report` (móvil = scroll horizontal). El
> detalle de cuenta (`accounts/:accountId`) y la grilla de posts (`accounts/:accountId/posts`) cuelgan del
> mismo workspace, así el header no se re-monta al navegar. Wiring: cada feature exporta `clientRoutes`
> (paths relativos) y `features/clients/routes.tsx` los anida bajo `<Outlet/>`; el router central sigue sin
> tocarse (glob). Cambios de navegación clave:
> - **Dashboard:** la lista "Cuentas conectadas" es **clickeable** → detalle de cuenta (antes no llevaba a ningún lado).
> - **Publicaciones** (entrada nueva): selector cascada red→cuenta → reutiliza la grilla `AllPostsPage`
>   (cuenta única = salto directo). Click en post → `posts/:postId` (fuera del workspace).
> - **Comparar:** la `CompareAccountsPage` existente, ahora alcanzable por pestaña.
> - **Reporte:** **vista en vivo + exportar** sobre `:preview` (ver fila de la tabla). Query key del preview:
>   `qk.feature('reportPreview', clientId, reportType, from, to, platforms, rankBy)`.

## 9. Flujos clave

- **Login → sesión:** login → guardar tokens → cargar `/auth/me` → Home. Access expira → refresh
  transparente → si falla, Login.
- **Onboarding OAuth (admin/empleado):** en el cliente → "Conectar {red}" →
  `POST /clients/{id}/accounts/connect/{platform}` → abrir `authorizationUrl` (pantalla oficial de
  Meta/TikTok; lo acompaña el operador) → la red redirige a `/oauth/callback/...` → el **backend
  redirige al front** a tu ruta de resultado con `?accountId=` (éxito) o `?error=` (ej.
  `unsupported_personal`, `invalid_state`). El front muestra el resultado y refresca la lista de
  cuentas. Cuenta personal IG/FB → queda `UNSUPPORTED` con aviso (pasar a profesional).
- **Generar reporte:** elegir tipo/formato/rango/redes/`rankBy` → `POST .../reports` (síncrono) →
  `downloadUrl` → descargar. Deshabilitado si no hay datos.
- **Comparar:** elegir ≤4 cuentas + métrica → totales del rango (default 30 días) → toggle Tabla↔Barras.

## 10. UX y diseño (de spec/07 + guía de feedback)

- **Divulgación progresiva:** vistazo → detalle (clic) → crudo/avanzado (escondido). Solo **CORE** en la
  vista inicial; EXTENDED en "ver más".
- **Jerarquía:** 3-5 KPIs grandes arriba; "una pantalla, una intención"; defaults inteligentes.
- **Lenguaje humano, no de API.** Nunca `ig_reach`; usar "Alcance". Cada métrica con ícono **ⓘ** y
  tooltip (texto del `description` del catálogo). Glosario en spec/07 §Glosario.
- **Multi-cuenta (transversal):** un cliente puede tener varias cuentas de la misma red → filtros y
  selectores **por cuenta** (cascada red → cuenta), no solo por red. Distinguir por chip de red +
  `handle`/`displayName` cuando hay >1.
- **Calma visual:** espacio en blanco, pocos bordes/colores; color con propósito. **Responsive:** KPIs
  se apilan en móvil.
- **Consistencia entre redes:** misma estructura visual IG/FB/TikTok aunque difieran las métricas.
- **Marca:** paleta en `../design/filgrama-colors.css` (azul marca `#1E66BC` + grises fríos; ya trae
  tokens y roles de UI). Logos/isotipo en `../design/*.svg`. Usar esos tokens como base del tema.

## 11. Comparar cuentas — alcance cerrado

- Cuentas **individuales** (incluso varias de la misma red), **no** agregado por red, **no** entre
  clientes. Hasta **4** a la vez.
- Métricas: **Alcance, Seguidores, Interacciones, Engagement**. Lo no equivalente entre redes (ej.
  "alcance" de TikTok = reproducciones) se marca **"—" + "no comparable"** con ⓘ.
- **Totales del rango** (default **30 días**), sin evolución temporal. Toggle **Tabla ↔ Barras por
  métrica** (cada métrica escalada por separado; mejor valor en negrita). Móvil: barras horizontales.

## 12. No incluido en el v1

Publicador/scheduler, IA/recomendaciones, benchmarking, ads, X/Twitter, portal cliente, reset de
contraseña self-service, SSO. No construir nada de esto todavía.

## 13. Punto de unión (front ↔ back)

- **Base URL** del backend por env (`VITE_API_BASE_URL` o equivalente) → `…/api/v1`. Local:
  `http://localhost:8080/api/v1`.
- **Ruta de resultado OAuth** en el front (la que el backend usa para redirigir): hoy el backend apunta
  a `oauth.front-redirect-url` = `http://localhost:5173/oauth/result` (Vite dev). Implementá esa ruta
  para leer `?accountId=` / `?error=`. Si cambiás el puerto/ruta, avisá para ajustar la config del backend.
- **CORS:** el backend deberá permitir el origen del front (coordinar al integrar; hoy dev local).
- **Seed admin (dev):** `admin@filgrama.local` / `Admin123!` para probar contra el backend local
  (se desactiva en prod).
- **Tokens nunca** se piden ni se muestran; todo lo sensible vive server-side.

---

## 14. Diseños de alta fidelidad (Claude Design)

Las pantallas ya están diseñadas en **Claude Design**:

**Proyecto:** https://claude.ai/design/p/4161a4d3-159d-43a0-818d-d958c9e9f02b

> **Importante — cómo accede Claude a estos diseños.** Un link **no** basta: Claude no puede leer una
> URL de claude.ai/design con fetch normal (restringido + render por JS). La **única** vía es el **MCP
> de Claude Design**. Pasos en la sesión de frontend:
> 1. Conectar el MCP: `/design-login` (otorga `user:design:read/write`).
> 2. Importar el proyecto por la URL de arriba con las tools del MCP.
> 3. Recién entonces Claude "ve" todas las pantallas y puede copiarlas/implementarlas.
>
> Implementá **cruzando** los diseños del MCP con la **§8 (lista de pantallas)** y los contratos de
> §6: el diseño da el layout/visual, este handoff da los datos, endpoints y estados de cada pantalla.
> Si una pantalla del diseño no coincide con §8, priorizá la spec y avisá la discrepancia.

### Cómo trabajar este handoff con Claude (en esta carpeta)
Abrí Claude en `frontend/` y usá este doc como fuente. Para iterar diseño sin ser experto, mirá
`../guia-feedback-diseño.md` (recetas de prompts para que Claude actúe como diseñador senior).
