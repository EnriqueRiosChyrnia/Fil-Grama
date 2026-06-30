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
