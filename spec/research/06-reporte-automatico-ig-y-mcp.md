# Investigación — Reporte automático de Instagram (caso Molino Don Alexis) + integración con Claude

> Fecha: 30 de junio de 2026. Complementa [research/05](05-investigacion-metricas-apis.md) (tablas de
> métricas por red) y las capas [02 modelo de datos](../02-modelo-de-datos.md),
> [05 catálogo](../05-catalogo-metricas.md), [08 IA/MCP](../08-ia-reportes.md), [10 job](../10-job-diario.md).
> Origen: se construyó a mano un reporte mensual de IG para el cliente real **Molino Don Alexis**
> (@molinodonalexis) a partir de capturas. Este documento analiza si Fil-Grama puede generar ese mismo
> reporte de forma **automática**, qué falta, y cómo conectarlo con Claude.
> Certezas etiquetadas: **[seguro]** (doc oficial), **[probable]** (inferencia fundada, validar en sandbox),
> **[no probable]** (dudoso / fuera del API).

---

## 0. TL;DR

1. **~85% del reporte es automatizable hoy** con la arquitectura ya decidida (snapshots diarios + payload
   crudo + catálogo abierto). El insight de arquitectura se confirma una vez más.
2. **El cuello de botella NO son los datos: es el App Review de Meta** (Advanced Access + Business
   Verification). Sin eso no se onboardean cuentas de terceros. Es el bloqueo #1 real.
3. **Faltan 4 cosas en el catálogo/modelo** para que el reporte salga completo: demografía estructurada,
   split seguidores/no-seguidores (`follow_type`), reposts y watch-time de reels en CORE, y desglose de
   `profile_links_taps` por destino.
4. **Dos splits del reporte son frágiles vía API** (ver §2): el % de *interacciones* por seguidor/no-seguidor
   y el desglose de visualizaciones por tipo de contenido. Validar en sandbox antes de prometerlos al cliente.
5. **La integración con Claude ya está bien encaminada** (spec/08, MCP read-only bajo Max). El reporte que
   hicimos a mano es, de hecho, **el spec visual del PDF automático** (mismas secciones).

---

## 1. Qué métricas del reporte se obtienen por la API de Meta

Mapeo 1:1 de cada dato del reporte de Molino Don Alexis → campo de API → estado en el catálogo Fil-Grama
(`spec/05` + `V3__seed_metrics.sql`).

### Nivel cuenta / período

| Dato en el reporte | Campo API (IG) | metric_type | ¿Disponible? | En catálogo Fil-Grama |
|---|---|---|---|---|
| Visualizaciones (5.100) | `views` | total_value | **[seguro]** | `ig_views` CORE ✓ |
| Cuentas alcanzadas (1.784) | `reach` | total_value / time_series | **[seguro]** (única con serie) | `ig_reach` CORE ✓ |
| Interacciones (480) | `total_interactions` | total_value | **[seguro]** | `ig_total_interactions` CORE ✓ |
| Me gusta / comentarios / compartidos / guardados | `likes`,`comments`,`shares`,`saves` | total_value | **[seguro]** | derivable; no como key propia |
| Reposts (12) | `reposts` | total_value | **[probable]** (validar) | ❌ no en catálogo |
| Seguidores nuevos / bajas (138 / 2) | `follows_and_unfollows` | total_value | **[seguro]** (req. ≥100 followers; la cuenta tiene 137 ✓) | `ig_follows_and_unfollows` EXTENDED |
| Total seguidores (137) | `followers_count` (campo del nodo, NO insight) | — | **[seguro]** | `ig_followers_count` CORE ✓ |
| Visitas al perfil (363) | `profile_views` | total_value | **[probable]** (ver §2; deprecado como serie en ene-2025, queda como total) | ❌ |
| Clics enlace WhatsApp (8) | `profile_links_taps` (breakdown `contact_button_type`) | total_value | **[probable]** (el total sí; la atribución exacta a WhatsApp, ver §2) | `ig_profile_links_taps` EXTENDED (sin breakdown) |
| Clics ubicación / Maps (2) | `profile_links_taps` breakdown `direction` (o `get_directions_clicks`) | total_value | **[probable]** | ❌ desglose |

### Demografía del público

| Dato en el reporte | Campo API | ¿Disponible? | En catálogo |
|---|---|---|---|
| Ciudades / Países / Edad / Sexo | `follower_demographics` (lifetime, breakdown `city`/`country`/`age`/`gender`) | **[seguro]** (req. ≥100 followers ✓) | `ig_follower_demographics` EXTENDED, **sin modelo de datos** |

