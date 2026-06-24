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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.filgrama.domain.Client;
import com.filgrama.domain.SocialAccount;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
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
    private SocialAccountRepository accountRepository;
    private ReportDataAssembler assembler;

    @BeforeEach
    void setUp() {
        ClientRepository clientRepository = mock(ClientRepository.class);
        accountRepository = mock(SocialAccountRepository.class);
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
        when(reportQuery.findPosts(any(), any(), any(), any(), any())).thenReturn(List.of());
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
