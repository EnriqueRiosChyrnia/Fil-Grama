package com.filgrama.devdata;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed de datos DEMO realistas para que el front tenga con qué pintar dashboards.
 *
 * <p><b>Solo perfil {@code local}</b> (espeja {@link com.filgrama.auth.AdminSeedRunner}, que corre en
 * {@code !prod}). Los e2e usan el perfil default y los slice tests no activan {@code local}, así que
 * este runner NO se instancia en test/CI → no toca los 196 tests ni los mocks.
 *
 * <p><b>Idempotente:</b> si ya hay clientes, aborta. Inserta DIRECTO en las tablas vía
 * {@link JdbcTemplate} (no pasa por sync ni OAuth): {@code KeyHolder} para las filas padre y
 * {@code batchUpdate} para los miles de snapshots. Multi-tenant: {@code client_id} en toda fila.
 *
 * <p>@Order LOWEST_PRECEDENCE para correr después del admin seed (que no declara orden); de todos
 * modos el lookup del admin es tolerante a su ausencia ({@code connected_by} es nullable).
 */
@Component
@Profile("local")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoDataSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedRunner.class);

    private static final String ADMIN_EMAIL = "admin@filgrama.local";
    private static final String EMP1_EMAIL = "empleado1@filgrama.local";
    private static final String EMP2_EMAIL = "empleado2@filgrama.local";
    private static final String EMP_PASSWORD = "Empleado123!";

    private static final int DAYS = 90;

    // Metric keys del catálogo V3 (V3__seed_metrics.sql) — account level.
    private static final String[] IG_ACCT = {
            "ig_followers_count", "ig_reach", "ig_views", "ig_total_interactions", "ig_accounts_engaged"};
    private static final String[] FB_ACCT = {
            "fb_page_views_total", "fb_page_post_engagements", "fb_page_fan_adds", "fb_page_views"};
    private static final String[] TT_ACCT = {
            "tt_follower_count", "tt_likes_count", "tt_video_count"};

    // Factor de estacionalidad semanal (Mon..Sun).
    private static final double[] DOW = {1.05, 1.00, 1.05, 1.10, 1.20, 1.15, 0.95};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Random rng = new Random(20260622L);

    public DemoDataSeedRunner(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    // name, rubro, plan, status, combo-de-plataformas (CSV)
    private static final String[][] CLIENTS = {
            {"La Cabrera Asunción", "gastronomia", "Pro", "ACTIVE", "INSTAGRAM,FACEBOOK"},
            {"Boutique Lela", "moda", "Básico", "ACTIVE", "INSTAGRAM,TIKTOK"},
            {"Raíces Inmobiliaria", "inmobiliaria", "Pro", "ACTIVE", "INSTAGRAM"},
            {"Energym Asunción", "gimnasio", "Básico", "ACTIVE", "INSTAGRAM,FACEBOOK,TIKTOK"},
            {"Clínica Estética Lumière", "estetica", "Premium", "ACTIVE", "INSTAGRAM"},
            {"Café Bolsi", "cafeteria", "Básico", "ACTIVE", "INSTAGRAM,FACEBOOK"},
            {"TiendaPy Online", "ecommerce", "Pro", "ACTIVE", "INSTAGRAM,TIKTOK"},
            {"Estudio Jurídico Ferreira & Asoc.", "juridico", "Básico", "ARCHIVED", "FACEBOOK"},
            {"Fundación Manos Abiertas", "ong", "ONG", "ACTIVE", "INSTAGRAM"},
            {"Pizzería Napoli", "gastronomia", "Básico", "ARCHIVED", "INSTAGRAM"},
    };

    // Índices de clientes (activos) prioritarios por empleado.
    private static final int[] EMP1_CLIENTS = {0, 1, 2};
    private static final int[] EMP2_CLIENTS = {3, 4, 5};

    @Override
    public void run(String... args) {
        Long existing = jdbc.queryForObject("SELECT count(*) FROM clients", Long.class);
        if (existing != null && existing > 0) {
            log.info("[demo-seed] {} clientes ya presentes — seed demo OMITIDO (idempotente).", existing);
            return;
        }
        log.info("[demo-seed] BD limpia — generando datos demo (perfil local)...");

        Long adminId = jdbc.query("SELECT id FROM users WHERE email = ?",
                rs -> rs.next() ? rs.getLong(1) : null, ADMIN_EMAIL);
        if (adminId == null) {
            log.warn("[demo-seed] admin '{}' no encontrado todavía; connected_by quedará null.", ADMIN_EMAIL);
        }

        long emp1 = seedEmployee(EMP1_EMAIL, "Empleado Uno (demo)");
        long emp2 = seedEmployee(EMP2_EMAIL, "Empleado Dos (demo)");

        long[] clientIds = new long[CLIENTS.length];
        for (int i = 0; i < CLIENTS.length; i++) {
            String[] c = CLIENTS[i];
            clientIds[i] = insertId(
                    "INSERT INTO clients (name, plan, timezone, status, notes) VALUES (?,?,?,?,?)",
                    c[0], c[2], "America/Asuncion", c[3], "Cliente demo (" + c[1] + ")");
        }

        List<Object[]> prio = new ArrayList<>();
        for (int ci : EMP1_CLIENTS) {
            prio.add(new Object[]{emp1, clientIds[ci]});
        }
        for (int ci : EMP2_CLIENTS) {
            prio.add(new Object[]{emp2, clientIds[ci]});
        }
        jdbc.batchUpdate("INSERT INTO employee_client_priority (user_id, client_id) VALUES (?,?)", prio);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate base = today.minusDays(DAYS - 1L);

        List<Object[]> acctSnap = new ArrayList<>();
        List<Object[]> postSnap = new ArrayList<>();
        List<Object[]> media = new ArrayList<>();
        int accounts = 0;
        int posts = 0;

        for (int i = 0; i < CLIENTS.length; i++) {
            long clientId = clientIds[i];
            String rubro = CLIENTS[i][1];
            for (String pf : CLIENTS[i][4].split(",")) {
                long accountId = insertAccount(clientId, CLIENTS[i][0], pf, adminId, accounts);
                accounts++;
                posts += processAccount(clientId, accountId, pf, rubro, base, today, acctSnap, postSnap, media);
            }
        }

        batchInsert("INSERT INTO account_metric_snapshots "
                + "(client_id, account_id, metric_key, value, period, captured_at, capture_date) "
                + "VALUES (?,?,?,?,?,?,?)", acctSnap);
        batchInsert("INSERT INTO post_metric_snapshots "
                + "(client_id, account_id, post_id, metric_key, value, captured_at, capture_date) "
                + "VALUES (?,?,?,?,?,?,?)", postSnap);
        batchInsert("INSERT INTO media_assets "
                + "(post_id, client_id, kind, storage_path, content_type, bytes, captured_at) "
                + "VALUES (?,?,?,?,?,?,?)", media);

        log.info("[demo-seed] LISTO. clientes={} empleados=2 cuentas={} posts={} "
                        + "account_snapshots={} post_snapshots={} media_assets={}",
                CLIENTS.length, accounts, posts, acctSnap.size(), postSnap.size(), media.size());
    }

    // ---- empleados / cuentas ------------------------------------------------

    private long seedEmployee(String email, String fullName) {
        return insertId(
                "INSERT INTO users (email, password_hash, full_name, role, is_active) VALUES (?,?,?,?,?)",
                email, passwordEncoder.encode(EMP_PASSWORD), fullName, "EMPLEADO", true);
    }

    private long insertAccount(long clientId, String clientName, String platform, Long adminId, int seq) {
        String accountType = "TIKTOK".equals(platform) ? "CREATOR" : "BUSINESS";
        String handle = "@" + slug(clientName) + ("INSTAGRAM".equals(platform) ? "" : "." + platform.toLowerCase());
        String externalId = platform.toLowerCase() + "-" + (1_000_000 + rng.nextInt(8_999_999)) + seq;
        Instant connectedAt = Instant.now().minus(120L + rng.nextInt(240), ChronoUnit.DAYS);
        return insertId(
                "INSERT INTO social_accounts "
                        + "(client_id, platform, external_account_id, handle, display_name, account_type, "
                        + "status, connected_by, connected_at) VALUES (?,?,?,?,?,?,?,?,?)",
                clientId, platform, externalId, handle, clientName, accountType,
                "CONNECTED", adminId, off(connectedAt));
    }

    // ---- generación de series por cuenta ------------------------------------

    /** Devuelve la cantidad de posts insertados para la cuenta. */
    private int processAccount(long clientId, long accountId, String platform, String rubro,
                               LocalDate base, LocalDate today,
                               List<Object[]> acctSnap, List<Object[]> postSnap, List<Object[]> media) {

        // Tamaño base por red; cuentas chicas ⇒ engagement % más alto.
        double followersBase;
        double maxRange;
        switch (platform) {
            case "INSTAGRAM" -> { followersBase = 2_000 + rng.nextInt(118_000); maxRange = 120_000; }
            case "FACEBOOK" -> { followersBase = 1_000 + rng.nextInt(59_000); maxRange = 60_000; }
            default -> { followersBase = 500 + rng.nextInt(249_500); maxRange = 250_000; } // TIKTOK
        }
        double sizeFrac = Math.min(1.0, followersBase / maxRange);
        double er = clamp(0.02 + 0.06 * (1 - sizeFrac) + rng.nextGaussian() * 0.005, 0.012, 0.09);
        double dailyGrowth = 0.001 + rng.nextDouble() * 0.004; // 0,1–0,5 %/día
        double reachFactor = 0.3 + rng.nextDouble() * 0.6;

        // Serie de "tamaño" diaria (acumulativo creciente con ruido + algún día viral).
        double[] followers = new double[DAYS];
        double cur = followersBase;
        for (int d = 0; d < DAYS; d++) {
            double g = 1 + dailyGrowth + rng.nextGaussian() * 0.001;
            if (rng.nextDouble() < 0.03) {
                g += 0.02 + rng.nextDouble() * 0.03; // día viral +2–5 %
            }
            cur *= Math.max(1.0, g);
            followers[d] = cur;
        }

        // Acumulativos extra para TikTok.
        double ttLikes = followersBase * (2 + rng.nextDouble() * 8);
        double ttVideos = 30 + rng.nextInt(370);

        for (int d = 0; d < DAYS; d++) {
            LocalDate date = base.plusDays(d);
            OffsetDateTime at = at06(date);
            double wk = DOW[date.getDayOfWeek().getValue() - 1];
            double f = followers[d];

            switch (platform) {
                case "INSTAGRAM" -> {
                    double reach = f * reachFactor * wk * noise() * spike();
                    double interactions = reach * (er * 0.6 + rng.nextDouble() * 0.02); // ≈ reach×0,02–0,06
                    double engaged = interactions * 0.7;
                    double views = reach * (1.1 + rng.nextDouble() * 0.3);
                    acctSnap.add(acctRow(clientId, accountId, "ig_followers_count", f, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "ig_reach", reach, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "ig_views", views, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "ig_total_interactions", interactions, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "ig_accounts_engaged", engaged, at, date));
                }
                case "FACEBOOK" -> {
                    double views = f * (0.2 + reachFactor * 0.4) * wk * noise() * spike();
                    double engagements = f * er * wk * noise();
                    double fanAdds = f * (0.0005 + rng.nextDouble() * 0.0025) * wk * noise();
                    acctSnap.add(acctRow(clientId, accountId, "fb_page_views", views, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "fb_page_views_total",
                            views * (1.5 + rng.nextDouble()), at, date));
                    acctSnap.add(acctRow(clientId, accountId, "fb_page_post_engagements", engagements, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "fb_page_fan_adds", fanAdds, at, date));
                }
                default -> { // TIKTOK
                    ttLikes += f * (0.01 + rng.nextDouble() * 0.04) * wk * noise();
                    if (rng.nextDouble() < 0.25) {
                        ttVideos += 1;
                    }
                    acctSnap.add(acctRow(clientId, accountId, "tt_follower_count", f, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "tt_likes_count", ttLikes, at, date));
                    acctSnap.add(acctRow(clientId, accountId, "tt_video_count", ttVideos, at, date));
                }
            }
        }

        return seedPosts(clientId, accountId, platform, rubro, followers[DAYS - 1], base, today, postSnap, media);
    }

    // ---- posts + sus snapshots ----------------------------------------------

    private int seedPosts(long clientId, long accountId, String platform, String rubro,
                          double followersToday, LocalDate base, LocalDate today,
                          List<Object[]> postSnap, List<Object[]> media) {
        int n = 15 + rng.nextInt(16); // 15–30
        int viralA = rng.nextInt(n);
        int viralB = rng.nextInt(n);
        int seq = 0;
        for (int p = 0; p < n; p++) {
            int pdi = rng.nextInt(DAYS);
            LocalDate pubDate = base.plusDays(pdi);
            String type = pickPostType(platform);
            boolean ephemeral = "STORY".equals(type);
            Instant publishedAt = at06(pubDate).toInstant().plus(2L + rng.nextInt(14), ChronoUnit.HOURS);
            Instant expiresAt = ephemeral ? publishedAt.plus(24, ChronoUnit.HOURS) : null;
            String extId = platform.toLowerCase() + "_" + accountId + "_" + (seq++) + "_" + rng.nextInt(99999);
            String permalink = "https://" + platform.toLowerCase() + ".demo/" + slug(rubro) + "/" + extId;
            String thumb = "https://picsum.photos/seed/" + extId + "/320/320";

            long postId = insertId(
                    "INSERT INTO posts (client_id, account_id, platform, external_post_id, post_type, "
                            + "permalink, caption, remote_thumbnail_url, is_ephemeral, published_at, "
                            + "expires_at, first_seen_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    clientId, accountId, platform, extId, type, permalink, caption(rubro), thumb,
                    ephemeral, off(publishedAt), off(expiresAt), off(publishedAt));

            // Pico de alcance del post: 0,2–1,5× followers; virales hasta varias×.
            double mult = 0.2 + rng.nextDouble() * 1.3;
            if (p == viralA || p == viralB) {
                mult *= 2.5 + rng.nextDouble() * 3.5;
            }
            double reachPeak = Math.max(50, followersToday * mult);
            double er = 0.03 + rng.nextDouble() * 0.05;

            long lifeDays = Math.min(14, ChronoUnit.DAYS.between(pubDate, today) + 1);
            for (int t = 0; t < lifeDays; t++) {
                LocalDate sd = pubDate.plusDays(t);
                OffsetDateTime at = at06(sd);
                double sat = 1 - Math.pow(0.6, t + 1.0); // crece y se aplana (saturación)
                double reach = reachPeak * sat;
                addPostMetrics(platform, type, clientId, accountId, postId, reach, er, at, sd, postSnap);
            }

            media.add(new Object[]{postId, clientId, "THUMBNAIL", "demo/thumbs/" + extId + ".jpg",
                    "image/jpeg", 20_000 + rng.nextInt(180_000), off(publishedAt)});
        }
        return n;
    }

    private void addPostMetrics(String platform, String type, long clientId, long accountId, long postId,
                                double reach, double er, OffsetDateTime at, LocalDate sd, List<Object[]> out) {
        switch (platform) {
            case "INSTAGRAM" -> {
                if ("STORY".equals(type)) {
                    double sreach = reach * 0.8;
                    out.add(postRow(clientId, accountId, postId, "ig_story_reach", sreach, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_story_replies",
                            sreach * (0.005 + rng.nextDouble() * 0.015), at, sd));
                } else {
                    double likes = reach * er;
                    double comments = likes * (0.03 + rng.nextDouble() * 0.09);
                    double saved = likes * (0.05 + rng.nextDouble() * 0.10);
                    double shares = likes * (0.04 + rng.nextDouble() * 0.08);
                    out.add(postRow(clientId, accountId, postId, "ig_post_reach", reach, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_views",
                            reach * (1.1 + rng.nextDouble() * 0.3), at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_likes", likes, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_comments", comments, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_saved", saved, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_shares", shares, at, sd));
                    out.add(postRow(clientId, accountId, postId, "ig_post_total_interactions",
                            likes + comments + saved + shares, at, sd));
                }
            }
            case "FACEBOOK" -> {
                double engaged = reach * er;
                out.add(postRow(clientId, accountId, postId, "fb_post_views", reach, at, sd));
                out.add(postRow(clientId, accountId, postId, "fb_post_engaged_users", engaged, at, sd));
                out.add(postRow(clientId, accountId, postId, "fb_post_clicks",
                        engaged * (0.3 + rng.nextDouble() * 0.4), at, sd));
                out.add(postRow(clientId, accountId, postId, "fb_post_reactions_total",
                        engaged * (0.5 + rng.nextDouble() * 0.4), at, sd));
                if ("VIDEO".equals(type)) {
                    out.add(postRow(clientId, accountId, postId, "fb_post_video_views",
                            reach * (0.6 + rng.nextDouble() * 0.3), at, sd));
                }
            }
            default -> { // TIKTOK (VIDEO)
                double views = reach * (1 + rng.nextDouble() * 2);
                double likes = views * (0.05 + rng.nextDouble() * 0.10);
                out.add(postRow(clientId, accountId, postId, "tt_view_count", views, at, sd));
                out.add(postRow(clientId, accountId, postId, "tt_like_count", likes, at, sd));
                out.add(postRow(clientId, accountId, postId, "tt_comment_count",
                        likes * (0.02 + rng.nextDouble() * 0.06), at, sd));
                out.add(postRow(clientId, accountId, postId, "tt_share_count",
                        likes * (0.03 + rng.nextDouble() * 0.07), at, sd));
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    private long insertId(String sql, Object... params) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("[demo-seed] sin id generado para: " + sql);
        }
        return key.longValue();
    }

    private void batchInsert(String sql, List<Object[]> rows) {
        final int chunk = 1_000;
        for (int i = 0; i < rows.size(); i += chunk) {
            jdbc.batchUpdate(sql, rows.subList(i, Math.min(rows.size(), i + chunk)));
        }
    }

    private Object[] acctRow(long clientId, long accountId, String key, double val,
                             OffsetDateTime at, LocalDate date) {
        return new Object[]{clientId, accountId, key, v(val), "day", at, date};
    }

    private Object[] postRow(long clientId, long accountId, long postId, String key, double val,
                             OffsetDateTime at, LocalDate date) {
        return new Object[]{clientId, accountId, postId, key, v(val), at, date};
    }

    private double noise() {
        return clamp(1 + rng.nextGaussian() * 0.12, 0.6, 1.6);
    }

    private double spike() {
        return rng.nextDouble() < 0.05 ? 1.5 + rng.nextDouble() * 1.5 : 1.0;
    }

    private static BigDecimal v(double x) {
        return BigDecimal.valueOf(Math.max(0, Math.round(x)));
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static OffsetDateTime at06(LocalDate d) {
        return OffsetDateTime.of(d, LocalTime.of(6, 0), ZoneOffset.UTC);
    }

    private static OffsetDateTime off(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    private String pickPostType(String platform) {
        if ("FACEBOOK".equals(platform)) {
            return rng.nextDouble() < 0.4 ? "VIDEO" : "IMAGE";
        }
        if ("TIKTOK".equals(platform)) {
            return "VIDEO";
        }
        double r = rng.nextDouble(); // INSTAGRAM
        if (r < 0.35) return "IMAGE";
        if (r < 0.65) return "REEL";
        if (r < 0.85) return "CAROUSEL";
        return "STORY";
    }

    private static String slug(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "");
        return n.isEmpty() ? "cuenta" : n;
    }

    private String caption(String rubro) {
        String[] base = switch (rubro) {
            case "gastronomia" -> new String[]{
                    "Hoy cocinamos para vos 🍝 reservá tu mesa",
                    "Nuevo plato de la semana, ¿te animás? 😋",
                    "Sabores que enamoran ❤️ #AsunciónGastronómica",
                    "Promo del finde: 2x1 en postres 🍰"};
            case "moda" -> new String[]{
                    "Nueva colección ya disponible 👗✨",
                    "Outfit del día: combiná y brillá 💫",
                    "Últimas talles, no te quedes sin el tuyo 🛍️",
                    "Tendencia primavera 2026 🌸"};
            case "inmobiliaria" -> new String[]{
                    "Departamento a estrenar en zona céntrica 🏢",
                    "Tu próxima casa te espera 🔑",
                    "Inversión segura: lotes con financiación 📈",
                    "Visitá nuestro showroom esta semana 🏠"};
            case "gimnasio" -> new String[]{
                    "Arrancá tu rutina hoy 💪 #SinExcusas",
                    "Clase de funcional a las 18h 🔥",
                    "Resultados reales, constancia real 🏋️",
                    "Promo verano: traé un amigo gratis 🤝"};
            case "estetica" -> new String[]{
                    "Tu piel merece lo mejor ✨",
                    "Tratamiento facial con 20% off este mes 💆",
                    "Realzá tu belleza natural 🌿",
                    "Agendá tu consulta sin cargo 📅"};
            case "cafeteria" -> new String[]{
                    "El mejor café para empezar el día ☕",
                    "Brunch del domingo, te esperamos 🥐",
                    "Nuevo blend de temporada disponible 🫘",
                    "Momentos que se disfrutan despacio 🍮"};
            case "ecommerce" -> new String[]{
                    "Envío gratis a todo el país 🚚",
                    "Ofertas que no podés dejar pasar 🔥",
                    "Comprá online, recibí en 24h 📦",
                    "Stock limitado, aprovechá ya 🛒"};
            case "juridico" -> new String[]{
                    "Asesoría legal que protege tus derechos ⚖️",
                    "Consultá tu caso con nuestro equipo 📑",
                    "Tranquilidad jurídica para tu empresa 🏛️",
                    "Primera consulta sin cargo 🤝"};
            default -> new String[]{ // ong
                    "Sumate a la causa, cada ayuda cuenta 🤲",
                    "Gracias a vos llegamos a más familias ❤️",
                    "Voluntariado abierto este mes 🙌",
                    "Tu donación transforma vidas 🌟"};
        };
        return base[rng.nextInt(base.length)];
    }
}