> Matiz **[probable]**: el bloque "Público" del reporte se basó en **seguidores** → mapea a
> `follower_demographics`. Si en el futuro quisiéramos "público alcanzado", es `reached_audience_demographics`
> / `engaged_audience_demographics` (limitado a top 45 segmentos y a una ventana corta, no lifetime).

### Por publicación / por historia

| Dato en el reporte | Campo API (media) | ¿Disponible? | En catálogo |
|---|---|---|---|
| Vistas del reel/post (672, 193…) | `views` | **[seguro]** | `ig_post_views` CORE ✓ |
| Alcance del post (221, 76…) | `reach` | **[seguro]** | `ig_post_reach` CORE ✓ |
| Likes / comentarios / guardados / compartidos del post | `likes`/`comments`/`saved`/`shares` | **[seguro]** | CORE ✓ |
| Tiempo promedio de reproducción (13 s) | `ig_reels_avg_watch_time` (ms→s) | **[seguro]** (reels NO afectados por deprecación ene-2025) | `ig_reels_avg_watch_time` EXTENDED |
| Visitas al perfil desde el post (7, 1…) | `profile_visits` (media) | **[probable]** | ❌ |
| Historias: vistas / interacciones / actividad perfil | story `views`/`reach`/`replies`/`navigation`/`profile_activity` | **[seguro]** *pero solo 24 h* (webhook `story_insights`) | `ig_story_*` CORE/EXTENDED |

**Conclusión §1:** todos los KPIs de portada y de contenido del reporte son obtenibles. La mayoría ya
está en el catálogo CORE; el resto existe en la API y solo hay que activarlo (es catálogo abierto, sin
reescribir código).

---

## 2. Qué NO está disponible o tiene limitaciones

| Caso | Estado | Detalle |
|---|---|---|
| **Split seguidores vs no-seguidores de las INTERACCIONES** (86,0% / 14,0%) | **[no probable]** | El breakdown `follow_type` existe para `reach`/`views`, no hay evidencia de que aplique a `total_interactions`. La app de IG lo muestra, pero el API público no lo expone claramente. *Validar en sandbox; si no está, se reporta solo el split de visualizaciones.* |
| **Split seguidores vs no-seguidores de las VISUALIZACIONES** (70,9% / 29,1%) | **[probable]** | `views`/`reach` admiten breakdown `follow_type`. Falta confirmar que conviva con el rango del período. |
| **Visualizaciones por tipo de contenido** (Reels 58% / Historias 30% / Pub 12%) | **[probable]** | Dos caminos: (a) breakdown `media_product_type` sobre `views`; (b) agregar nosotros por `posts.post_type`. La opción (b) es segura y no depende del breakdown. |
| **`profile_views` a nivel cuenta** (363) | **[probable]** | Deprecado como *serie temporal* en ene-2025 (v21); como `total_value` del rango la situación es ambigua según versión. Plan B: sumar `profile_visits` de los media del período. |
| **Atribución del tap a WhatsApp vs Maps vs web** | **[no probable]** (exacto) | `profile_links_taps` da el total y un breakdown por tipo de botón, pero la separación fina "WhatsApp" depende de cómo esté configurado el perfil. Para tracking confiable de WhatsApp → links con UTM / acortador propio (fuera de la API de Meta). |
| **Series históricas arbitrarias** | **[seguro]** (limitación) | Solo `reach` da serie; el resto es total del rango y media es `lifetime`. → el job de snapshots es la única fuente de historia (ya decidido). |
| **Datos con delay** | **[seguro]** | Hasta 48 h de retraso en Meta → el upsert del día corrige valores. |
| **Stories tras 24 h** | **[seguro]** (limitación) | Si no se capturan dentro de 24 h (webhook), se pierden. |
| **App Review / Business Verification** | **[seguro]** (BLOQUEO) | Sin Advanced Access no hay onboarding de terceros. Es el verdadero cuello de botella, no la data. |

---

## 3. Qué debería almacenar/procesar Fil-Grama

Sobre el modelo ya existente (`account_metric_snapshots`, `post_metric_snapshots`, `posts`,
`raw_api_payloads` — ver `spec/02`), **agregar**:

