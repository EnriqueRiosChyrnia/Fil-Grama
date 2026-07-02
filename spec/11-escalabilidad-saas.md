# Spec — Capa 11: Escalabilidad a SaaS (nota a futuro)

> Estado: **NOTA / FUTURO** — no es v1. Sirve para no tomar decisiones que cierren este camino.
> Contexto: posible evolución a SaaS multi-agencia (otras agencias pagan suscripción).
> Relacionado: [[01-definiciones-alto-nivel]] (norte nivel Metricool), [[10-job-diario]], [[02-modelo-de-datos]].

## La buena noticia: las APIs no son el cuello de botella

- El límite de Meta es **200 llamadas/hora _por cada cuenta_** (no un techo global repartido). Cada
  cuenta nueva **trae su propia cuota** → el límite **escala con la cantidad de cuentas**.
- Cada cuenta necesita **1 captura/día**. 10.000 cuentas = 10.000 capturas/día repartidas en 24 h
  ≈ ~7/min. Trivial para las APIs.
- TikTok: ~600 req/min por endpoint, holgadísimo.

→ Al crecer, lo que se escala es **la infraestructura propia**, no el acceso a las APIs.

## Qué se MANTIENE (decisiones ya SaaS-friendly)

- Jerarquía **multi-tenant** limpia (cliente → cuentas → snapshots/posts).
- **Snapshots append-only** + payload crudo.
- **JWT stateless** (escala horizontal sin sesión pegada al server).
- **Storage desacoplado** (`StoragePort` → R2).
- **Catálogo de métricas abierto** (sin cambios de esquema para sumar métricas).

→ Crecer es **extender, no reescribir**.

## Qué se EXTIENDE al pasar a SaaS

1. **Capa de tenant "organización".** Agregar `organization_id` **por encima** de `client_id` (cada
   agencia suscriptora es una organización). RBAC y todas las queries se scopean por organización.
2. **Worker escalable.** Pasar del cron único a **repartir las capturas a lo largo del día**
   (escalonado) y, más adelante, **workers horizontales con cola de trabajos** (ej. una queue +
   varios consumidores). Cada captura es independiente → paraleliza fácil.
3. **Base de datos.** **Particionar** los snapshots por fecha, índices, eventualmente réplicas de
   lectura. Política de retención del crudo.
4. **Relación con Meta a escala.** Una sola app de Meta para todos → mantener buen standing en App
   Review, autorregular con headers (`X-App-Usage`, `X-Business-Use-Case-Usage`), pedir límites
   mayores si hace falta. Considerar facturación/planes de la suscripción SaaS.
5. **Aislamiento y datos.** Garantizar que ninguna organización vea datos de otra (tests de tenancy);
   posible aislamiento por esquema o fila según volumen.

## Regla

No optimizar nada de esto en v1 (sería sobre-ingeniería para ~10 clientes). Solo **no cerrar la
puerta**: mantener la jerarquía y los contratos limpios para que sumar `organization_id` y escalar
workers sea aditivo. Es coherente con el norte de [[01-definiciones-alto-nivel]].

## Adición v1.1 (futuro) — autogestión + aislamiento de scope en la integración con IA

> Pedido 30-jun-2026. Rumbo para cuando se lance el SaaS multi-empresa y el **plan de autogestión**.
> NO es v1; nota para no cerrar la puerta.

**Dos tipos de tenant:**
- **Organización-agencia:** tiene cartera de clientes; cada cliente con sus cuentas (modelo actual).
- **Usuario autogestionado (ej. "Pepe"):** gestiona **sus propias** redes, sin agencia. Modelable como
  una organización de un solo "cliente = él mismo", para reusar toda la jerarquía sin caso especial.

**Aislamiento estricto (requisito duro):** ningún tenant ve datos fuera de su scope. Pepe no ve nada de
ninguna agencia; ninguna agencia ve lo de Pepe ni lo de otra. Se implementa con `organization_id` por
encima de `client_id` (ya previsto arriba) + RBAC + **toda query scopeada por organización** + tests de
tenancy. Posible aislamiento por esquema/fila según volumen.

