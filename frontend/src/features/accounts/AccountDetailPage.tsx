import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useGetAccountsId,
  useGetAccountsIdPosts,
} from '../../api/generated/endpoints';
import type { AccountReportResponse, PostListItem } from '../../api/generated/model';
import { useAccountReport, pointsForMetric } from './metricsReport';
import { Card, KpiCard, TrendChart, NetworkChip, DateRangeControl, InfoTooltip, Button } from '../../components/ui';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '../../components/layout';
import { useCatalog, CONCEPT_BY_KEY, CORE_CONCEPTS, type CoreConcept } from '../../lib/catalog';
import { primaryMetricKey } from '../../lib/metrics';
import { computeRange, formatByUnit, formatDate, trendFromDelta, WIDE_RANGES, type RangeDays } from '../../lib/format';
import { accountMetrics, primaryPostMetricKey } from './catalogMetrics';
import { PostThumb } from '../posts/PostThumb';

const selectStyle: React.CSSProperties = {
  height: 30,
  borderRadius: 'var(--fg-radius-sm)',
  border: '1px solid var(--fg-border-strong)',
  background: 'var(--fg-bg-surface)',
  color: 'var(--fg-text-primary)',
  fontSize: 13,
  padding: '0 10px',
  cursor: 'pointer',
  maxWidth: 260,
};

function pctDelta(values: number[]): number | null {
  if (values.length < 2) return null;
  const first = values[0];
  const last = values[values.length - 1];
  if (!first) return null;
  return ((last - first) / Math.abs(first)) * 100;
}

function Breadcrumb({ clientId }: { clientId?: string }) {
  return (
    <button
      type="button"
      onClick={() => history.back()}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
    >
      <span style={{ fontSize: 15 }}>‹</span>
      <span>{clientId ? 'Volver al cliente' : 'Volver'}</span>
    </button>
  );
}

/**
 * KPI de un concepto CORE para esta cuenta, leído del informe compartido del
 * panel (una sola llamada :report con todas las métricas) — sin request propia.
 */
function ConceptKpi({
  report,
  loading,
  platform,
  concept,
}: {
  report: AccountReportResponse | undefined;
  loading: boolean;
  platform: string;
  concept: CoreConcept;
}) {
  const key = primaryMetricKey(concept, platform);
  const values = pointsForMetric(report, key).map((p) => p.value ?? 0);
  const meta = CONCEPT_BY_KEY[concept];
  // 'seguidores' es stock (último valor); flujos se suman en el rango.
  const value = values.length
    ? concept === 'seguidores'
      ? values[values.length - 1]
      : values.reduce((a, b) => a + b, 0)
    : null;
  return <KpiCard label={meta.label} info={meta.info} value={loading ? '…' : formatByUnit(value, meta.unit)} />;
}