1. **Tabla de demografía** (la pieza más grande que falta):
   ```
   audience_demographics(
     client_id, account_id,
     scope,            -- 'follower' | 'reached' | 'engaged'
     breakdown_type,   -- 'age' | 'gender' | 'city' | 'country'
     breakdown_value,  -- '25-34' | 'F' | 'Encarnación' | 'PY'
     value,            -- conteo o %
     captured_at, capture_date
   )  -- snapshot, append-only, UNIQUE por (account, scope, type, value, capture_date)
   ```
2. **Métricas de split por `follow_type`** como keys propias: `ig_views_followers`,
   `ig_views_non_followers` (y `ig_reach_*`). Capturarlas con el breakdown en el mismo job.
3. **Pasar a CORE**: `ig_post_reposts`, `ig_reels_avg_watch_time`. Agregar `ig_post_profile_visits`.
4. **`profile_links_taps` con breakdown por destino** (guardar el breakdown crudo y derivar
   `ig_taps_whatsapp` / `ig_taps_direction` / `ig_taps_website` cuando estén).
5. **Derivar por tipo de contenido** agregando `post_metric_snapshots` por `posts.post_type`
   (camino seguro, no depende del breakdown del API).
6. **Seguir guardando el payload crudo** (ya se hace) — es el seguro contra deprecaciones.

Todo esto encaja en el patrón actual (append-only + catálogo abierto): son filas nuevas de catálogo
+ una tabla, **sin reescribir el pipeline**.

---

## 4. ¿Las specs actuales contemplan todo lo necesario?

**Arquitectura: sí.** snapshots append-only, payload crudo, catálogo abierto con `metric_key` por red,
job diario, multi-tenant, OAuth, y hasta la capa de IA por MCP (`spec/08`) ya están especificados.

**Cobertura de datos del reporte: parcial.** Resumen:

| Pieza del reporte | ¿Spec la contempla? |
|---|---|
| KPIs (views, reach, interacciones, seguidores) | ✓ CORE |
| Top posts + miniaturas + permalink | ✓ (`ReportData`, `spec/03`) |
| Desglose por tipo de contenido | ✓ vía `posts.post_type` (derivable) |
| Engagement rate / follower growth / deltas | ✓ (con baseline nullable = primer mes) |
| **Demografía (ciudades/países/edad/sexo)** | ⚠️ EXTENDED, **sin tabla** → falta modelar |
| **Split seguidores/no-seguidores** | ❌ no está en catálogo |
| **Reposts / watch-time reels** | ⚠️ existen pero EXTENDED, no CORE |
| **Taps por destino (WhatsApp/Maps)** | ⚠️ solo el tap genérico |
| Narrativa/análisis con IA | ⚠️ PLANIFICADO v2 (`spec/08`), no implementado |
| Render del PDF igual al que hicimos | ⚠️ `PdfRenderer` existe; el diseño nuevo (este reporte) es la referencia visual a alcanzar |

> Nota SDD: conviene **abrir `spec/05` y `spec/02`** para incorporar lo del §3 antes de codear, y dejar
> el PDF de Molino Don Alexis como **anexo de diseño** del reporte (es el objetivo visual).

---

## 5. Funcionalidades a agregar a la app web

Priorizadas:

**v1.1 (datos que faltan):**
- Tabla + captura de **demografía** (follower_demographics) y render del bloque "Público".
- Captura de **split `follow_type`** y render de los donuts seguidores/no-seguidores (con caveat si el de
  interacciones no está disponible).
- **Reposts + watch-time de reels** a CORE; columna en top posts.
- **Breakdown de taps** (WhatsApp/Maps) si el API lo permite; si no, integrar acortador/UTM propio.

**v1.x (UX del reporte):**
- Reporte como **vista en pantalla** + botón Exportar (no solo "genera archivo") — ya detectado en Fase 2.
- Manejo de **primer mes / sin baseline** = "sin comparación" (no deltas falsos) — ya detectado.
- Bloque "**mejor hora/día para publicar**" (datos ya se guardan: `published_at` + timezone).
- Calendario de **fechas/efemérides** sugeridas por mercado (PY) — opcional.

**v2 (IA):**
- **Narrativa automática** con Claude vía MCP (resumen + recomendaciones), guardada en `Report.narrative_md`.
- Etiqueta "generado con asistencia de IA".

---

## 6. Integración con Claude (Desktop / terminal / MCP / conector)

La spec ya eligió **MCP read-only bajo Claude Max** (`spec/08`). Recomendación y detalle:

