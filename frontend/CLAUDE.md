# CLAUDE.md — Fil-Grama Frontend

Contexto para sesiones de Claude en esta carpeta. Conciso a propósito.

## Qué es

Frontend web de **Fil-Grama**: herramienta **interna de una agencia** para analizar y reportar el
rendimiento orgánico de redes (Instagram, Facebook, TikTok) de sus clientes. Consume una API REST ya
implementada (Spring Boot 4, en `../backend`). **No** es una app de cara al cliente final.

## Fuente de verdad

- **`HANDOFF.md`** (esta carpeta) — contratos de API, pantallas, flujos, UX, punto de unión. **Leerlo siempre primero.**
- Spec del producto: `../spec/` (07 = principios UX y pantallas; 03 = contratos API; 01 = visión/roles).
- Diseño/marca: `../design/filgrama-colors.css` (tokens; azul marca `#1E66BC` + grises) y `../design/*.svg` (logos).
- **Diseños alta fidelidad (Claude Design):** https://claude.ai/design/p/4161a4d3-159d-43a0-818d-d958c9e9f02b
  — Claude solo los lee vía el **MCP de Claude Design** (no por fetch). En la sesión: `/design-login` →
  importar el proyecto con el MCP → implementar cruzando con la §8 del HANDOFF. Ver HANDOFF §14.
- Iterar diseño sin ser experto: `../guia-feedback-diseño.md`.

## Stack

> **PENDIENTE de confirmar antes de scaffoldear.** Recomendado: **React web + Vite** (el deploy es
> Cloudflare Pages → web, no React Native), React Router, cliente HTTP con interceptor JWT, lib de
> charts (Recharts o Chart.js). Actualizar esta sección cuando se decida.

## Convenciones (de la spec, no negociar sin motivo)

- **Divulgación progresiva:** mostrar poco por defecto (solo métricas CORE), detalle a un clic.
- **Lenguaje humano, nunca de API:** "Alcance", no `ig_reach`. Cada métrica con ícono **ⓘ** + tooltip
  (texto del `description` del catálogo `/metrics`).
- **Dirigido por el catálogo:** no asumir paridad entre IG/FB/TikTok; renderizar según lo que devuelve `/metrics`.
- **Multi-cuenta:** un cliente puede tener varias cuentas de la misma red → selectores por cuenta (cascada red→cuenta).
- **Estados vacíos amables** ("Aún no hay datos para este rango"), nunca tabla vacía ni error crudo.
- **RBAC:** ocultar/deshabilitar Administración para rol `EMPLEADO`. El rol viene en `/auth/me`.
- **Calma visual + responsive:** KPIs grandes arriba, se apilan en móvil; color con propósito.

## Integración con el backend

- Base URL por env (`VITE_API_BASE_URL`), local `http://localhost:8080/api/v1`.
- **Auth:** JWT access + refresh. Mandar `Authorization: Bearer <access>`; en 401 → `POST /auth/refresh`
  → reintentar; si falla → Login. "Recordar sesión" = refresh vivo. Sin reset self-service en v1.
- **OAuth onboarding:** `connect/{platform}` devuelve `authorizationUrl`; el backend redirige al front a
  `/oauth/result?accountId=` o `?error=`. Implementar esa ruta.
- **Tokens nunca** se piden ni se muestran en el front.
- Errores en `application/problem+json` (`{type,title,status,detail,instance}`) → mostrar `detail` humano.
- Seed dev para probar: `admin@filgrama.local` / `Admin123!`.

## Decisiones abiertas (resolver al empezar)

1. Confirmar **React web** + lib de charts.
2. Dónde guardar el refresh token: `localStorage` (simple, riesgo XSS) vs cookie httpOnly (toca backend).
3. **CORS** del backend para el origen del front (coordinar con backend al integrar).

## Integración Fase 1 (contratos congelados — leer antes de tocar features)

- **Mutaciones (PATRÓN BENDECIDO).** orval v8 NO genera mutation hooks usables: activar
  `query.useMutation` convierte los **GET** en `useMutation` (bug de naming). Por eso solo hay
  `useQuery` para GET. Para escrituras: importar la **fn cruda** (`postX` / `patchX` / `deleteX` de
  `api/generated/endpoints`) — pasa por el mutator (Bearer + refresh + `ApiError`) — y envolverla en un
  `useMutation` propio; invalidar con `getGet<Name>QueryKey(...)`. (FA/FB/FC/FD ya tienen su helper local.)
- **Concepto → métrica:** usar `lib/metrics` (`metricKeysForConcept` / `primaryMetricKey` / `isComparable`).
  `engagement` no tiene key cruda (keys=[]); `/summary` da `engagementRate` por red. Derivación
  interacciones/alcance·100 reproduce al backend. *Pendiente backend:* key de engagement por cuenta o
  `/accounts/{id}/summary`.
- **Catálogo `/metrics` sin `description`** → tooltips usan `displayName`; conceptos CORE usan el glosario
  local (`lib/catalog/concepts`).
- **Posts:** no existe `GET /posts/{id}` → el detalle recibe metadata por router state (deep-link/refresh
  degrada amable). `PostListItem` sin `isEphemeral` → derivar de `postType==='STORY'`. Orden cronológico =
  `published_at` (snake_case); `sort` sin dirección → 400.
- **Reportes:** `rankBy` (`reach|views|engagement|likes`, default `reach`) no está en OpenAPI; alias
  inválido → 422. *Pendiente:* exponerlo como enum en la spec.
- **Tokens/format disponibles:** `--fg-warning-bg/-fg/-border` (PARTIAL/UNSUPPORTED); `formatDateTime` en
  `lib/format`.

## Estado

Fase 0 + 0.5 + **Fase 1 (tracks FA Clientes, FB Cuentas&Posts, FC Reportes&Comparar, FD Admin) mergeados
en `main`**. Gate verde por merge (build+lint+typecheck+smoke Playwright). Backend v1 feature-complete;
local en `:8080` (perfil `local` seedea demo).
