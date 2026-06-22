# Investigación — Métricas e Insights de las APIs (IG / FB / TikTok)

> Fecha: 21 de junio de 2026. Fuentes: documentación oficial (developers.facebook.com,
> developers.tiktok.com) + notas de deprecación. Alcance: contenido **orgánico**, cuentas de
> clientes terceros gestionadas por OAuth con Advanced Access.
> Uso: insumo para la próxima capa de spec (catálogo `metrics` por red).

## TL;DR — 5 conclusiones que afectan el diseño

1. **El insight de arquitectura se confirma.** Las APIs casi no dan series históricas. En IG, a
   nivel cuenta **solo `reach` admite `time_series`**; el resto es `total_value` (valor del día/rango).
   A nivel media/post, el período es **siempre `lifetime`** (foto puntual). → El JOB diario que
   guarda snapshots no es opcional: es la única forma de tener historia.
2. **`followers_count` no es un "insight"**, es un campo del nodo de la cuenta. Hay que leerlo y
   snapshotearlo vos mismo cada día (igual TikTok: `follower_count` vía user info).
3. **Meta está deprecando métricas en oleadas (2024-2026).** `impressions` → reemplazado por
   `views`; `page_fans` eliminado. Algunas deprecaciones caen el **15 de junio de 2026** (ya en
   vigor a esta fecha). El diseño abierto por catálogo nos protege.
4. **TikTok da menos que Meta para orgánico vía API estándar.** El Display API entrega
   conteos-foto por video (`view_count`, `like_count`, `comment_count`, `share_count`) y de cuenta
   (`follower_count`, `likes_count`, `video_count`), pero **no** reach, watch-time ni fuente de
   tráfico a nivel API. Eso vive en la app o en el Business/Marketing API (cuenta business + acceso aparte).
5. **Onboarding real exige revisión.** Meta: App Review + Advanced Access + **Business
   Verification**. TikTok: **auditoría** del app client para sacar el sandbox y operar en producción.

---

## Instagram

API: *Instagram Graph API* / *Instagram API with Instagram Login*. Host `graph.facebook.com`
(Facebook Login) o `graph.instagram.com` (Instagram Login). Versión vigente citada: **v25.0**.

### Nivel CUENTA — `GET /{ig-user-id}/insights`

Parámetros clave: `metric`, `period`, `metric_type` (`time_series` | `total_value`), `breakdown`,
`since`/`until`, `timeframe` (demográficas). Datos con hasta **48 h** de delay.

| Métrica (campo API) | Nivel | metric_type | ¿Histórico/serie? | Scope | Notas |
|---|---|---|---|---|---|
| `reach` | cuenta | `total_value`, **`time_series`** | **Sí** (serie por día) | `instagram_manage_insights` (FB login) / `instagram_business_manage_insights` (IG login) | Único de cuenta con serie real. Breakdown `media_product_type`, `follow_type`. Estimada. |
| `views` | cuenta | `total_value` | No (total del rango) | idem | Reemplazo de impressions. Breakdown `follower_type`, `media_product_type`. |
| `impressions` | cuenta | `total_value`, `time_series` | **Deprecada** | idem | Deprecada v22.0+ y todas las versiones desde **21-abr-2025**. No usar. |
| `accounts_engaged` | cuenta | `total_value` | No | idem | Cuentas que interactuaron. Estimada. |
| `total_interactions` | cuenta | `total_value` | No | idem | Suma de interacciones. |
| `likes` / `comments` / `saves` / `shares` / `replies` / `reposts` | cuenta | `total_value` | No | idem | Agregados del día/rango. |
| `profile_links_taps` | cuenta | `total_value` | No | idem | Taps en botones de contacto. |
| `follows_and_unfollows` | cuenta | `total_value` | No | idem | Requiere ≥100 followers. |
| `follower_demographics` / `engaged_audience_demographics` | cuenta | `total_value` (`lifetime`) | No | idem | Por `age`/`city`/`country`/`gender`. Requiere ≥100. |
| `online_followers` | cuenta | — | últimos 30 días | idem | Solo 30 días disponibles. |

**`followers_count` (NO es insight):** se obtiene como campo del nodo —
`GET /{ig-user-id}?fields=followers_count,media_count`. Hay que snapshotearlo a diario.

### Nivel MEDIA/POST — `GET /{ig-media-id}/insights`

Período **siempre `lifetime`** (foto acumulada del post; no hay serie por API). Datos guardados
hasta **2 años**. Stories: métricas solo **24 h** (usar webhook `story_insights`).

