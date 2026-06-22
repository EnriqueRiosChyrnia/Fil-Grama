# Spec — Capa 5: Catálogo de Métricas por Red

> Estado: **CERRADA**. Decisión: **v1 captura y muestra solo el set `CORE`**. Las `EXTENDED`
> quedan planificadas para una **versión futura** (se activan agregando filas al catálogo, sin
> cambios de esquema ni de código).
> Base: [[05-investigacion-metricas-apis]] (research). Modelo: tabla `metrics` en [[02-modelo-de-datos]].
> Convención `metric_key`: prefijo de red (`ig_`, `fb_`, `tt_`) porque el significado difiere entre redes.

## Principios

- **La historia la hacemos nosotros.** Casi ninguna métrica da serie por API; el job la captura a
  diario → en nuestra base toda métrica CORE es una serie temporal. La columna "API nativa" indica
  solo qué entrega la API en cada llamada (foto del día / lifetime), no lo que vos podés graficar.
- **Tier `CORE`** = se captura y se muestra en dashboards/reportes del **v1**. **`EXTENDED`** =
  planificadas para una **versión futura**; se activan agregando filas al catálogo, sin cambios de
  esquema ni de código (el modelo ya es abierto). Quedan documentadas acá para no perder el trabajo.
- **Frecuencia:** la mayoría es captura **diaria**; las **stories** se capturan en ventana **24h**.
- `level`: `ACCOUNT` | `POST`. `unit`: `count` | `seconds` | `ratio`.

---

## Instagram

### Nivel cuenta (`ACCOUNT`)
| metric_key | display_name | api_field (fuente) | unit | API nativa | captura | tier |
|---|---|---|---|---|---|---|
| `ig_followers_count` | Seguidores | `followers_count` (campo del nodo) | count | foto | diaria | CORE |
| `ig_reach` | Alcance | `reach` (insights) | count | serie/total | diaria | CORE |
| `ig_views` | Visualizaciones | `views` (insights) | count | total del día | diaria | CORE |
| `ig_total_interactions` | Interacciones | `total_interactions` | count | total del día | diaria | CORE |
| `ig_accounts_engaged` | Cuentas que interactuaron | `accounts_engaged` | count | total del día | diaria | CORE |
| `ig_profile_links_taps` | Taps en botones de perfil | `profile_links_taps` | count | total del día | diaria | EXTENDED |
| `ig_follows_and_unfollows` | Follows / unfollows | `follows_and_unfollows` | count | total del día | diaria | EXTENDED |
| `ig_follower_demographics` | Demografía de seguidores | `follower_demographics` | count | lifetime | diaria | EXTENDED |

### Nivel post (`POST`) — período API siempre `lifetime` (foto acumulada)
| metric_key | display_name | api_field | unit | aplica a | captura | tier |
|---|---|---|---|---|---|---|
| `ig_post_reach` | Alcance del post | `reach` | count | feed/reels/story | diaria | CORE |
| `ig_post_views` | Reproducciones | `views` | count | feed/reels/story | diaria | CORE |
| `ig_post_likes` | Likes | `likes` | count | feed/reels | diaria | CORE |
| `ig_post_comments` | Comentarios | `comments` | count | feed/reels | diaria | CORE |
| `ig_post_saved` | Guardados | `saved` | count | feed/reels | diaria | CORE |
| `ig_post_shares` | Compartidos | `shares` | count | feed/reels/story | diaria | CORE |
| `ig_post_total_interactions` | Interacciones del post | `total_interactions` | count | feed/reels/story | diaria | CORE |
| `ig_reels_avg_watch_time` | Tiempo medio de visionado (reel) | `ig_reels_avg_watch_time` | seconds | reels | diaria | EXTENDED |
| `ig_reels_skip_rate` | Tasa de skip (reel) | `reels_skip_rate` | ratio | reels | diaria | EXTENDED |

### Nivel story (`POST`, `is_ephemeral=true`) — captura en ventana 24h
| metric_key | display_name | api_field | unit | captura | tier |
|---|---|---|---|---|---|
| `ig_story_reach` | Alcance de la story | `reach` | count | 24h | CORE |
| `ig_story_replies` | Respuestas | `replies` | count | 24h | CORE |
| `ig_story_navigation` | Navegación (fwd/back/exit) | `navigation` | count | 24h | EXTENDED |
| `ig_story_link_clicks` | Clicks en link | `link_clicks` | count | 24h | EXTENDED |

> `impressions` (cuenta y post) **deprecada** → no se incluye; se usa `views`.

