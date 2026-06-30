-- Fil-Grama — v1.1 (FG-T1): adiciones al catálogo para el reporte mensual completo de Instagram.
-- Fuente: spec/05-catalogo-metricas.md §"v1.1 — Adiciones para reporte completo de Instagram".
-- NO edita V3 (ya aplicada); agrega filas nuevas y promueve a CORE las que existían como EXTENDED.
-- ON CONFLICT (key) DO UPDATE → idempotente y "promueve" la fila si ya existiera (sin tocar código).
--
-- Validación sandbox (Fase A del track): NO se contó con cuenta IG profesional conectada en Dev Mode,
-- así que las keys marcadas ⚠ en spec/05 quedan ACTIVE pero PENDIENTES DE VALIDACIÓN: el provider las
-- pide en llamadas Graph separadas y best-effort, y el deriver solo persiste la fila si la API devolvió
-- el campo (degradación elegante). La descripción de cada una lo deja anotado. Ver
-- spec/research/06-reporte-automatico-ig-y-mcp.md §"Validación sandbox".
--
-- El split follow_type sobre total_interactions (⚠ [no probable]) NO se incluye: sin evidencia en el API.

INSERT INTO metrics (key, display_name, platform, level, unit, tier, state, description) VALUES
    -- ===== Promovidas a CORE (estaban planificadas como EXTENDED en spec/05) =====
    ('ig_follows_and_unfollows',  'Follows / unfollows',              'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', 'Altas/bajas del período. Requiere >=100 seguidores.'),
    ('ig_follower_demographics',  'Demografía de seguidores',         'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', 'Marca: la demografía se persiste en audience_demographics (lifetime, requiere >=100 seguidores). Activa la captura de demografía en el job.'),
    ('ig_reels_avg_watch_time',   'Tiempo medio de visionado (reel)', 'INSTAGRAM', 'POST',    'seconds', 'CORE',     'ACTIVE', 'Solo reels. La API devuelve ms; se persiste en segundos.'),
    ('ig_profile_links_taps',     'Taps en botones de perfil',        'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', 'Total de taps de contacto del período.'),

    -- ===== Nuevas (spec/05 §v1.1) =====
    ('ig_profile_views',          'Visitas al perfil',                'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación en sandbox: total_value del rango; plan B = sumar profile_visits de los media.'),
    ('ig_views_followers',        'Visualizaciones — seguidores',     'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación: breakdown follow_type de views.'),
    ('ig_views_non_followers',    'Visualizaciones — no seguidores',  'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación: breakdown follow_type de views.'),
    ('ig_reach_followers',        'Alcance — seguidores',             'INSTAGRAM', 'ACCOUNT', 'count',   'EXTENDED', 'ACTIVE', '⚠ pendiente de validación: breakdown follow_type de reach. EXTENDED: no se captura en v1.1 (queda catalogado).'),
    ('ig_reach_non_followers',    'Alcance — no seguidores',          'INSTAGRAM', 'ACCOUNT', 'count',   'EXTENDED', 'ACTIVE', '⚠ pendiente de validación: breakdown follow_type de reach. EXTENDED: no se captura en v1.1 (queda catalogado).'),
    ('ig_taps_whatsapp',          'Clics a WhatsApp',                 'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación: profile_links_taps breakdown contact_button_type; atribución no garantizada.'),
    ('ig_taps_direction',         'Clics a ubicación (Maps)',         'INSTAGRAM', 'ACCOUNT', 'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación: profile_links_taps breakdown contact_button_type.'),
    ('ig_post_reposts',           'Reposts del post',                 'INSTAGRAM', 'POST',    'count',   'CORE',     'ACTIVE', '⚠ pendiente de validación: métrica reposts por media.'),
    ('ig_post_profile_visits',    'Visitas al perfil desde el post',  'INSTAGRAM', 'POST',    'count',   'CORE',     'ACTIVE', 'profile_visits por media.')
ON CONFLICT (key) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    platform     = EXCLUDED.platform,
    level        = EXCLUDED.level,
    unit         = EXCLUDED.unit,
    tier         = EXCLUDED.tier,
    state        = EXCLUDED.state,
    description  = EXCLUDED.description;
