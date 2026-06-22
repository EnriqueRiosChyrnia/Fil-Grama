# Fil-Grama — Plan de Frontend (fuente de verdad)

> Plan acordado para implementar el frontend. Complementa `HANDOFF.md` (contratos, pantallas, flujos):
> el HANDOFF dice **qué** consume cada pantalla; este doc dice **cómo** lo construimos y **en qué orden**.
> Metodología: terminal central dueña de `main` + sub-terminales en worktrees, en paralelo (la misma del
> backend). Estado: backend feature-complete (Spring Boot 4, 167 tests verdes); frontend **sin scaffoldear**.

## 1. Decisiones cerradas (jun-2026)

| Decisión | Elección | Por qué |
|---|---|---|
| Tipo de app / deploy | **React web + Vite + TypeScript**, deploy Cloudflare Pages | Target estático; TS es el contrato entre tracks paralelos. |
| Routing | **React Router** | Estándar SPA; router **por agregación** (ver §5). |
| Server-state | **TanStack Query (React Query)** | App ~95% estado de servidor: cache, dedupe, refetch, paginación y estados loading/empty/error unificados (HANDOFF §7). El interceptor JWT vive debajo, en el `fetch` base. |
| Charts | **Recharts** | Declarativa, React-first, se temiza con tokens. Cubre líneas/sparklines/barras de Comparar. |
| Tipos + cliente API | **Generados desde OpenAPI con `orval`** | Única fuente de verdad; `tsc` avisa ante cambios del backend. Genera tipos **+ hooks de React Query** (encaja con la decisión de server-state). Requiere springdoc en el backend (§2). |
| Token storage | **access en memoria + refresh en `localStorage`** (con CSP) | Pragmático para SPA cross-origin (Pages↔VPS), evita CSRF. Riesgo XSS documentado; upgradeable a cookie httpOnly si app y API comparten dominio raíz. |
| Tema | Tokens de `../design/filgrama-colors.css`, **light forzado en v1** (sin dark-mode automático) | El `prefers-color-scheme: dark` del CSS sorprende en una tool interna (la pantalla del empleado cambia según su OS). |

## 2. Prerequisitos (antes de Fase 0)

Estos dos puntos **bloquean** el scaffold y conviene resolverlos juntos:

1. **Monorepo.** Hoy el repo de GitHub está vinculado a `backend/`. El objetivo es la **raíz del proyecto**
   (padre que contiene `backend/`, `frontend/`, `spec/`, `design/`, docs) como **monorepo**. Esto importa
   porque la orquestación paralela usa **git worktrees**: la raíz del repo define desde dónde se crean los
   worktrees, las rutas de cada track, el CI y la config de deploy (Cloudflare Pages necesita apuntar al
   subdirectorio `frontend/`). **Mover la raíz antes de scaffoldear** evita reescrituras de rutas después.
2. **OpenAPI en el backend.** El backend **no** expone springdoc todavía. Para el codegen con `orval`:
   agregar `springdoc-openapi-starter-webmvc-ui` al `pom.xml`, levantar el backend una vez y apuntar `orval`
   a `http://localhost:8080/v3/api-docs`. Commit chico en `main` del backend; hacerlo junto con el push pendiente.

> **Nota de estado:** el repo se está reorganizando a monorepo. Hasta que eso se resuelva, **no se ejecutan
> comandos git** (ni worktrees). Este plan y el track de setup quedan escritos para aplicarse después.

## 3. Fase 0 — esqueleto (central, secuencial)

La central produce y **congela** todo lo compartido. Ningún track de Fase 1 toca esto después.

1. Scaffold **Vite + React + TS + React Router**.
2. **Tema:** importar `../design/filgrama-colors.css`; light forzado en v1.
3. **`lib/api`:** `fetch` base + `Authorization: Bearer` + refresh rotado en 401 (reintento; si falla → Login)
   + normalización de errores `application/problem+json` → objeto de error con `detail` humano.
4. **`lib/auth`:** `AuthProvider`, `useAuth`, carga de `/auth/me`, `ProtectedRoute`, **gate RBAC** ADMIN/EMPLEADO.
5. **React Query:** `QueryClientProvider` + convención de **query keys** + defaults (staleTime, retry sensato).
6. **Codegen (`orval`):** tipos del dominio (HANDOFF §5/§6) + hooks de React Query desde OpenAPI.
7. **Catalog context:** carga `/metrics` una vez → `displayName`, `unit`, `description` (tooltips ⓘ) + helper
   "lenguaje humano, nunca de API".
