# Spec — Capa 7: Principios de UX y Diseño

> Estado: **CERRADA** (mockups núcleo aprobados; alta fidelidad se hará luego en claude.ai).
> Objetivo declarado del producto: interfaz **bonita, intuitiva, que no agobie**. Tener TODO el
> detalle disponible, pero priorizar **legibilidad y utilidad** por sobre densidad de datos.
> Paleta: [[design/filgrama-colors.css]]. Catálogo CORE/EXTENDED: [[05-catalogo-metricas]].

## Marca

La marca de la empresa se escribe **"Fil-Grama"** (con guión y ambas mayúsculas) en TODO lo visible:
pantallas, títulos, PDF, textos y diseños. `filgrama` (sin guión, minúsculas) es solo el nombre
técnico de carpeta/repo/artefactos — nunca aparece de cara al usuario. Dominio: `fil-grama.com`.

## Principio rector: divulgación progresiva

Capturamos y procesamos todo; **mostramos poco por defecto** y revelamos el detalle bajo demanda.
"Abrir la app no debe impactar con datos, botones ni métricas raras."

Tres niveles de profundidad, siempre opcionales hacia abajo:

1. **Vistazo (overview):** lo esencial de un golpe. Pocos números grandes y una tendencia.
2. **Detalle (drill-down):** al hacer clic en una métrica/cliente/red, se abre su desglose.
3. **Crudo/avanzado:** tablas completas, todas las métricas EXTENDED, export — escondido hasta pedirlo.

## Reglas de diseño