| Métrica (campo API) | Tipo de media | Scope | Notas |
|---|---|---|---|
| `reach` | FEED, REELS, STORY | `instagram_manage_insights` / `instagram_business_manage_insights` | Cuentas únicas. Estimada. |
| `views` | FEED, REELS, STORY | idem | Reproducciones/visualizaciones. |
| `likes` / `comments` / `saved` / `shares` | FEED, REELS (saved no en story) | idem | Conteos orgánicos. |
| `total_interactions` | FEED, REELS, STORY | idem | Likes+saves+comments+shares netos. |
| `reposts` | FEED, REELS, STORY | idem | |
| `ig_reels_avg_watch_time` | REELS | idem | Tiempo medio de reproducción. |
| `ig_reels_video_view_total_time` | REELS | idem | Tiempo total reproducido. |
| `reels_skip_rate` | REELS | idem | % skip primeros 3s. Estimada. |
| `profile_visits` / `profile_activity` | FEED, STORY | idem | Acciones de perfil (breakdown `action_type`). |
| `follows` | FEED, STORY | idem | Seguidores ganados desde el post. |
| `navigation` / `link_clicks` / `replies` | STORY | idem | Navegación, clicks de link, respuestas. |
| `impressions` | FEED, STORY | idem | **Deprecada** para media creada después del **2-jul-2024**. |
| `total_likes` / `total_comments` / `total_views` | FEED, REELS | idem | Agregados con boosted/ads. **Solo Facebook Login.** |

Cuenta **Business vs Creator**: ambas son "professional" y comparten este set de insights; las
diferencias relevantes son por tipo de media (REELS/STORY/FEED), no por tipo de cuenta.

---

## Facebook (Pages)

API: *Facebook Graph API — Page Insights*. `GET /{page-id}/insights` y `GET /{post-id}/insights`.
Período: `day` | `week` | `days_28` | `lifetime`. **Retención 2 años; máximo 90 días por consulta**
(`since`/`until`). Solo páginas con ≥100 likes. Actualización ~cada 24 h.

> **Deprecaciones en curso (críticas):** `impressions` (página) → reemplazada por `views`, y
> `page_fans`, deprecadas desde **15-nov-2025**. Más métricas de Page Insights deprecadas para
> **15-jun-2026** en todas las versiones (ya en vigor). Verificar siempre contra el changelog antes
> de fijar el catálogo. Reemplazos anunciados tipo `page_media_view` / `post_media_view`.

### Nivel PÁGINA

| Métrica (campo API) | Período | Scope | Estado |
|---|---|---|---|
| `page_views_total` | day/week/days_28 | `read_insights` + `pages_read_engagement` + `pages_show_list` | Vigente |
| `page_post_engagements` | day/week/days_28 | idem | Vigente |
| `page_fan_adds` / `page_fan_removes` | day | idem | Vigente |
| `page_fans` | lifetime | idem | **Deprecada** (15-nov-2025) |
| `page_impressions` / `page_impressions_unique` | day/week/days_28 | idem | **Deprecación** → migrar a `views` |
| `page_views` / `views` (nuevas) | day | idem | Reemplazo de impressions |

### Nivel POST — `GET /{post-id}/insights`

| Métrica (campo API) | Scope | Notas |
|---|---|---|
| `post_impressions` / `post_impressions_unique` | `read_insights` + `pages_read_engagement` | En migración a `views` |
| `post_engaged_users` | idem | Usuarios que interactuaron |
| `post_clicks` | idem | Clicks totales |
| `post_reactions_by_type_total` | idem | Reacciones por tipo (like, love, etc.) |
| `post_video_views` (video) | idem | Reproducciones de video |

> Nota: las reacciones/comentarios/shares "básicos" del post también se leen como campos/edges del
> objeto post (`reactions.summary(true)`, `comments.summary(true)`, `shares`) además de Insights.

---

## TikTok

API orgánica estándar para apps de terceros: **Display API** (lado lectura). Host
`open.tiktokapis.com`. Login Kit (OAuth). Rate limit 600 req/min por endpoint. Scopes oficiales:
`user.info.basic`, `user.info.profile`, `user.info.stats`, `video.list`.

### Nivel CUENTA — `GET /v2/user/info/`

| Campo API | Scope | Notas |
|---|---|---|
| `follower_count` | `user.info.stats` | Snapshotear a diario (es foto, sin serie) |
| `following_count` | `user.info.stats` | |
| `likes_count` | `user.info.stats` | Total de likes recibidos |
| `video_count` | `user.info.stats` | |
| `display_name`, `avatar_url`, `bio_description`, `is_verified` | `user.info.profile` | Perfil |
| `open_id`, `union_id`, `display_name`, `avatar_url` | `user.info.basic` | Básico (desde 29-feb-2024 el basic ya NO da stats) |

### Nivel VIDEO/POST — `POST /v2/video/list/` y `POST /v2/video/query/`

Devuelven el *Video Object* con conteos-foto (no series). `video.list` pagina por `cursor`
(timestamp), máx 20 por página.