8. **Layout shell:** topbar/sidebar, navegación, slot de contenido, responsive (KPIs se apilan en móvil) +
   estados reusables **loading / empty / error** (estados vacíos amables, HANDOFF §7).
9. **`components/ui`:** botones, inputs, **selector cascada red→cuenta**, **date-range**, **KPI card**,
   **tooltip ⓘ**, **chips de red**, tabla, y los **chart primitives en Recharts** (línea/sparkline +
   barras-comparar con cada métrica escalada por separado).
10. **Router por agregación** (§5).
11. **Login** + ruta **`/oauth/result`** (lee `?accountId=` / `?error=`).
12. (Opcional) **Mapa de flujo** (pantalla de orientación).

## 4. Fase 0.5 — rebanada vertical (central)

Antes de fanear: la central hace **Login → Home → Dashboard de cliente** end-to-end contra el backend local.
Valida el cliente API, el patrón de hooks, los chart primitives y los estados vacíos/error. **El molde probado
es lo que los tracks copian.** Recién con esto validado se abre Fase 1.

## 5. Router por agregación (anti-conflicto)

El `router` central **no** se edita por track (sería un hotspot de merge). Cada track exporta sus rutas desde
su carpeta `features/<x>/routes.tsx`; la central las importa y wirea una sola vez. Rutas con lazy import.

## 6. Fase 1 — tracks paralelos

Reparto por carpeta `features/*` **disjunta**. Cada track toca **solo su carpeta + su `routes.tsx`**.

| Track | Pantallas (HANDOFF §8) | Carpeta dueña |
|---|---|---|
| **FA — Clientes** | Home/lista, Dashboard de cliente, wizard onboarding/conectar red, reconexión de token | `features/clients/*` |
| **FB — Cuentas & Posts** | Detalle por red/cuenta, Todas las publicaciones, Detalle de post/story | `features/accounts/*`, `features/posts/*` |
| **FC — Reportes & Comparar** | Reporte (SUMMARY/EXTENDED), Comparar cuentas (≤4, toggle Tabla↔Barras) | `features/reports/*`, `features/compare/*` |
| **FD — Administración** [ADMIN] | Usuarios (CRUD), prioritarios, sync/job | `features/admin/*` |

**Reglas duras (igual que el backend):**

- Cada track toca SOLO su carpeta `features/*` + registra sus rutas. **Nada** de `components/ui`, `lib/`,
  tipos generados, tema, ni el router central.
- Si un track necesita una primitiva nueva (componente, hook compartido, dep) → **PARÁ y pedila a la central**.
- Consumir los hooks/componentes **públicos** de Fase 0; no editarlos.
- `git rebase main` antes de empezar; un solo backend local en `:8080` para todos (read-mostly; seed
  `admin@filgrama.local` / `Admin123!`).
- Diseños de alta fidelidad vía MCP de Claude Design (`/design-login` en cada terminal; ver HANDOFF §14).

**Orden de merge sugerido:** FA → FB → FC → FD (FD es independiente, puede ir cuando sea). Gate entre merges:
`build + lint + typecheck` + **smoke E2E (Playwright)** de los flujos críticos (login, ver dashboard, generar
reporte, comparar).

## 7. Riesgo principal y plan B (lectura crítica)

El frontend **no** paraleliza como el backend: router, design system, charts, cliente API, tipos, layout y
catalog context son **compartidos y transversales**. La paralelización **solo paga si Fase 0 + 0.5 quedan
sólidas** y el reparto es por carpetas verdaderamente disjuntas. Si por tiempo se recorta Fase 0 → bajar a
**2 tracks** o ir **secuencial**: el costo de reconciliar componentes compartidos mal definidos supera la
ganancia del paralelismo.

## 8. Punto de unión (recordatorio del HANDOFF)

- Base URL por env: `VITE_API_BASE_URL` → `…/api/v1`; local `http://localhost:8080/api/v1`.
- Ruta de resultado OAuth en el front: `/oauth/result` (el backend redirige ahí con `?accountId=`/`?error=`).
  Si cambia puerto/ruta, avisar para ajustar `oauth.front-redirect-url` en el backend.
- CORS: el backend debe permitir el origen del front (coordinar al integrar).
- Tokens nunca se piden ni se muestran en el front.
