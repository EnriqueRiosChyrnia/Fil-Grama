# Fil-Grama — Plan / Spec (índice)

> Plataforma analítica de redes sociales para una agencia de marketing que gestiona las cuentas
> orgánicas (Instagram, Facebook, TikTok) de sus clientes. Metodología: Spec-Driven Development.
> Este índice es la entrada a la spec; cada capa es la fuente de verdad de su tema.
> Última revisión global: 28-jun-2026.
>
> **✅ PLAN APROBADO (22-jun-2026) · EN IMPLEMENTACIÓN.** Backend Spring Boot + frontend React andando en
> **local (Docker)**, código en **GitHub**. **OAuth real de TikTok funcionando end-to-end** (sandbox, con
> PKCE) y la **feature de connect-link completa** (link compartible → QR de marca → onboarding multi-cuenta
> → selector de red). Próximo: Meta (IG/FB) App Review + onboarding real, y deploy en Vultr (capa 06).
> Ver "Estado de implementación" abajo.

## Estado de las capas

| # | Capa | Estado |
|---|---|---|
| 01 | [Definiciones de alto nivel](01-definiciones-alto-nivel.md) | CERRADA |
| 02 | [Modelo de datos](02-modelo-de-datos.md) | CERRADA |
| 03 | [Contratos de API](03-contratos-api.md) | CERRADA |
| 04 | [Criterios de aceptación](04-criterios-aceptacion.md) | CERRADA |
| 05 | [Catálogo de métricas por red](05-catalogo-metricas.md) | CERRADA |
| 06 | [Infraestructura y deploy (v1.5)](06-infra-deploy-v15.md) | PLANIFICADO (+ dev: túnel cloudflared documentado) |
| 07 | [Principios de UX y diseño](07-principios-ux.md) | CERRADA |
| 08 | [Análisis con IA en reportes](08-ia-reportes.md) | PLANIFICADO (v2) |
| 09 | [Flujo OAuth y manejo de tokens](09-flujo-oauth.md) | **IMPLEMENTADA** (TikTok e2e; Meta pend. App Review) |
| 10 | [Estrategia del job diario](10-job-diario.md) | EN REVISIÓN |
| 11 | [Escalabilidad a SaaS (nota futura)](11-escalabilidad-saas.md) | NOTA / FUTURO |
| — | [Investigación de APIs (research)](research/05-investigacion-metricas-apis.md) | Referencia |
| — | [Reporte automático IG + integración MCP/Claude (research)](research/06-reporte-automatico-ig-y-mcp.md) | Referencia |
| — | [Paleta de marca](../design/filgrama-colors.css) | — |

## Estado de implementación (28-jun-2026)

**Andando en local (Docker), código en GitHub:** backend Spring Boot (auth JWT, clientes, cuentas,
métricas, sync/job, reportes, storage) + frontend React.

**OAuth real — TikTok (sandbox) end-to-end ✅:**
- **PKCE obligatorio** (`code_challenge`/`code_verifier`), `disable_auto_auth`, refresh con rotación.
- **Ciclo de vida de cuenta:** reconectar inteligente (refresh→reactivar), desconectar (pausa),
  **eliminar/dar de baja** (solo admin, estado `REMOVED`, migración V6); el listado oculta `REMOVED`.
- **Connect-link compartible** completo: endpoint público + página `/connect/{token}`, **QR de marca**
  (azul Fil-Grama + estilo por red, `qr-code-styling`), **onboarding multi-cuenta** (lista de cuentas +
  "conectar otra", sin callejón), **selector de red** al generar. Probado desde el celular (link por WhatsApp).
- **Dev:** corre vía Docker + **túnel cloudflared** (`api`/`app.fil-grama.com`); detalle y migración a
  prod en [[06-infra-deploy-v15]].

**Pendiente:**
- **Meta (Instagram/Facebook):** los providers existen, pero falta **App Review (Advanced Access)** y
  probar el onboarding real — solo **TikTok** está testeado end-to-end.
- **Deploy en Vultr** (capa 06): hoy todo local.
- **Repo público → privatizar antes de prod** y mover ToS/Privacy fuera de GitHub Pages.
- Validaciones runtime sueltas: scan real de los QR de color (caveat del cian en los finders de TikTok).

**Próximo gran objetivo — Reporte automático de Instagram (handoff):** ver
`tracks/FG-PLAN-reporte-automatico.md` (estado + orden de tracks + dónde continuar). Base hecha: se armó a
mano el reporte mensual de un cliente real (Molino Don Alexis) = **referencia visual del PDF automático**;
análisis API↔reporte en [[research/06-reporte-automatico-ig-y-mcp]]; **spec 02 y 05 actualizadas** (tabla
`audience_demographics` + métricas v1.1). Futuro multi-tenant + IA por usuario (autogestión) en
[[11-escalabilidad-saas]].

## Decisiones tomadas (resumen)