export function AccountDetailPage() {
  const { clientId, accountId } = useParams();
  const id = Number(accountId);
  const navigate = useNavigate();
  const catalog = useCatalog();

  const [range, setRange] = useState<RangeDays>(30);
  const [metric, setMetric] = useState<string | null>(null);
  const dr = useMemo(() => computeRange(range), [range]);

  const accountQ = useGetAccountsId(id, { query: { enabled: Number.isFinite(id) } });
  const account = accountQ.data?.data;
  const platform = account?.platform ?? '';

  // Métricas de nivel cuenta de ESTA red (dirigido por catálogo).
  const metricItems = useMemo(() => accountMetrics(catalog, platform), [catalog, platform]);
  const activeMetric = metric ?? primaryMetricKey('alcance', platform) ?? metricItems[0]?.key ?? null;

  // Conceptos CORE visibles para esta red: sólo si su métrica existe a NIVEL
  // CUENTA en el catálogo (no inventar paridad: p.ej. TikTok no tiene alcance).
  const accountKeys = useMemo(() => new Set(metricItems.map((it) => it.key)), [metricItems]);
  const concepts = CORE_CONCEPTS.filter((c) => {
    const k = primaryMetricKey(c.key, platform);
    return !!k && accountKeys.has(k);
  });

  // Una sola llamada :report con TODAS las métricas del panel (KPIs CORE + la
  // métrica del gráfico principal), en vez de una request por métrica.
  const panelMetrics = (() => {
    const keys = new Set<string>();
    if (activeMetric) keys.add(activeMetric);
    for (const c of concepts) {
      const k = primaryMetricKey(c.key, platform);
      if (k) keys.add(k);
    }
    return [...keys];
  })();

  const seriesQ = useAccountReport(id, panelMetrics, dr, {
    enabled: Number.isFinite(id) && panelMetrics.length > 0,
  });
  const report = seriesQ.data?.data;
  const points = pointsForMetric(report, activeMetric).map((p) => ({ x: p.date ?? '', value: p.value ?? 0 }));
  const heroValue = points.length ? points[points.length - 1].value : null;
  const heroTrend = trendFromDelta(pctDelta(points.map((p) => p.value)));

  // Top posts del rango, rankeados por la métrica principal de publicación de la red.
  // Orden cronológico = campo `published_at` (snake_case, lo que acepta el backend);
  // por métrica = la propia key del catálogo.
  const postSortKey = primaryPostMetricKey(catalog, platform);
  const postSort = postSortKey ? `${postSortKey},desc` : 'published_at,desc';
  const postsQ = useGetAccountsIdPosts(
    id,
    { from: dr.from, to: dr.to, page: 0, size: 6, sort: postSort },
    { query: { enabled: Number.isFinite(id) } },
  );
  const posts = postsQ.data?.data?.content ?? [];

  const openPost = (p: PostListItem) =>
    navigate(`/posts/${p.id}`, { state: { post: p, clientId, accountId } });

  if (accountQ.isLoading) {
    return (
      <div>
        <Breadcrumb clientId={clientId} />
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 14 }}>
          <Skeleton width={44} height={44} radius={10} />
          <Skeleton width={180} height={20} />
        </div>
        <Card style={{ marginTop: 18 }} padding={22}>
          <LoadingState message="Cargando la cuenta…" />
        </Card>
      </div>
    );
  }
  if (accountQ.isError) {
    return (
      <div>
        <Breadcrumb clientId={clientId} />
        <ErrorState error={accountQ.error} onRetry={() => accountQ.refetch()} />
      </div>
    );
  }

  const accountName = account?.handle || account?.displayName || `Cuenta ${id}`;
  const status = (account?.status ?? '').toUpperCase();
  const connected = status === 'CONNECTED';

  return (
    <div>
      <Breadcrumb clientId={clientId} />

      {/* header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, marginTop: 14, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 13 }}>
          <div style={{ width: 44, height: 44, borderRadius: 10, background: 'var(--fg-blue-50)', color: 'var(--fg-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 15, fontWeight: 600 }}>
            {accountName.replace('@', '').slice(0, 2).toUpperCase()}
          </div>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
              {accountName}
            </h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
              <NetworkChip platform={platform} long />
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: connected ? 'var(--fg-success-fg)' : 'var(--fg-text-tertiary)' }}>
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: connected ? 'var(--fg-success-fg)' : 'var(--fg-text-tertiary)' }} />
                {connected ? 'Conectada' : 'Sin conexión'}
              </span>
            </div>
          </div>
        </div>
        <Button variant="secondary" onClick={() => navigate(`/clients/${clientId}/accounts/${accountId}/posts`)}>
          Ver todas las publicaciones
        </Button>
      </div>

      {/* controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, marginTop: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.6px' }}>Métrica</span>
          {metricItems.length > 0 && activeMetric ? (
            <>
              <select aria-label="Métrica" value={activeMetric} onChange={(e) => setMetric(e.target.value)} style={selectStyle}>
                {metricItems.map((it) => (
                  <option key={it.key} value={it.key}>
                    {catalog.displayName(it.key)}
                  </option>
                ))}
              </select>
              <InfoTooltip title={catalog.displayName(activeMetric)}>{catalog.description(activeMetric)}</InfoTooltip>
            </>
          ) : (
            <span style={{ fontSize: 13, color: 'var(--fg-text-tertiary)' }}>Sin métricas en el catálogo</span>
          )}
        </div>
        <DateRangeControl value={range} onChange={setRange} options={WIDE_RANGES} />
      </div>

      {/* CORE concept KPIs (consistencia visual entre redes) */}
      {concepts.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 16, marginTop: 18 }}>
          {concepts.map((c) => (
            <ConceptKpi key={c.key} report={report} loading={seriesQ.isLoading} platform={platform} concept={c.key} />
          ))}
        </div>
      )}

      {/* main metric chart */}
      <KpiCard
        hero
        label={activeMetric ? catalog.displayName(activeMetric) : 'Tendencia'}
        info={activeMetric ? catalog.description(activeMetric) : undefined}
        value={seriesQ.isLoading ? '…' : formatByUnit(heroValue, catalog.unit(activeMetric))}
        trend={points.length ? heroTrend : null}
        caption={`Último valor · ${dr.label}`}
        headerRight={dr.label}
      >
        <div style={{ marginTop: 18 }}>
          {!activeMetric ? (
            <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--fg-text-tertiary)', fontSize: 13 }}>
              Esta red no expone métricas de cuenta en el catálogo.
            </div>
          ) : seriesQ.isLoading ? (
            <LoadingState message="Cargando la tendencia…" minHeight={200} />
          ) : seriesQ.isError ? (
            <ErrorState error={seriesQ.error} onRetry={() => seriesQ.refetch()} minHeight={200} />
          ) : points.length === 0 ? (
            <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--fg-text-tertiary)', fontSize: 13 }}>
              Aún no hay datos para este rango.
            </div>
          ) : (
            <TrendChart data={points} />
          )}
        </div>
      </KpiCard>

      {/* top posts */}
      <div style={{ marginTop: 26 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)', margin: 0 }}>Mejores publicaciones</h2>
          <button
            type="button"
            onClick={() => navigate(`/clients/${clientId}/accounts/${accountId}/posts`)}
            style={{ fontSize: 13, color: 'var(--fg-primary)', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 500 }}
          >
            Ver todas →
          </button>
        </div>

        <div style={{ marginTop: 14 }}>
          {postsQ.isLoading ? (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 14 }}>
              {[0, 1, 2].map((i) => (
                <Card key={i} padding={10}>
                  <Skeleton width="100%" height={120} radius={10} />
                  <Skeleton width="80%" height={12} style={{ marginTop: 10 }} />
                </Card>
              ))}
            </div>
          ) : postsQ.isError ? (
            <ErrorState error={postsQ.error} onRetry={() => postsQ.refetch()} minHeight={160} />
          ) : posts.length === 0 ? (
            <EmptyState
              title="Aún no hay publicaciones en este rango"
              description="Cuando esta cuenta publique (o ampliés el rango), vas a ver acá sus mejores piezas."
            />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 14 }}>
              {posts.map((p) => (
                <Card key={p.id} interactive padding={10} onClick={() => openPost(p)}>
                  <PostThumb src={p.remoteThumbnailUrl} postType={p.postType} />
                  <div style={{ marginTop: 9, fontSize: 12.5, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {p.caption || 'Sin descripción'}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8, marginTop: 6 }}>
                    <span style={{ fontSize: 11.5, color: 'var(--fg-text-tertiary)' }}>{formatDate(p.publishedAt)}</span>
                    {postSortKey && p.sortValue != null && (
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                        {formatByUnit(p.sortValue, catalog.unit(postSortKey))}
                      </span>
                    )}
                  </div>
                </Card>
              ))}
            </div>
          )}
          {postSortKey && posts.length > 0 && (
            <div style={{ fontSize: 11.5, color: 'var(--fg-text-tertiary)', marginTop: 10 }}>
              Ordenadas por {catalog.displayName(postSortKey)} en {dr.label.toLowerCase()}.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