- **Jerarquía visual clara:** 3-5 KPIs principales arriba, grandes y legibles; lo demás más abajo o detrás de un clic.
- **Una pantalla, una intención.** Cada vista responde una pregunta ("¿cómo viene este cliente?"), no veinte.
- **Defaults inteligentes:** rango de fechas, red y métricas por defecto razonables; el usuario ajusta solo si quiere.
- **Solo métricas CORE en la vista inicial.** Las EXTENDED viven en "ver más / detalle".
- **Lenguaje humano, no de API.** "Alcance", "Seguidores nuevos" — nunca `ig_reach` ni `page_fan_adds`.
- **Tooltips explicativos en cada métrica (requisito).** Toda métrica visible lleva un ícono de
  info (ⓘ) que al pasar/tocar muestra una explicación corta en lenguaje simple ("Alcance: cuántas
  personas distintas vieron tu contenido"). El usuario no necesita conocer los términos de antemano.
  Las definiciones salen del campo `description` del catálogo `metrics`. Ver glosario abajo. [[05-catalogo-metricas]]
- **Espacio en blanco como aliado.** Menos bordes, menos cajas, menos colores compitiendo. Calma visual.
- **Estados vacíos amables:** "Aún no hay datos para este rango" en vez de una tabla vacía o un error.
- **Consistencia entre redes:** misma estructura visual para IG/FB/TikTok, aunque cada una tenga métricas distintas.
- **Color con propósito:** la paleta Fil-Grama (azules/grises) guía la atención; el color marca lo importante, no decora.
- **Móvil/responsive:** legible en pantallas chicas; los KPIs se apilan, no se amontonan.

## Cómo se conecta con el resto de la spec

- El backend ya **guarda todo** (snapshots append-only + payload crudo) → la UI puede ofrecer
  cualquier nivel de detalle sin volver a pedir a la API. [[02-modelo-de-datos]]
- El **catálogo CORE/EXTENDED** define qué entra en el "vistazo" (CORE) y qué queda en el detalle
  (EXTENDED). [[05-catalogo-metricas]]
- Los **reportes exportables** siguen la misma lógica: limpios y legibles por defecto, con opción de
  anexo detallado. [[04-criterios-aceptacion]] (CU5)

## Pantallas candidatas del v1 (a diseñar)

1. **Home / lista de clientes:** tarjetas por cliente con 1-2 indicadores de salud y sus redes conectadas.
2. **Dashboard de cliente:** KPIs CORE arriba + una tendencia principal; selector de red y rango discretos.
3. **Detalle por red:** métricas de esa red + top posts (miniatura + métricas), drill-down al post.
4. **Detalle de post / story:** preview + sus métricas en el tiempo.
5. **Reporte:** vista limpia exportable a MD/PDF.
6. **Admin:** usuarios, clientes prioritarios, estado del job (fuera del camino del uso diario).

> Pendiente: maquetar (mockups) la Home y el Dashboard de cliente para fijar la jerarquía visual
> antes de codear el frontend.

## Pantallas adicionales (diseño cerrado, jun-2026)

Más allá de las candidatas de arriba, se cerraron en alta fidelidad: **Todas las publicaciones**,
**Login**, **Reconexión de token**, **Dashboard vacío (estado, no pantalla)**, **Comparar cuentas**,
**Administración** y **Mapa de flujo**.

### Multi-cuenta en selectores (transversal)
Un cliente puede tener **varias cuentas de la misma red** ([[02-modelo-de-datos]]). Por eso filtros y
selectores son **por cuenta** (cascada red → cuenta), no solo por red. Las cuentas se distinguen por
chip de red + `handle`/`display_name` siempre que haya >1 de esa red.

### Comparar cuentas — alcance cerrado
- Se comparan **cuentas individuales** (incluso varias de la misma red), **no** agregado por red, **no**
  entre clientes. Hasta **4** cuentas a la vez.
- Métricas comparables: **Alcance, Seguidores, Interacciones, Engagement**. Lo no equivalente entre
  redes (ej. "alcance" de TikTok = reproducciones, no personas) se marca **"—" + "no comparable"** con ⓘ.
- **Totales del rango** (default **30 días**), sin evolución temporal (la tendencia ya vive en el Dashboard).
- Presentación unificada en **toggle Tabla ↔ Barras por métrica** (cada métrica se escala por separado
  para no engañar; misma pantalla, una intención — no se parte en dos pantallas). Mejor valor por
  columna en negrita. Móvil: barras horizontales apiladas por métrica.

### Estado vacío del Dashboard
Cuando el cliente está conectado pero el job aún no capturó: se reemplazan KPIs/gráficos por un bloque
cálido (sin números ni charts vacíos), con las cuentas conectadas como señal de que todo está bien.
**"Generar reporte" va deshabilitado (no oculto)** con tooltip "Disponible cuando haya datos" — ocultarlo
confundiría. Estado distinto "todas las cuentas en error" enlaza a Reconexión. El copy del primer dato
esperado **no debe prometer una hora exacta** (ver [[10-job-diario]]).

### Login
Pre-login sin barra superior ni breadcrumb. Refleja la decisión de auth de [[01-definiciones-alto-nivel]]
(c): "recordar sesión" vía refresh; sin recuperación self-service en v1 (admin resetea); sin SSO. Error
de credenciales en lenguaje humano, sin revelar si el email existe.

## Home — patrón aprobado

- Tarjeta por cliente con **un solo número hero**, redes conectadas (íconos), tendencia + sparkline.
- **Selector de métrica** arriba: el usuario elige qué hero ver en todas las tarjetas
  (Alcance / Seguidores / Interacciones / Engagement). Una métrica a la vez, nunca todas.
- La elección se **recuerda** (localStorage en local; preferencia de usuario en backend a futuro).
- **Rango por defecto: 7 días** para la Home (alimenta tendencia y sparkline). El rango variable y
  más amplio vive en el Dashboard de cliente.
- "Seguidores totales" = suma de seguidores de todas las redes del cliente (valor a hoy); la
  tendencia compara contra el inicio del rango.

## Glosario de métricas (lenguaje simple, para tooltips)

Estas definiciones alimentan los tooltips (campo `description` del catálogo):

- **Seguidores:** cuántas cuentas siguen al cliente. Audiencia potencial; número estable.
- **Alcance:** cuántas **personas distintas** vieron el contenido (cada una cuenta una vez, aunque lo haya visto varias).
- **Impresiones:** cuántas **veces** se mostró el contenido en total (la misma persona puede sumar varias).
- **Interacciones:** suma de likes + comentarios + guardados + compartidos.
- **Engagement (tasa):** qué porcentaje de quienes vieron el contenido reaccionó. Mide qué tan atractivo fue.
- **Visualizaciones (views):** cuántas veces se reprodujo/mostró un video o post.
- **Seguidores nuevos:** cuántas cuentas empezaron a seguir en el período.

## Flujo de diseño (buenas prácticas)

1. **Concepto / wireframe rápido** (acá, en el chat): mockups de baja-media fidelidad con la paleta
   Fil-Grama para acordar estructura, jerarquía y "feeling". Datos de ejemplo, no funcionales.
2. **Maquetado y wireframes formales** (en claude.ai, tras aprobar el plan): refinar pantallas.
3. **Alta fidelidad**: diseño detallado previo al desarrollo del frontend React.

Los mockups del chat son la referencia visual que alimenta los pasos 2 y 3.
