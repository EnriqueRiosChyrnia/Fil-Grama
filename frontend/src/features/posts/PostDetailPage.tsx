import { useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import type { PostListItem } from '../../api/generated/model';
import { usePostReport, pointsForMetric } from '../accounts/metricsReport';
import { Card, Button, NetworkChip, TrendChart, DateRangeControl, InfoTooltip } from '../../components/ui';
import { ErrorState, LoadingState } from '../../components/layout';
import { useCatalog } from '../../lib/catalog';
import { computeRange, formatDate, type RangeDays } from '../../lib/format';
import { postMetrics, anyPostMetrics } from '../accounts/catalogMetrics';
import { PostThumb } from './PostThumb';
import { postTypeLabel, isEphemeralType } from './postKind';

interface PostNavState {
  post?: PostListItem;
  clientId?: string;
  accountId?: string;
}

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

function Badge({ children, tone = 'neutral' }: { children: React.ReactNode; tone?: 'neutral' | 'warning' }) {
  const warn = tone === 'warning';
  return (
    <span
      style={{
        fontSize: 11.5,
        fontWeight: 500,
        color: warn ? 'var(--fg-danger-fg)' : 'var(--fg-text-secondary)',
        background: warn ? 'var(--fg-danger-bg)' : 'var(--fg-gray-50)',
        border: `1px solid ${warn ? 'var(--fg-danger-bg)' : 'var(--fg-border)'}`,
        borderRadius: 6,
        padding: '3px 8px',
        lineHeight: 1.3,
      }}
    >
      {children}
    </span>
  );
}

export function PostDetailPage() {
  const { postId } = useParams();
  const id = Number(postId);
  const navigate = useNavigate();
  const catalog = useCatalog();
  const location = useLocation();
  const state = (location.state as PostNavState | null) ?? null;
  const post = state?.post;
  const platform = post?.platform ?? '';

  const [range, setRange] = useState<RangeDays>(90);
  const [metric, setMetric] = useState<string | null>(null);
  const dr = useMemo(() => computeRange(range), [range]);

  // Dirigido por catálogo: métricas de publicación de la red (o todas, si es un deep-link sin red conocida).
  const metricItems = useMemo(
    () => (platform ? postMetrics(catalog, platform) : anyPostMetrics(catalog)),
    [catalog, platform],
  );
  const activeMetric = metric ?? metricItems[0]?.key ?? null;

  const seriesQ = usePostReport(
    id,
    activeMetric ? [activeMetric] : [],
    dr,
    { enabled: Number.isFinite(id) && !!activeMetric },
  );
  const points = pointsForMetric(seriesQ.data?.data, activeMetric).map((p) => ({ x: p.date ?? '', value: p.value ?? 0 }));

  const back = () => {
    if (state?.clientId && state?.accountId) {
      navigate(`/clients/${state.clientId}/accounts/${state.accountId}/posts`);
    } else {
      history.back();
    }
  };

  return (
    <div>
      <button
        type="button"
        onClick={back}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
      >
        <span style={{ fontSize: 15 }}>‹</span>
        <span>Volver a las publicaciones</span>
      </button>

      <div style={{ display: 'flex', gap: 22, marginTop: 16, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {/* preview */}
        <div style={{ width: 300, maxWidth: '100%', flex: '0 0 auto' }}>
          <Card padding={12}>
            <PostThumb src={post?.remoteThumbnailUrl} postType={post?.postType} radius={12} />
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexWrap: 'wrap', marginTop: 12 }}>
              <NetworkChip platform={platform} long />
              <Badge>{postTypeLabel(post?.postType)}</Badge>
              {isEphemeralType(post?.postType) && <Badge tone="warning">Efímera</Badge>}
            </div>
            {post?.caption && (
              <p style={{ fontSize: 13, color: 'var(--fg-text-secondary)', lineHeight: 1.55, marginTop: 11, marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                {post.caption}
              </p>
            )}
            <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 11 }}>
              {post?.publishedAt ? `Publicada el ${formatDate(post.publishedAt)}` : 'Fecha de publicación no disponible'}
            </div>
            {post?.permalink ? (
              <Button
                variant="secondary"
                fullWidth
                style={{ marginTop: 13 }}
                onClick={() => window.open(post.permalink as string, '_blank', 'noopener,noreferrer')}
                leftIcon={
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden>
                    <path d="M5.5 2.5H2.5v9h9v-3" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M8 2.5h3.5V6M11.5 2.5 6.5 7.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                }
              >
                Abrir en {(platform && platform.charAt(0) + platform.slice(1).toLowerCase()) || 'la red'}
              </Button>
            ) : (
              !post && (
                <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 13, lineHeight: 1.5 }}>
                  Abrí esta publicación desde la grilla para ver su vista previa y el enlace original.
                </div>
              )
            )}
          </Card>
        </div>

        {/* metrics over time */}
        <div style={{ flex: 1, minWidth: 280 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, flexWrap: 'wrap' }}>
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
            <DateRangeControl value={range} onChange={setRange} />
          </div>

          <Card style={{ marginTop: 14 }} padding={20}>
            <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>
              {activeMetric ? `${catalog.displayName(activeMetric)} en el tiempo` : 'Evolución'}
            </div>
            <div style={{ marginTop: 14 }}>
              {!activeMetric ? (
                <div style={{ height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--fg-text-tertiary)', fontSize: 13 }}>
                  No hay métricas de publicación en el catálogo para mostrar.
                </div>
              ) : seriesQ.isLoading ? (
                <LoadingState message="Cargando las métricas…" minHeight={240} />
              ) : seriesQ.isError ? (
                <ErrorState error={seriesQ.error} onRetry={() => seriesQ.refetch()} minHeight={240} />
              ) : points.length === 0 ? (
                <div style={{ height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', color: 'var(--fg-text-tertiary)', fontSize: 13 }}>
                  Aún no hay datos para este rango.
                </div>
              ) : (
                <TrendChart data={points} height={240} />
              )}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
