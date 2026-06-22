-- Fil-Grama — seed del catálogo de métricas (track D: Métricas/Dashboard).
-- Fuente: spec/05-catalogo-metricas.md. Solo el set CORE del v1.
--   * Las EXTENDED quedan para una versión futura (se activan agregando filas, sin tocar código).
--   * Las derivadas (*_engagement_rate, *_follower_growth) NO son filas: se calculan en consulta.
-- Convención metric_key: prefijo de red (ig_/fb_/tt_) porque el significado difiere entre redes.
-- platform: 'INSTAGRAM' | 'FACEBOOK' | 'TIKTOK' (coincide con el enum Platform / social_accounts).
-- ON CONFLICT (key) DO NOTHING → idempotente; V3 se aplica limpio sobre V1 (no depende de V2).

INSERT INTO metrics (key, display_name, platform, level, unit, tier, state) VALUES
    -- ===================== Instagram — cuenta (ACCOUNT) =====================
    ('ig_followers_count',        'Seguidores',                 'INSTAGRAM', 'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('ig_reach',                  'Alcance',                    'INSTAGRAM', 'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('ig_views',                  'Visualizaciones',            'INSTAGRAM', 'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('ig_total_interactions',     'Interacciones',              'INSTAGRAM', 'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('ig_accounts_engaged',       'Cuentas que interactuaron',  'INSTAGRAM', 'ACCOUNT', 'count', 'CORE', 'ACTIVE'),

    -- ===================== Instagram — post (POST) =====================
    ('ig_post_reach',             'Alcance del post',           'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_views',             'Reproducciones',             'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_likes',             'Likes',                      'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_comments',          'Comentarios',                'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_saved',             'Guardados',                  'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_shares',            'Compartidos',                'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_post_total_interactions','Interacciones del post',     'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),

    -- ===================== Instagram — story (POST, efímero) =====================
    ('ig_story_reach',            'Alcance de la story',        'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),
    ('ig_story_replies',          'Respuestas',                 'INSTAGRAM', 'POST',    'count', 'CORE', 'ACTIVE'),

    -- ===================== Facebook — página (ACCOUNT) =====================
    ('fb_page_views_total',       'Vistas de la página',        'FACEBOOK',  'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('fb_page_post_engagements',  'Interacciones con posts',    'FACEBOOK',  'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('fb_page_fan_adds',          'Nuevos seguidores',          'FACEBOOK',  'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('fb_page_views',             'Vistas',                     'FACEBOOK',  'ACCOUNT', 'count', 'CORE', 'ACTIVE'),

    -- ===================== Facebook — post (POST) =====================
    ('fb_post_engaged_users',     'Usuarios que interactuaron', 'FACEBOOK',  'POST',    'count', 'CORE', 'ACTIVE'),
    ('fb_post_clicks',            'Clicks',                     'FACEBOOK',  'POST',    'count', 'CORE', 'ACTIVE'),
    ('fb_post_reactions_total',   'Reacciones',                 'FACEBOOK',  'POST',    'count', 'CORE', 'ACTIVE'),
    ('fb_post_video_views',       'Reproducciones de video',    'FACEBOOK',  'POST',    'count', 'CORE', 'ACTIVE'),
    ('fb_post_views',             'Vistas del post',            'FACEBOOK',  'POST',    'count', 'CORE', 'ACTIVE'),

    -- ===================== TikTok — cuenta (ACCOUNT) =====================
    ('tt_follower_count',         'Seguidores',                 'TIKTOK',    'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('tt_likes_count',            'Likes totales recibidos',    'TIKTOK',    'ACCOUNT', 'count', 'CORE', 'ACTIVE'),
    ('tt_video_count',            'Cantidad de videos',         'TIKTOK',    'ACCOUNT', 'count', 'CORE', 'ACTIVE'),

    -- ===================== TikTok — video (POST) =====================
    ('tt_view_count',             'Reproducciones',             'TIKTOK',    'POST',    'count', 'CORE', 'ACTIVE'),
    ('tt_like_count',             'Likes',                      'TIKTOK',    'POST',    'count', 'CORE', 'ACTIVE'),
    ('tt_comment_count',          'Comentarios',                'TIKTOK',    'POST',    'count', 'CORE', 'ACTIVE'),
    ('tt_share_count',            'Compartidos',                'TIKTOK',    'POST',    'count', 'CORE', 'ACTIVE')
ON CONFLICT (key) DO NOTHING;