- **Producto:** herramienta interna de la agencia, orgánico (IG/FB/TikTok), reportes por cliente/red.
  Norte: nivel Metricool, por capas.
- **Roles:** admin (todo) y empleado (ve/gestiona todo salvo usuarios/config). Cliente no tiene login.
  Prioritarios = flag informativo. Onboarding OAuth lo hacen admin y empleado.
- **Arquitectura de datos:** multi-tenant (cliente → cuentas → snapshots/posts), snapshots
  **append-only**, payload **crudo** guardado, catálogo de métricas **abierto** (CORE en v1,
  EXTENDED a futuro), `metric_key` con prefijo de red.
- **Backend:** Spring Boot. **Auth:** JWT (access + refresh rotado).
- **Hosting (v1.5):** Vultr São Paulo (backend + Postgres + job 24/7) + Cloudflare Pages (front) +
  Cloudflare R2 (miniaturas). Optimizado para latencia a Paraguay. ~$11-13/mes. v1 = todo local.
- **Storage de media:** solo **miniaturas** (videos no), object storage desacoplado (`StoragePort`).
- **Cuentas:** detección de capacidades por cuenta; IG/FB personales → `UNSUPPORTED`; TikTok degrada
  bien; cambios de tipo se ajustan solos sin backfill.
- **Stories:** captura en ventana 24h (webhook) + miniatura cacheada.
- **Reportes:** dos modos (resumen / extendido). Extendido = grilla de miniaturas por tipo, orden
  más nuevo→más viejo, destacadas vs "con más margen de mejora", fecha siempre visible.
- **UX:** divulgación progresiva (vistazo → detalle → avanzado), métrica hero configurable en Home
  (rango 7 días), tooltips con glosario en lenguaje simple. Paleta azul/gris Fil-Grama.
- **IA (v2):** capa opcional; la app funciona sin Claude. Modo MCP (consumido interactivo bajo Max,
  $0 extra). API in-app descartada por ahora.
- **Plan del cliente:** etiqueta de texto libre, estética; contratos por fuera de la app.
- **Mejores horas/días para publicar (v2):** se derivan de `published_at` + `clients.timezone`;
  `published_at` se captura desde v1.
- **Onboarding self-service (jun-2026):** la agencia genera un **link compartible** y el cliente conecta
  su red desde su propio navegador (sin login, sin que la agencia cierre sesión en la red). Complemento
  dev: `disable_auto_auth=1` en TikTok. Premisa descartada: no se puede forzar la cuenta desde el
  backend (OAuth autoriza la sesión activa). El link se puede compartir como **QR** (estilo auto por red
  o azul Fil-Grama; frontend, `qr-code-styling`). Detalle en [[09-flujo-oauth]] · contrato
  [[03-contratos-api]] · datos [[02-modelo-de-datos]] (`connect_links`) · criterios
  [[04-criterios-aceptacion]] (CU9).
- **Ciclo de vida de cuenta (jun-2026):** estados `CONNECTED`/`DISCONNECTED` (pausa, token vivo)/`ERROR`
  (token muerto)/`UNSUPPORTED`/`REMOVED` (baja). **Reconectar inteligente** (refresh→reactivar; si el
  token murió → re-auth por agencia o por link). **Eliminar** (`DELETE`) revoca + borra credencial y
  conserva historia. Detalle y matriz de escenarios en [[09-flujo-oauth]]; criterios CU10
  [[04-criterios-aceptacion]].

## Pantallas (mockups aprobados)

Home (lista de clientes) · Dashboard de cliente · Detalle de post · Detalle de story · Reporte
(resumen y extendido) · Admin. Son referencia visual de baja-media fidelidad; el alta fidelidad se
hará luego en claude.ai (ver flujo de diseño en [[07-principios-ux]]).

## Revisión de consistencia (21-jun-2026)

Verificado el cruce entre capas. Ajustes aplicados en esta revisión:
- API de clientes acepta `plan` y `timezone` (alineado con el modelo).
- Ejemplos de la API usan `metric_key` con prefijo de red (alineado con el catálogo).
- CU5 (capa 01) refleja los dos modos de reporte.

Sin contradicciones pendientes detectadas entre capas.

## Decisiones abiertas (no bloquean el arranque)

- Flujo OAuth/onboarding: paso a paso técnico del token de larga duración + refresh por red (detalle
  de implementación).
- Estrategia del job diario: scheduling exacto, idempotencia, captura sub-diaria de stories.
- Dominio: compra/registro y conexión a Cloudflare (pendiente de explicar; parte de [[06-infra-deploy-v15]] Fase 3).
- CI/CD y migraciones de DB (Flyway/Liquibase) para v1.5.

## Próximos pasos sugeridos

1. Aprobar este plan.
2. Detallar el **flujo OAuth** por red (próxima capa de implementación).
3. Definir la **estrategia del job diario** y el bootstrap del proyecto Spring Boot.
