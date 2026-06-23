package com.filgrama.reports.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Client;
import com.filgrama.metrics.dto.PlatformSummary;
import com.filgrama.metrics.dto.SummaryMetric;
import com.filgrama.metrics.dto.SummaryResponse;
import com.filgrama.metrics.service.SummaryService;
import com.filgrama.reports.ReportFormat;
import com.filgrama.reports.ReportQueryRepository;
import com.filgrama.reports.ReportQueryRepository.PlatformMetricKey;
import com.filgrama.reports.ReportType;
import com.filgrama.reports.data.ReportData.Kpi;
import com.filgrama.reports.data.ReportData.PlatformKpis;
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
    private ReportDataAssembler assembler;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        SocialAccountRepository accountRepository = mock(SocialAccountRepository.class);
        summaryService = mock(SummaryService.class);
        reportQuery = mock(ReportQueryRepository.class);
        RankMetricResolver rankResolver = mock(RankMetricResolver.class);
        ThumbnailLoader thumbnailLoader = mock(ThumbnailLoader.class);
        assembler = new ReportDataAssembler(clientRepository, accountRepository, summaryService,
                reportQuery, rankResolver, thumbnailLoader);

        Client client = new Client();
        client.setId(CLIENT);
        client.setName("Molinos");
        client.setTimezone("UTC");
        when(clientRepository.findById(CLIENT)).thenReturn(Optional.of(client));
        when(rankResolver.normalize(any())).thenReturn("reach");
        when(reportQuery.findPosts(any(), any(), any(), any())).thenReturn(List.of());
        when(reportQuery.latestPostMetrics(any(), any(), any(), any())).thenReturn(List.of());

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
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), eq(PREV_FROM), eq(PREV_TO)))
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
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), eq(PREV_FROM), eq(PREV_TO)))
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
        when(reportQuery.accountMetricKeysPresent(eq(CLIENT), any(), eq(PREV_FROM), eq(PREV_TO)))
                .thenReturn(Set.of(
                        new PlatformMetricKey("INSTAGRAM", "ig_followers_count"),
                        new PlatformMetricKey("INSTAGRAM", "ig_total_interactions")));

        PlatformKpis ig = assembleInstagram();

        assertThat(kpi(ig, "ig_followers_count").delta()).isEqualByComparingTo("150");
        assertThat(kpi(ig, "ig_total_interactions").delta()).isEqualByComparingTo("931");
    }

    // ---- helpers ----

    private PlatformKpis assembleInstagram() {
        ReportData data = assembler.assemble(CLIENT, ReportType.SUMMARY, ReportFormat.MARKDOWN,
                FROM, TO, List.of("INSTAGRAM"), "reach");
        return data.kpis().stream().filter(k -> k.platform().equals("INSTAGRAM")).findFirst().orElseThrow();
    }

    private static Kpi kpi(PlatformKpis platform, String key) {
        return platform.metrics().stream().filter(k -> k.key().equals(key)).findFirst().orElseThrow();
    }

    /** Summary IG con seguidores (stock → latest) e interacciones (flujo → total) + alcance. */
    private static SummaryResponse igSummary(LocalDate from, LocalDate to, BigDecimal followers,
                                             BigDecimal interactions) {
        PlatformSummary ig = new PlatformSummary("INSTAGRAM", List.of(
                new SummaryMetric("ig_followers_count", "Seguidores", "count", followers, followers),
                new SummaryMetric("ig_total_interactions", "Interacciones", "count", interactions, interactions),
                new SummaryMetric("ig_reach", "Alcance", "count", interactions, interactions)),
                null, null);
        return new SummaryResponse(CLIENT, from, to, List.of(ig));
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
