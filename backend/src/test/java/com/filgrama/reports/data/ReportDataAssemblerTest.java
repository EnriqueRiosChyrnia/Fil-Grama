package com.filgrama.reports.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Client;
import com.filgrama.domain.Metric;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.service.MetricCatalogService;
import com.filgrama.metrics.service.SummaryService;
import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportQueryRepository.DemographicRow;
import com.filgrama.reports.ReportQueryRepository.PlatformMetricKey;
import com.filgrama.reports.ReportQueryRepository.PostMetricValue;
import com.filgrama.reports.ReportQueryRepository.PostRow;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;
import com.filgrama.reports.data.ReportData.ReportPost;
import com.filgrama.reports.render.ThumbnailLoader;
import com.filgrama.repository.ClientRepository;
import com.filgrama.repository.SocialAccountRepository;

/**
 * TAREA B: el delta de los KPIs por red debe ser {@code null} cuando el período anterior NO tiene
 * snapshots (cuenta recién conectada) — no el valor completo —, distinguiendo "previo ausente" de
 * "previo = 0 real". Dependencias mockeadas (sin DB).
 */
class ReportDataAssemblerTest {

    private static final Long CLIENT = 9L;
    private static final LocalDate FROM = LocalDate.parse("2026-05-01");
    private static final LocalDate TO = LocalDate.parse("2026-05-31");
    // previousPeriod(31 días): prevTo = 04-30, prevFrom = 03-31.
    private static final LocalDate PREV_FROM = LocalDate.parse("2026-03-31");
    private static final LocalDate PREV_TO = LocalDate.parse("2026-04-30");

    private SummaryService summaryService;
    private ReportQueryRepository reportQuery;
    private SocialAccountRepository accountRepository;
    private MetricCatalogService catalog;
    private ThumbnailLoader thumbnailLoader;
    private ReportDataAssembler assembler;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        accountRepository = mock(SocialAccountRepository.class);
        summaryService = mock(SummaryService.class);
        reportQuery = mock(ReportQueryRepository.class);
        RankMetricResolver rankResolver = mock(RankMetricResolver.class);
        thumbnailLoader = mock(ThumbnailLoader.class);
        catalog = mock(MetricCatalogService.class);
        assembler = new ReportDataAssembler(clientRepository, accountRepository, summaryService,
                reportQuery, rankResolver, thumbnailLoader, catalog);

        Client client = new Client();
        client.setId(CLIENT);
        client.setName("Molinos");
        client.setTimezone("UTC");
        when(clientRepository.findById(CLIENT)).thenReturn(Optional.of(client));
        when(rankResolver.normalize(any())).thenReturn("reach");
        when(reportQuery.findPosts(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(reportQuery.latestPostMetrics(any(), any(), any(), any())).thenReturn(List.of());
        when(reportQuery.findDemographics(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(catalog.find(any())).thenReturn(Optional.empty());
        when(thumbnailLoader.load(any(), any())).thenReturn(new ThumbnailLoader.Thumbnail(null, null));
        // Defaults amables para los tests v1.1 que no ejercitan deltas/baseline (los tests de TAREA B
        // los pisan con stubs más específicos, que Mockito prioriza).
        when(reportQuery.accountMetricKeysPresent(any(), any(), any(), any(), any())).thenReturn(Set.of());
        when(summaryService.summary(any(), any(), any(), any()))
                .thenReturn(new SummaryResponse(CLIENT, null, null, List.of()));

        // Período actual: 150 seguidores, 931 interacciones.
        when(summaryService.summary(eq(CLIENT), eq(FROM), eq(TO), isNull())).thenReturn(
                igSummary(FROM, TO, bd(150), bd(931)));
    }

    @Test
    void deltaIsNullWhenPreviousPeriodHasNoSnapshots() {
        // Previo SIN snapshots: SummaryService igual devuelve la red con métricas en 0 (COALESCE).
        when(summaryService.summary(eq(CLIENT), eq(PREV_FROM), eq(PREV_TO), isNull())).thenReturn(
                igSummary(PREV_FROM, PREV_TO, bd(0), bd(0)));
        // ...pero NO hay snapshots de cuenta en el rango previo → sin baseline.
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), any(), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of());

        PlatformKpis ig = assembleInstagram();

        assertThat(kpi(ig, "ig_followers_count").value()).isEqualByComparingTo("150");
        assertThat(kpi(ig, "ig_followers_count").delta()).isNull();
        assertThat(kpi(ig, "ig_total_interactions").value()).isEqualByComparingTo("931");
        assertThat(kpi(ig, "ig_total_interactions").delta()).isNull();
        // Sin baseline tampoco se inventa la evolución del alcance.
        assertThat(ig.reach().previous()).isNull();
        assertThat(ig.reach().deltaPct()).isNull();
    }

    @Test
    void deltaIsRealDifferenceWhenPreviousPeriodHasSnapshots() {
        when(summaryService.summary(eq(CLIENT), eq(PREV_FROM), eq(PREV_TO), isNull())).thenReturn(
                igSummary(PREV_FROM, PREV_TO, bd(120), bd(800)));
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), any(), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of(
                        new PlatformMetricKey("INSTAGRAM", "ig_followers_count"),
                        new PlatformMetricKey("INSTAGRAM", "ig_total_interactions"),
                        new PlatformMetricKey("INSTAGRAM", "ig_reach")));