**Integración con Claude / ChatGPT por tenant (MCP / conector):**
- Cada usuario conecta **su propia** cuenta de Claude (Desktop/Cowork) o ChatGPT a Fil-Grama vía
  **MCP/conector remoto**. La conexión se autentica como **ese** usuario (OAuth/token por usuario), nunca
  con un token global.
- El MCP server **deriva el scope del token del usuario** y filtra **en el backend** (jamás confía en
  parámetros que mande el cliente IA): un tool como `get_client_report_data(client_id)` debe **rechazar**
  cualquier `client_id` fuera del scope. La tenancy vive en el backend, no en el prompt.
- Resultado: la misma plataforma sirve a varias agencias y a autogestionados, y cada IA conectada solo lee
  lo que su dueño tiene permitido. Para ChatGPT, exponer el mismo backend como conector compatible
  (function-calling / MCP), reusando la capa de scope.

→ Regla aplicable **desde ya**: los `get_*` del MCP (capa 08) deben filtrar **siempre por el scope del
token**, aunque hoy haya un solo tenant, para que sumar `organization_id` + multi-IA sea aditivo.

## Adición v1.1 (futuro) — auto-publicación de contenido (v3, estilo Metricool)

> Investigado 30-jun-2026. Idea para una **v3**: programar y auto-publicar contenido desde Fil-Grama
> (como Metricool/Later). NO es v1/v2. Se deja anotado para no cerrar la puerta y para dimensionar el App Review.

**Cómo lo hace Metricool (referencia) [seguro, doc oficial de Metricool]:**
- Publica vía la **Instagram Content Publishing API** oficial: **feed, carruseles (hasta 10) y Reels**
  (Reels ya con audio del catálogo autorizado por Meta) → 100% automático.
- **Historias:** auto **solo en cuentas Business**; las **Creator** van por **notificación**.
- **Fallback "notificación":** lo que la API no puede postear solo (Historias con **stickers
  interactivos**, o **audio fuera del catálogo autorizado**) se resuelve con un **push al celular** que
  descarga el media y abre Instagram para terminar a mano. Es el estándar (Later/Hootsuite igual).
- **Límite:** 50 publicaciones / 24 h por cuenta (feed+reels+historias). Catálogo de música por API más
  chico que el de la app. Stickers interactivos no soportados por API.

**Qué implica para Fil-Grama v3:**
- El **producto Instagram ya está agregado** a la app (use case "Manage messaging & content on Instagram"),
  así que la superficie técnica está. Falta el permiso **`instagram_business_content_publish`** + su propio
  **App Review** (con screencast del flujo de publicación).
- **Decisión:** **NO** pedir el permiso de publicación en el App Review de v1/v2 (mantener la revisión
  liviana, solo lectura/insights). Recién en v3, cuando se construya la función.
- **Modelo de datos futuro:** tabla `scheduled_posts` (`client_id`, `account_id`, refs de media, `caption`,
  `scheduled_at`, `status`, `publish_mode` = `AUTO` | `NOTIFICATION`) + worker que publica a la hora
  (reusa el patrón del job: cola + idempotencia). Soportar el fallback notificación para Historias/stickers.
- Encaja con el norte multi-tenant: cada publicación scopeada por `organization_id`/`client_id`/`account_id`.

Fuentes: help.metricool.com (Schedule and Post on Instagram; Auto-publish Stories; Publish via notification);
developers.facebook.com/docs/instagram-platform (Content Publishing).

## Adición v1.1 (futuro) — archivar / eliminar cliente

> Idea de Enrique (1-jul-2026, tras probar el onboarding real). Hoy `clients.status` ya contempla
> `ARCHIVED` ([[02-modelo-de-datos]]) pero falta definir la semántica. Spec a acordar antes de codear.