---

## Facebook (Pages)

### Nivel página (`ACCOUNT`)
| metric_key | display_name | api_field | unit | período API | captura | tier |
|---|---|---|---|---|---|---|
| `fb_page_views_total` | Vistas de la página | `page_views_total` | count | day/week/28 | diaria | CORE |
| `fb_page_post_engagements` | Interacciones con posts | `page_post_engagements` | count | day/week/28 | diaria | CORE |
| `fb_page_fan_adds` | Nuevos seguidores | `page_fan_adds` | count | day | diaria | CORE |
| `fb_page_fan_removes` | Bajas de seguidores | `page_fan_removes` | count | day | diaria | EXTENDED |
| `fb_page_views` | Vistas (reemplazo de impressions) | `views` / `page_media_view` | count | day | diaria | CORE |

### Nivel post (`POST`)
| metric_key | display_name | api_field | unit | captura | tier |
|---|---|---|---|---|---|
| `fb_post_engaged_users` | Usuarios que interactuaron | `post_engaged_users` | count | diaria | CORE |
| `fb_post_clicks` | Clicks | `post_clicks` | count | diaria | CORE |
| `fb_post_reactions_total` | Reacciones (por tipo) | `post_reactions_by_type_total` | count | diaria | CORE |
| `fb_post_video_views` | Reproducciones de video | `post_video_views` | count | diaria | CORE |
| `fb_post_views` | Vistas del post | `views` (reemplazo de impressions) | count | diaria | CORE |

> `page_fans` y `page_impressions` **deprecadas** (2025-2026) → no se incluyen; se migra a `views`.
> Likes/comments/shares "básicos" del post también se leen como campos del objeto (`*.summary(true)`).

---

## TikTok

### Nivel cuenta (`ACCOUNT`) — `GET /v2/user/info/`
| metric_key | display_name | api_field | unit | scope | captura | tier |
|---|---|---|---|---|---|---|
| `tt_follower_count` | Seguidores | `follower_count` | count | `user.info.stats` | diaria | CORE |
| `tt_likes_count` | Likes totales recibidos | `likes_count` | count | `user.info.stats` | diaria | CORE |
| `tt_video_count` | Cantidad de videos | `video_count` | count | `user.info.stats` | diaria | CORE |
| `tt_following_count` | Seguidos | `following_count` | count | `user.info.stats` | diaria | EXTENDED |

### Nivel video (`POST`) — `/v2/video/list/` y `/v2/video/query/`
| metric_key | display_name | api_field | unit | scope | captura | tier |
|---|---|---|---|---|---|---|
| `tt_view_count` | Reproducciones | `view_count` | count | `video.list` | diaria | CORE |
| `tt_like_count` | Likes | `like_count` | count | `video.list` | diaria | CORE |
| `tt_comment_count` | Comentarios | `comment_count` | count | `video.list` | diaria | CORE |
| `tt_share_count` | Compartidos | `share_count` | count | `video.list` | diaria | CORE |

> **Límite TikTok:** reach, watch-time y fuente de tráfico **no** salen del Display API → quedan
> fuera del v1. Si un cliente tiene cuenta **Business**, se podrían sumar vía Business API
> (EXTENDED, evaluación futura). Métricas marcadas según `account_type`/`capabilities`. [[02-modelo-de-datos]]

---

## Métricas derivadas (calculadas, no se piden a la API)

Se computan desde los snapshots CORE; no son filas capturadas sino cálculo en consulta/reporte:
| metric_key | display_name | fórmula | tier |
|---|---|---|---|
| `*_engagement_rate` | Tasa de engagement | interacciones / alcance (o seguidores) | CORE |
| `*_follower_growth` | Crecimiento de seguidores | followers(t) − followers(t-1) | CORE |
| `*_avg_interactions_per_post` | Interacciones promedio por post | interacciones / nº posts del período | EXTENDED |

---

## Notas de implementación

- Seed inicial de la tabla `metrics` con estas filas (key, platform, level, unit, tier).
- Marcar en el catálogo el estado de cada métrica (`ACTIVE` / `DEPRECATED` / `MIGRATING`) para reaccionar
  a los cambios de Meta sin tocar código. Guardar siempre el payload crudo (ya decidido) por las dudas.
- El dashboard/reporte se arma leyendo el catálogo filtrado por `platform` + `tier` + `capabilities`
  de la cuenta → nunca asume paridad entre redes.
- **Validar nombres en sandbox/development** antes de fijar el seed: la doc es de jun-2026 y estas APIs cambian.