        PlatformKpis ig = assembleInstagram();

        assertThat(kpi(ig, "ig_followers_count").delta()).isEqualByComparingTo("30");  // 150 - 120
        assertThat(kpi(ig, "ig_total_interactions").delta()).isEqualByComparingTo("131"); // 931 - 800
    }

    @Test
    void realZeroBaselineKeepsFullDeltaNotNull() {
        // Previo = 0 REAL (sí hay snapshot, vale 0): el delta es el valor completo, NO null.
        when(summaryService.summary(eq(CLIENT), eq(PREV_FROM), eq(PREV_TO), isNull())).thenReturn(
                igSummary(PREV_FROM, PREV_TO, bd(0), bd(0)));
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), any(), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of(
                        new PlatformMetricKey("INSTAGRAM", "ig_followers_count"),
                        new PlatformMetricKey("INSTAGRAM", "ig_total_interactions")));

        PlatformKpis ig = assembleInstagram();

        assertThat(kpi(ig, "ig_followers_count").delta()).isEqualByComparingTo("150");
        assertThat(kpi(ig, "ig_total_interactions").delta()).isEqualByComparingTo("931");
    }

    // ---- TAREA C: reporte por cuenta (accountIds) ----

    @Test
    void withAccountIdsUsesAccountScopedSummaryAndDerivesPlatformFromAccounts() {
        // El cliente tiene 2 cuentas IG + 1 TIKTOK; pedimos SÓLO la cuenta IG #11.
        when(accountRepository.findByClientId(CLIENT)).thenReturn(List.of(
                account(11L, Platform.INSTAGRAM),
                account(12L, Platform.INSTAGRAM),
                account(13L, Platform.TIKTOK)));
        when(summaryService.summaryForAccounts(eq(CLIENT), eq(FROM), eq(TO), eq(List.of(11L))))
                .thenReturn(igSummary(FROM, TO, bd(150), bd(931)));
        when(summaryService.summaryForAccounts(eq(CLIENT), eq(PREV_FROM), eq(PREV_TO), eq(List.of(11L))))
                .thenReturn(igSummary(PREV_FROM, PREV_TO, bd(0), bd(0)));
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), eq(List.of(11L)), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of());

        // platforms=["TIKTOK"] se IGNORA cuando vienen accountIds: la red se deriva de la cuenta.
        ReportData data = assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, List.of("TIKTOK"), List.of(11L), "reach");

        assertThat(data.platforms()).containsExactly("INSTAGRAM");
        // KPIs account-scoped: NO se usa el summary por red (todas las cuentas).
        verify(summaryService, never()).summary(any(), any(), any(), any());
        verify(summaryService).summaryForAccounts(eq(CLIENT), eq(FROM), eq(TO), eq(List.of(11L)));
        // Posts y baseline restringidos a la cuenta pedida.
        verify(reportQuery).findPosts(eq(CLIENT), eq(FROM), eq(TO), eq(List.of("INSTAGRAM")), eq(List.of(11L)));
    }

    @Test
    void withoutAccountIdsKeepsByNetworkBehaviour() {
        // Sin accountIds: summary por red (no account-scoped) y queries con accountIds = null (compat).
        when(summaryService.summary(eq(CLIENT), eq(PREV_FROM), eq(PREV_TO), isNull())).thenReturn(
                igSummary(PREV_FROM, PREV_TO, bd(0), bd(0)));
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), isNull(), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of());

        assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, List.of("INSTAGRAM"), null, "reach");

        verify(summaryService, never()).summaryForAccounts(any(), any(), any(), any());
        verify(summaryService).summary(eq(CLIENT), eq(FROM), eq(TO), isNull());
        verify(reportQuery).findPosts(eq(CLIENT), eq(FROM), eq(TO), eq(List.of("INSTAGRAM")), isNull());
    }

    @Test
    void accountIdOfAnotherClientIsNotFound() {
        // Multi-tenant: una cuenta que no es del cliente → 404 (no se filtra silenciosamente).
        when(accountRepository.findByClientId(CLIENT)).thenReturn(List.of(account(11L, Platform.INSTAGRAM)));

        assertThatThrownBy(() -> assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, null, List.of(999L), "reach"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    // ---- v1.1: demografía, split, interacciones por acción, tipo de contenido, watch-time ----

    @Test
    void demographicsAggregatedByBreakdownTypeSortedDescWithPercentages() {
        when(reportQuery.findDemographics(eq(CLIENT), any(), any(), eq(FROM), eq(TO))).thenReturn(List.of(
                new DemographicRow("INSTAGRAM", "CITY", "Encarnación", bd(75)),
                new DemographicRow("INSTAGRAM", "CITY", "Asunción", bd(25)),
                new DemographicRow("INSTAGRAM", "COUNTRY", "PY", bd(80)),
                new DemographicRow("INSTAGRAM", "COUNTRY", "AR", bd(20)),
                new DemographicRow("INSTAGRAM", "GENDER", "F", bd(60)),
                new DemographicRow("INSTAGRAM", "GENDER", "M", bd(40))));

        PlatformKpis ig = assembleInstagram();

        assertThat(ig.demographics()).isNotNull();
        assertThat(ig.demographics().cities()).hasSize(2);
        assertThat(ig.demographics().cities().get(0).label()).isEqualTo("Encarnación");
        assertThat(ig.demographics().cities().get(0).pct()).isEqualByComparingTo("0.75");
        assertThat(ig.demographics().cities().get(1).label()).isEqualTo("Asunción");
        // Código ISO de país (research/06 §1) se traduce a nombre en español.
        assertThat(ig.demographics().countries().get(0).label()).isEqualTo("Paraguay");
        assertThat(ig.demographics().countries().get(0).pct()).isEqualByComparingTo("0.8");
        // Género: valores crudos de Meta (F/M) a etiqueta legible.
        assertThat(ig.demographics().genders()).extracting("label").containsExactly("Mujeres", "Hombres");
    }

    @Test
    void demographicsIsNullWhenNoRowsCaptured() {
        // Default de setUp(): findDemographics devuelve vacío.
        assertThat(assembleInstagram().demographics()).isNull();
    }

    @Test
    void viewsFollowerSplitExtractedFromAccountMetricsWithPercentages() {
        List<SummaryMetric> metrics = baseIgMetrics(bd(150), bd(931));
        metrics.add(new SummaryMetric("ig_views_followers", "Visualizaciones — seguidores", "count",
                bd(3616), bd(3616)));
        metrics.add(new SummaryMetric("ig_views_non_followers", "Visualizaciones — no seguidores", "count",
                bd(1484), bd(1484)));
        when(summaryService.summary(eq(CLIENT), eq(FROM), eq(TO), isNull()))
                .thenReturn(igSummary(FROM, TO, metrics));

        PlatformKpis ig = assembleInstagram();

        assertThat(ig.viewsFollowerSplit()).isNotNull();
        assertThat(ig.viewsFollowerSplit().followers()).isEqualByComparingTo("3616");
        assertThat(ig.viewsFollowerSplit().nonFollowers()).isEqualByComparingTo("1484");
        assertThat(ig.viewsFollowerSplit().followerPct()).isEqualByComparingTo("0.709");
        assertThat(ig.viewsFollowerSplit().nonFollowerPct()).isEqualByComparingTo("0.291");
    }

    @Test
    void viewsFollowerSplitAndProfileActivityAreNullWhenKeysAbsent() {
        // Default de setUp(): la red no trae ig_views_followers/non_followers ni las keys de actividad.
        PlatformKpis ig = assembleInstagram();

        assertThat(ig.viewsFollowerSplit()).isNull();
        assertThat(ig.profileActivity()).isNull();
    }

    @Test
    void profileActivityKeepsIndividualNullsWhenOnlyPartiallyCaptured() {
        List<SummaryMetric> metrics = baseIgMetrics(bd(150), bd(931));
        // Sólo llegó el tap a WhatsApp; profileViews/directionTaps no se capturaron todavía.
        metrics.add(new SummaryMetric("ig_taps_whatsapp", "Clics a WhatsApp", "count", bd(8), bd(8)));
        when(summaryService.summary(eq(CLIENT), eq(FROM), eq(TO), isNull()))
                .thenReturn(igSummary(FROM, TO, metrics));

        PlatformKpis ig = assembleInstagram();

        assertThat(ig.profileActivity()).isNotNull();
        assertThat(ig.profileActivity().whatsappTaps()).isEqualByComparingTo("8");
        assertThat(ig.profileActivity().profileViews()).isNull();
        assertThat(ig.profileActivity().directionTaps()).isNull();
    }

    @Test
    void interactionsByActionSumsAcrossAllPostsOfThePlatform() {
        stubTwoInstagramPosts();
        stubActionCatalog();

        PlatformKpis ig = assembleInstagram();

        assertThat(ig.interactionsByAction()).extracting(Kpi::key).containsExactly(
                "ig_post_likes", "ig_post_comments", "ig_post_shares", "ig_post_saved", "ig_post_reposts");
        assertThat(actionValue(ig, "ig_post_likes")).isEqualByComparingTo("30"); // 10 + 20
        assertThat(actionValue(ig, "ig_post_comments")).isEqualByComparingTo("20"); // 5 + 15
        assertThat(actionValue(ig, "ig_post_shares")).isEqualByComparingTo("0"); // no capturado
        Kpi likes = ig.interactionsByAction().stream().filter(k -> k.key().equals("ig_post_likes")).findFirst()
                .orElseThrow();
        assertThat(likes.displayName()).isEqualTo("Me gusta");
        assertThat(likes.delta()).isNull(); // sin comparación vs período anterior en v1.1
    }

    @Test
    void viewsByContentTypeAggregatesAndSortsDescending() {
        stubTwoInstagramPosts();

        PlatformKpis ig = assembleInstagram();

        assertThat(ig.viewsByContentType()).extracting("displayType").containsExactly("Reels", "Feed");
        assertThat(ig.viewsByContentType().get(0).views()).isEqualByComparingTo("1000");
        assertThat(ig.viewsByContentType().get(0).pct()).isEqualByComparingTo("0.6667");
        assertThat(ig.viewsByContentType().get(1).views()).isEqualByComparingTo("500");
    }

    @Test
    void watchTimeAttachedOnlyToReelsFromCatalogKey() {
        stubTwoInstagramPosts();

        ReportData data = assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, List.of("INSTAGRAM"), null, "reach");

        ReportPost reel = data.topPosts().stream().filter(p -> "REEL".equals(p.postType())).findFirst().orElseThrow();
        ReportPost image = data.topPosts().stream().filter(p -> "IMAGE".equals(p.postType())).findFirst()
                .orElseThrow();
        assertThat(reel.watchTimeSeconds()).isEqualByComparingTo("13.5");
        assertThat(image.watchTimeSeconds()).isNull();
    }

    // ---- helpers ----

    private static SocialAccount account(Long id, Platform platform) {
        SocialAccount a = new SocialAccount();
        a.setId(id);
        a.setClientId(CLIENT);
        a.setPlatform(platform);
        return a;
    }

    private PlatformKpis assembleInstagram() {
        ReportData data = assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, List.of("INSTAGRAM"), null, "reach");
        return data.kpis().stream().filter(k -> k.platform().equals("INSTAGRAM")).findFirst().orElseThrow();
    }

    private static Kpi kpi(PlatformKpis platform, String key) {
        return platform.metrics().stream().filter(k -> k.key().equals(key)).findFirst().orElseThrow();
    }

    /** Summary IG con seguidores (stock → latest) e interacciones (flujo → total) + alcance. */
    private static SummaryResponse igSummary(LocalDate from, LocalDate to, BigDecimal followers,
                                             BigDecimal interactions) {
        return igSummary(from, to, baseIgMetrics(followers, interactions));
    }

    /** Variante con la lista de métricas a medida (para agregar keys v1.1 sueltas por test). */
    private static SummaryResponse igSummary(LocalDate from, LocalDate to, List<SummaryMetric> metrics) {
        PlatformSummary ig = new PlatformSummary("INSTAGRAM", metrics, null, null);
        return new SummaryResponse(CLIENT, from, to, List.of(ig));
    }

    private static ArrayList<SummaryMetric> baseIgMetrics(BigDecimal followers, BigDecimal interactions) {
        return new ArrayList<>(List.of(
                new SummaryMetric("ig_followers_count", "Seguidores", "count", followers, followers),
                new SummaryMetric("ig_total_interactions", "Interacciones", "count", interactions, interactions),
                new SummaryMetric("ig_reach", "Alcance", "count", interactions, interactions)));
    }

    /** 2 posts IG en el rango: un reel (likes/comments/views/watch-time) y una imagen (likes/comments/views). */
    private void stubTwoInstagramPosts() {
        PostRow reel = new PostRow(101L, 11L, "INSTAGRAM", "REEL", "https://instagram.com/p/101", "cap reel",
                null, Instant.parse("2026-05-10T10:00:00Z"), false);
        PostRow image = new PostRow(102L, 11L, "INSTAGRAM", "IMAGE", "https://instagram.com/p/102", "cap image",
                null, Instant.parse("2026-05-12T10:00:00Z"), false);
        when(reportQuery.findPosts(eq(CLIENT), eq(FROM), eq(TO), any(), any())).thenReturn(List.of(reel, image));
        when(reportQuery.latestPostMetrics(eq(CLIENT), any(), eq(FROM), eq(TO))).thenReturn(List.of(
                new PostMetricValue(101L, "ig_post_likes", bd(10)),
                new PostMetricValue(102L, "ig_post_likes", bd(20)),
                new PostMetricValue(101L, "ig_post_comments", bd(5)),
                new PostMetricValue(102L, "ig_post_comments", bd(15)),
                new PostMetricValue(101L, "ig_post_views", bd(1000)),
                new PostMetricValue(102L, "ig_post_views", bd(500)),
                new PostMetricValue(101L, "ig_reels_avg_watch_time", new BigDecimal("13.5"))));
    }

    private void stubActionCatalog() {
        when(catalog.find("ig_post_likes")).thenReturn(Optional.of(metric("ig_post_likes", "Me gusta", "count")));
        when(catalog.find("ig_post_comments"))
                .thenReturn(Optional.of(metric("ig_post_comments", "Comentarios", "count")));
        when(catalog.find("ig_post_shares")).thenReturn(Optional.of(metric("ig_post_shares", "Compartidos", "count")));
        when(catalog.find("ig_post_saved")).thenReturn(Optional.of(metric("ig_post_saved", "Guardados", "count")));
        when(catalog.find("ig_post_reposts")).thenReturn(Optional.of(metric("ig_post_reposts", "Reposts", "count")));
    }

    private static Metric metric(String key, String displayName, String unit) {
        Metric m = new Metric();
        m.setKey(key);
        m.setDisplayName(displayName);
        m.setUnit(unit);
        return m;
    }

    private static BigDecimal actionValue(PlatformKpis platform, String key) {
        return platform.interactionsByAction().stream().filter(k -> k.key().equals(key)).findFirst()
                .orElseThrow().value();
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
