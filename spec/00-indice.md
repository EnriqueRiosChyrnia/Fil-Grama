# Fil-Grama — Plan / Spec (índice)

> Plataforma analítica de redes sociales para una agencia de marketing que gestiona las cuentas
> orgánicas (Instagram, Facebook, TikTok) de sus clientes. Metodología: Spec-Driven Development.
> Este índice es la entrada a la spec; cada capa es la fuente de verdad de su tema.
> Última revisión global: 21-jun-2026.
>
> **✅ PLAN APROBADO — 22-jun-2026.** La spec de alto nivel es la base acordada para implementar.
> Próximo: detallar el flujo OAuth por red y/o bootstrap del proyecto Spring Boot.

## Estado de las capas

| # | Capa | Estado |
|---|---|---|
| 01 | [Definiciones de alto nivel](01-definiciones-alto-nivel.md) | CERRADA |
| 02 | [Modelo de datos](02-modelo-de-datos.md) | CERRADA |
| 03 | [Contratos de API](03-contratos-api.md) | CERRADA |
| 04 | [Criterios de aceptación](04-criterios-aceptacion.md) | CERRADA |
| 05 | [Catálogo de métricas por red](05-catalogo-metricas.md) | CERRADA |
| 06 | [Infraestructura y deploy (v1.5)](06-infra-deploy-v15.md) | PLANIFICADO |
| 07 | [Principios de UX y diseño](07-principios-ux.md) | CERRADA |
| 08 | [Análisis con IA en reportes](08-ia-reportes.md) | PLANIFICADO (v2) |
| 09 | [Flujo OAuth y manejo de tokens](09-flujo-oauth.md) | EN REVISIÓN |
| 10 | [Estrategia del job diario](10-job-diario.md) | EN REVISIÓN |
| 11 | [Escalabilidad a SaaS (nota futura)](11-escalabilidad-saas.md) | NOTA / FUTURO |
| — | [Investigación de APIs (research)](research/05-investigacion-metricas-apis.md) | Referencia |
| — | [Paleta de marca](../design/filgrama-colors.css) | — |

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