**Opción recomendada — Servidor MCP propio de Fil-Grama** (envuelve el backend; no expone la DB directo):
- Tools de **lectura** (autenticadas con un token de servicio scoped a los clientes del usuario):
  - `list_clients()`
  - `get_client_report_data(client_id, from, to, account_ids?)` → el mismo `ReportData` del endpoint
    `:preview` (SSOT: lo que ve la IA = lo que exporta el PDF).
  - `get_metric_series(account_id, metric_key, from, to)`
  - `get_audience_demographics(account_id)` (cuando exista la tabla del §3)
  - `compare_periods(client_id, period_a, period_b)`
  - `get_posting_performance(client_id, by=hour|weekday)`
  - `save_report_narrative(client_id, period, markdown, model)` (única de escritura, opcional)
- **Flujo:** desde **Claude Desktop o Cowork** (bajo Max, sin costo extra de API): "generá el reporte de
  Molino Don Alexis de julio" → Claude llama `get_client_report_data` → redacta la narrativa (es. 150-250
  palabras, tono cálido/profesional, solo con números provistos) → opcional `save_report_narrative`.
- **Terminal:** mismo MCP server vía Claude Code (`claude mcp add`), útil para vos como dev.

**Opción conector (Anthropic Connector / remote MCP):** el mismo MCP server expuesto como conector
remoto para que empleados **sin Claude Max** lo usen desde claude.ai. Requiere hostear el MCP (en el mismo
Vultr) con auth por usuario.

**Opción B (in-app, descartada salvo necesidad):** botón "Generar análisis IA" → backend llama a la API de
Anthropic con `ReportData`. Esto **sí cuesta** (tokens) y conviene solo para empleados sin Claude.

> Seguridad: el MCP nunca recibe tokens de clientes ni toca tablas crudas; solo consume endpoints del
> backend con un service token de alcance limitado. Toda la lógica de tenancy (client_id sobre account_id)
> ya vive en el backend.

---

## 7. Arquitectura / flujo recomendado

```
                         ┌─────────────────────────────────────────┐
   Meta Graph API ──────▶│  JOB DIARIO (Spring @Scheduled)          │
   (IG/FB)               │  reach·views·interacciones·followers·    │
   TikTok Display API────▶│  demografía·follow_type·posts·stories    │
                         │  → guarda PAYLOAD CRUDO + deriva SNAPSHOTS│
                         └───────────────┬─────────────────────────┘
                                         ▼
                         ┌─────────────────────────────────────────┐
                         │  POSTGRES (append-only)                  │
                         │  account/post snapshots · posts ·        │
                         │  audience_demographics · raw_payloads    │
                         └───────────────┬─────────────────────────┘
                                         ▼
                         ┌─────────────────────────────────────────┐
                         │  BACKEND  — ReportDataAssembler          │
                         │  POST /clients/{id}/reports:preview      │
                         │  = SSOT (preview == export)              │
                         └──────┬───────────────────────┬──────────┘
                                ▼                        ▼
                    Web (React): vista          MCP server (read-only)
                    + Exportar PDF/MD                    │
                                                         ▼
                                            Claude Desktop / Cowork / terminal
                                            redacta narrativa → save_report_narrative
```

**Principios:**
- **Una sola fuente de verdad de datos del reporte:** el endpoint `:preview` (`ReportData`). La web, el PDF
  y la IA consumen lo mismo.
- **El job de snapshots es el corazón** (las APIs no dan historia). Idempotente por día.
- **Catálogo abierto + payload crudo** = se agregan métricas/redes sin reescribir, y se sobrevive a las
  deprecaciones de Meta (que son constantes; ver `research/05`).
- **La IA es una capa opcional encima**, no un acoplamiento: la app funciona sin Claude; Claude potencia
  la narrativa.

---

## 8. Caveats del asesor (no validar como hechos sin sandbox)

- Los **dos splits** seguidor/no-seguidor (sobre todo el de interacciones) y `profile_views` a nivel cuenta
  hay que **probarlos en development/sandbox** antes de prometerlos en el producto. Si el API no los da,
  el reporte automático mostrará el dato a nivel que sí exista (p. ej. split solo de visualizaciones).
- **No prometer paridad** entre redes: TikTok da bastante menos vía Display API (ver `research/05`).
- El **bloqueo real es operativo** (App Review + Business Verification de Meta), no técnico. Conviene
  iniciarlo en paralelo al desarrollo, no después.
- Estas APIs **cambian seguido**: este documento refleja jun-2026; re-verificar contra el changelog antes
  de fijar nuevos `metric_key`.

