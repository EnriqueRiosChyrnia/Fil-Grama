# Spec — Capa 1: Definiciones de Alto Nivel

> Estado: **CERRADA**.
> Metodología: Spec-Driven Development. Este documento es fuente de verdad.
> Próxima capa: Modelo de Datos.

## Norte / ambición

A largo plazo, Fil-Grama apunta a ser una **herramienta profesional de uso serio, nivel Metricool**
(analítica, programación, reportes, IA, benchmarking). Ese es el norte que guía las decisiones de
arquitectura: el v1 es deliberadamente acotado, pero el modelo (multi-tenant, catálogo abierto,
snapshots + crudo, storage desacoplado) está pensado para crecer hacia ese nivel sin reescrituras.
**Regla:** cada versión suma capacidad sin romper la simplicidad ni la legibilidad (ver [[07-principios-ux]]).

## Versionado del producto

- **v1** — Núcleo analítico, corre **todo en local**.
- **v1.5** — Deploy en hosting barato (decisión diferida a esta etapa).
- **v2** — Publicador/scheduler de contenido, análisis con IA, recomendaciones y benchmarking competitivo.

---

## 1. Visión y alcance

Herramienta web **interna de la agencia** para medir, analizar y reportar el rendimiento
**orgánico** de las redes sociales (Instagram, Facebook, TikTok) de los clientes. Captura y
almacena métricas históricas día a día (snapshots propios, porque las APIs no devuelven series
históricas arbitrarias) y las presenta en reportes visuales segmentados **por cliente** y **por
red social**. El valor central: series históricas propias + reportes claros para mostrar al cliente.

### Fuera de alcance del v1
- Publicador / scheduler de contenido → **v2**.
- Análisis con IA, recomendaciones automáticas, benchmarking competitivo → **v2**.
- Pauta/ads y presupuestos (solo orgánico).
- X/Twitter ni otras redes.
- Portal de cara al cliente final (el cliente solo aparece en el onboarding OAuth).
- Facturación, CRM, gestión de tareas de la agencia.
- Deploy / hosting → **v1.5**.

---

## 2. Actores y roles

**Admin (dueño/líder de agencia):** acceso total. CRUD de clientes, cuentas conectadas y
**usuarios**. Define clientes prioritarios por empleado. Monitorea el job diario. Configuración del sistema.

**Empleado:** puede **ver y gestionar cualquier cliente** (sin restricción de acceso). Consulta
métricas, genera y exporta reportes. **No** gestiona usuarios ni configuración del sistema. Se le
pueden marcar clientes como **prioritarios** (flag informativo, para enfocar su trabajo — no limita acceso).

**Cliente (tercero):** **no tiene login ni cuenta en la plataforma.** Participa una sola vez:
autoriza la app de la agencia desde la pantalla oficial de Meta/TikTok durante el onboarding. No es
un rol RBAC; es un sujeto externo del flujo OAuth.

→ RBAC: dos roles (admin, empleado) + flag `prioritario` en la relación empleado↔cliente.
→ El **onboarding OAuth lo pueden hacer admin y empleado** (ambos conectan cuentas de clientes).

---

## 3. Casos de uso del v1 (priorizados)

1. **Onboarding OAuth de un cliente** — conectar cuenta (IG/FB/TikTok) vía OAuth de la app;
   guardar token de larga duración con refresh automático. Nunca se guardan contraseñas.
2. **Job diario de captura** — worker que consulta insights y guarda snapshots append-only por
   cuenta y por post.
3. **Dashboard por cliente** — métricas históricas y evolución, filtrable por red social.
4. **Gestión de usuarios y clientes prioritarios** (admin) — alta de empleados, marca de prioritarios.
5. **Reporte exportable lindo** — por cliente/red, con gráficos, en **Markdown y PDF**, en dos
   modos: **resumen** y **extendido** (detalle por publicación). (Núcleo v1.) [[04-criterios-aceptacion]]
6. **Métricas por post** — top posts, engagement por publicación (según lo que entregue cada API).

Núcleo imprescindible: 1–5. El 6 depende de la cobertura de cada API.

---

## 4. Decisiones técnicas

### (a) Backend → **Spring Boot** ✅
Stack en el que el dev es productivo; Spring Security cubre el RBAC; `@Scheduled` resuelve el job
diario; Spring Security OAuth2 Client maneja tokens + refresh. Las APIs de Meta/TikTok se consumen
por HTTP (no se depende de un SDK exclusivo de Python), así que el supuesto beneficio de FastAPI casi
desaparece. Base **nueva y limpia** (sin reutilizar Chambito — proyecto personal e independiente).

*Trade-off:* FastAPI arranca más liviano (menos RAM), pero a escala de ~10 clientes es irrelevante;
pesa más la velocidad de desarrollo.

### (b) Hosting → **DECIDIDO: Vultr (São Paulo) + Cloudflare** (se ejecuta en v1.5)
v1 corre todo en local (backend + Postgres, vía Docker Compose en la Mac).

**Criterio rector: velocidad para Paraguay.** Región más cercana = **São Paulo** (~30-50 ms desde
Asunción, vs ~120-150 ms EE.UU., ~200+ ms Europa). Por eso se descartaron Hetzner (EU/US),
DigitalOcean (sin región sudamericana) y Render/Railway (ídem).

Arquitectura confirmada (detalle y checklist en [[06-infra-deploy-v15]]):
- **Backend + Postgres + job 24/7:** **VPS Vultr en São Paulo** (~2 GB RAM, ~$10/mes), Docker Compose.
  Postgres self-managed en el mismo Compose (no se usa la DB manejada de Vultr).
- **Frontend:** Cloudflare Pages (gratis; cacheado en el PoP de **Asunción**).
- **Storage miniaturas + CDN:** Cloudflare R2 (gratis; cacheado en Asunción). [[02-modelo-de-datos]]
- **Regla:** lo estático (frontend, imágenes) → Cloudflare/Asunción; lo dinámico (backend, DB) → São Paulo.

El plan free de Cloudflare usa los mismos PoPs que el pago (no degrada velocidad). Fly.io quedó
descartado por costo (su Postgres manejado ~$38/mes rompe el presupuesto). Costo total estimado v1.5: **~$11-13/mes**.

### (c) Autenticación y sesión → **DECIDIDO (jun-2026)**
- **JWT** access + refresh rotado (Spring Security). El **refresh token habilita "recordar sesión"**:
  el usuario no vuelve a loguearse mientras el refresh siga vivo.
- **Sin recuperación de contraseña self-service en v1.** Con <10 usuarios internos creados a mano, un
  flujo de reset por email es sobre-ingeniería. **El admin resetea la contraseña desde Administración.**
  El link "¿Olvidaste tu contraseña?" del login dirige a "contactá al admin" (o se omite).
- **Sin SSO** en v1 (overkill para uso interno). Reevaluable si Fil-Grama se abre como producto.
- El **cliente (tercero) no tiene login**; solo participa en el onboarding OAuth (ver sección 2).