**Archivar** (soft, reversible): el cliente sale de las vistas activas/dashboards y **se pausa la captura**
de sus cuentas (el job diario lo ignora, igual que una cuenta `DISCONNECTED`), pero **se conserva toda la
historia** (snapshots, posts, reportes) para consultarla o reactivarlo. Útil cuando el cliente pausa el
servicio o deja de pagar pero no querés perder los datos. Sus cuentas quedan en pausa (sin sincronizar) y
sin borrar credenciales; se puede desarchivar y reanudar.

**Eliminar** — dos opciones a decidir:
- **Borrado lógico (recomendado):** `DELETED`, oculto en toda la UI, **revoca/borra credenciales** de sus
  cuentas (como `REMOVED` a nivel cuenta) pero conserva la fila + historia por N meses (auditoría / posible
  restauración) antes de purga física. Coherente con el soft-delete que ya usa el proyecto.
- **Borrado físico:** cascada a cuentas/credenciales/snapshots/posts/reportes. Solo bajo pedido explícito
  (derecho al olvido) + confirmación fuerte; irreversible.

**Decisiones pendientes:** (1) archivar = pausa captura (recomendado) vs solo ocultar; (2) eliminación
lógica con purga diferida vs física inmediata; (3) qué pasa con reportes ya generados de un cliente
eliminado; (4) permisos (¿solo admin?). Relacionado: ciclo de vida de cuenta (`REMOVED`) en
[[09-flujo-oauth]].

## Adición v1.1 (futuro) — tema del reporte por cliente (paleta + tipografías)

> Idea de Enrique (1-jul-2026). Hoy el reporte automático sale con la **marca Fil-Grama (azules/gris)** por
> defecto — está bien así para v1. A futuro: permitir **personalizar el reporte por cliente** con su propia
> **paleta de colores** y **tipografías** (títulos + cuerpo), para que cada reporte se vea con la marca del
> cliente (como el que hicimos a mano para Molino Don Alexis, que era amarillo + Gill Sans/Palatino).

**Idea de implementación (a acordar):**
- **Modelo:** un "tema" por cliente (`client_themes` o JSON en `clients`): `primary_color` (+ derivados
  50/20%), `text_color`, `heading_font`, `body_font`, opcional logo. El `PdfRenderer` toma el tema del
  cliente; si no hay, usa el default Fil-Grama.
- **Carga de fuentes:** subir/registrar las tipografías del cliente (ojo con archivos rotos: validar el cmap,
  como pasó con la Palatino de Molino → hubo que usar el clon Pagella). Fallback a fuentes seguras.
- **Vía MCP (alineado a [[08-ia-reportes]]):** Claude podría **elegir/guardar** paleta y tipografías del
  reporte por cliente (`save_report_theme(client_id, palette, fonts)`), o respetar el tema ya definido.
- **Default:** marca Fil-Grama. La personalización es opt-in por cliente.

## Adición v1.1 (futuro lejano) — bandeja de mensajería centralizada

> Idea de Enrique (30-jun-2026): después de auto-publicación + calendario, sumar una **bandeja unificada**
> de mensajes/comentarios (DMs y comentarios de IG/FB) dentro de Fil-Grama, para responder a los seguidores
> de los clientes desde un solo lugar. Roadmap tentativo: **reportes (v1-2) → auto-publish + calendario (v3)
> → mensajería centralizada (v4)**. NO es ahora.

**Viabilidad técnica:** la base ya está encaminada — la app de Meta tiene los use cases de mensajería
(`instagram_manage_messages`, `pages_messaging`) y comentarios (`instagram_manage_comments`,
`pages_manage_engagement`) disponibles; los webhooks de Meta (`messages`, `comments`) empujan los eventos
en tiempo real. Requeriría: permisos extra + su App Review, un servidor de webhooks (ya previsto para
stories), modelo de conversaciones/mensajes, y respetar la **ventana de mensajería de 24 h** de Meta.
Mismo principio de tenancy: todo scopeado por `organization_id`/`client_id`/`account_id`.