| Campo API | Scope | Notas |
|---|---|---|
| `view_count` | `video.list` | Reproducciones (foto acumulada) |
| `like_count` | `video.list` | |
| `comment_count` | `video.list` | |
| `share_count` | `video.list` | |
| `id`, `create_time`, `video_description`, `title`, `duration`, `cover_image_url`, `share_url`, `embed_link` | `video.list` | Metadatos |

> **Límite importante:** el Display API **no** expone reach, watch-time, completion rate, ni fuente
> de tráfico (For You / followers / hashtag) a nivel API para apps de terceros. Esas métricas
> orgánicas más ricas viven en la app de TikTok o requieren cuenta **Business** + el **TikTok
> Business/Marketing API** (acceso y aprobación aparte). El Research API tiene métricas amplias pero
> está restringido a investigación no comercial → **no aplica** a una agencia. *(Algunas fuentes
> de terceros afirman que el Display API da "reach/watch time"; el Video Object oficial no los
> incluye — tratar como no disponible salvo verificación directa en sandbox.)*

---

## Permisos, App Review y modos de acceso

| | Meta (IG + FB) | TikTok |
|---|---|---|
| **Scopes insights** | `instagram_manage_insights` / `instagram_business_manage_insights`, `pages_read_engagement`, `pages_show_list`, `read_insights`, `business_management` | `user.info.stats`, `user.info.profile`, `video.list` |
| **Onboardear cuentas de terceros** | **Advanced Access** (obligatorio) | App client **auditado** (producción) |
| **Requisito extra** | **Business Verification** + App Review (screencast del flujo) | Auditoría con video demo end-to-end (hasta 5, 50 MB c/u) |
| **Modo desarrollo** | Standard Access: solo cuentas con rol en la app | Sandbox: hasta 5, 10 cuentas test c/u; contenido forzado a privado |
| **Costo** | Gratis (gestión orgánica) | Display API gratis; auditoría es el "costo" en tiempo |

Esto confirma lo ya definido en la spec: el costo real del onboarding es el **tiempo de revisión**,
no las APIs.

---

## Recomendaciones para el catálogo `metrics` (v1)

- **Snapshot diario obligatorio** de: IG `followers_count`/`reach`/`views`/`total_interactions`;
  FB `page_views_total`/`page_post_engagements`/fans-adds; TikTok `follower_count`/`likes_count`/
  `video_count`. Todo lo demás es valor del día → append-only resuelve la historia.
- **Modelar `metric_key` con prefijo de red** (ej. `ig_reach`, `fb_page_views`, `tt_view_count`)
  para evitar colisiones, ya que el significado difiere entre redes. El catálogo lleva `platform`.
- **No asumir paridad** IG/FB/TikTok: TikTok dará bastante menos que Meta a nivel API estándar.
- **Marcar métricas deprecadas/en migración** en el catálogo (`impressions`→`views`) y guardar el
  payload crudo (ya decidido) para reprocesar cuando Meta cambie campos.
- **Validar todo en sandbox/development** antes de fijar nombres: las tablas de arriba reflejan la
  doc a jun-2026, pero estas APIs cambian seguido.

---

## Fuentes

- [Instagram Account Insights — Meta for Developers](https://developers.facebook.com/docs/instagram-platform/api-reference/instagram-user/insights/) (v25.0, consultado 21-jun-2026)
- [Instagram Media Insights — Meta for Developers](https://developers.facebook.com/docs/instagram-platform/reference/instagram-media/insights/) (v25.0)
- [Page Insights API Updates — Meta for Developers (15-ago-2025)](https://developers.facebook.com/blog/post/2025/08/15/page-insights-api-updates/)
- [Page/insights — Graph API Reference](https://developers.facebook.com/docs/graph-api/reference/insights/)
- [Meta deprecates additional Page Insights API metrics from November 15 — ppc.land](https://ppc.land/meta-deprecates-additional-page-insights-api-metrics-from-november-15/)
- [App Review — Instagram Platform](https://developers.facebook.com/docs/instagram-platform/app-review/)
- [TikTok List Videos — `/v2/video/list/`](https://developers.tiktok.com/doc/tiktok-api-v2-video-list)
- [TikTok Get User Info — `/v2/user/info/`](https://developers.tiktok.com/doc/tiktok-api-v2-get-user-info)
- [TikTok User Info scope migration bulletin](https://developers.tiktok.com/bulletin/user-info-scope-migration)
- [TikTok API Scopes Reference](https://developers.tiktok.com/doc/tiktok-api-scopes)
- [TikTok App Review FAQ / Guidelines](https://developers.tiktok.com/doc/getting-started-faq)
- [Instagram Insights metrics deprecation (abr-2025) — Emplifi docs](https://docs.emplifi.io/platform/latest/home/instagram-insights-metrics-deprecation-april-2025)