## 9. Validación sandbox (FG-T1, 30-jun-2026)

> **Estado del spike: PENDIENTE.** La captura de datos v1.1 (track `tracks/FG-T1-captura-datos-v11.md`)
> se implementó **sin** una cuenta de IG profesional conectada en Dev Mode, así que **ningún campo ⚠ se
> validó contra la API real**. Se siguió el camino de respaldo del track: implementar B–D **gateado por
> la detección de capacidades / catálogo**, capturando **solo si la respuesta de la API trae el campo**.
> Cuando haya una cuenta conectada, correr el job y completar la tabla de abajo con "existe / no existe".

**Cómo quedó implementado (degradación elegante):**

- **Tabla** `audience_demographics` (migración `V9`) y **seed** v1.1 (`V10`) aplicados. Las keys ⚠ quedan
  `state='ACTIVE'` pero **pendientes de validación** (anotado en la `description` de cada fila): el deriver
  solo persiste la fila si la API devolvió el campo, así que ACTIVE = "capturá si está", no "asumí que está".
- Las llamadas a los datos nuevos (demografía, splits `follow_type`, `profile_views`, taps por destino,
  `reposts`/`profile_visits`/watch-time por media) se hacen en **requests Graph separadas y best-effort**
  (`MetaInsightsProvider.fetchAccountExtras` / `fetchPostExtras` / `fetchAudienceDemographics`): si Meta
  rechaza un nombre/breakdown, esa sub-llamada degrada a vacío y **no rompe la captura CORE** existente.
- La demografía se **gatea por catálogo**: solo se pide si `ig_follower_demographics` está CORE+ACTIVE.
- El split `follow_type` sobre **`total_interactions`** (⚠ [no probable]) **no se implementó** (sin evidencia
  en el API): no hay key ni captura para él. El reporte mostrará, como máximo, el split de *visualizaciones*.

**Tabla a completar en el spike (qué existe / qué no):**

| Campo / breakdown | Esperado (doc jun-2026) | Resultado sandbox | Acción si NO existe |
|---|---|---|---|
| `follower_demographics` breakdown age/gender/city/country | [seguro] (≥100 followers) | _pendiente_ | bajar `ig_follower_demographics` a EXTENDED |
| `views` breakdown `follow_type` | [probable] | _pendiente_ | bajar `ig_views_*` a EXTENDED |
| `reach` breakdown `follow_type` | [probable] | _pendiente_ | ya es EXTENDED; dejar fuera |
| `profile_views` (total_value del rango) | [probable] | _pendiente_ | plan B: sumar `profile_visits` de los media |
| `profile_links_taps` breakdown `contact_button_type` | [probable] | _pendiente_ | usar acortador/UTM propio para WhatsApp |
| media `reposts` | [probable] | _pendiente_ | quitar `ig_post_reposts` del set pedido |
| media `profile_visits` | [probable] | _pendiente_ | dejar fuera |
| media `ig_reels_avg_watch_time` (ms→s) | [seguro] (reels) | _pendiente_ | — |
| split `follow_type` de `total_interactions` | [no probable] | **no implementado** | — |

> Marcar lo confirmado-inexistente con `state` adecuado en una migración de seguimiento (el `CHECK` actual
> de `metrics.state` admite `ACTIVE`/`DEPRECATED`/`MIGRATING`; si se quisiera un `UNAVAILABLE` explícito,
> ampliar el `CHECK` y el enum `MetricState`). Hoy basta con mover el `tier` a EXTENDED para sacarlo de la
> captura sin tocar código.

## Fuentes
- [Instagram Account Insights — Meta for Developers](https://developers.facebook.com/docs/instagram-platform/api-reference/instagram-user/insights/)
- [Instagram Media Insights — Meta for Developers](https://developers.facebook.com/docs/instagram-platform/reference/instagram-media/insights/)
- [Instagram Insights metrics deprecation (abr-2025) — Emplifi](https://docs.emplifi.io/platform/latest/home/instagram-media-and-profile-insights-metrics-depre)
- [Instagram Reels analytics 2026 — Sociality.io](https://sociality.io/blog/instagram-reels-analytics/)
- [Audience Demographics API — Phyllo](https://www.getphyllo.com/post/instagram-audience-demographics-for-influencer-marketing-platforms)
- Complementa: [research/05 — Métricas e Insights de las APIs](05-investigacion-metricas-apis.md)
