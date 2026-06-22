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
