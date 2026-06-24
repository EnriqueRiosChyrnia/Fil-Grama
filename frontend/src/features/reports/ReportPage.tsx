import { useState, type CSSProperties, type ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  useGetClientsClientIdAccounts,
  postClientsClientIdReportsPreview,
  postClientsClientIdReports,
} from '../../api/generated/endpoints';
import {
  PreviewReportRequestReportType,
  GenerateReportRequestFormat,
} from '../../api/generated/model';
import type {
  AccountResponse,
  ReportData,
  PlatformKpis,
  PostGroup,
  ReportPost,
  PreviewReportRequest,
} from '../../api/generated/model';
import {
  Button,
  Card,
  SegmentedControl,
  NetworkChip,
  InfoTooltip,
  DateRangeControl,
  CompareBars,
  networkLabel,
  type CompareDatum,
} from '../../components/ui';
import { EmptyState, ErrorState } from '../../components/layout';
import { PostThumb } from '../posts/PostThumb';
import { ApiError } from '../../lib/api';
import { qk } from '../../lib/query/keys';
import {
  computeRange,
  formatByUnit,
  formatCompact,
  formatDate,
  formatPercent,
  type RangeDays,
} from '../../lib/format';
import { downloadReport } from './reportDownload';

/**
 * Reporte = vista en pantalla + exportar (reconstrucción sobre `:preview`).
 *
 * El reporte se VE en pantalla (POST /clients/{id}/reports:preview → ReportData) y
 * se refresca en vivo con los controles (tipo, período, redes, ranking). El ARCHIVO
 * se genera sólo al tocar Exportar (POST /reports → downloadUrl → descarga autenticada).
 * Diseño: "Reporte (rediseño)" (Claude Design) cruzado con el contrato ReportData.
 */

/** `rankBy` lógico del backend (alias) → etiqueta humana. Default: reach. */
const RANK_OPTIONS = [
  { value: 'reach', label: 'Alcance' },
  { value: 'views', label: 'Reproducciones' },
  { value: 'engagement', label: 'Interacciones' },
  { value: 'likes', label: 'Me gusta' },
];

const PAPER_MAX = 860;

function trendStyle(up: boolean, size: number): CSSProperties {
  return { fontSize: size, fontWeight: 500, marginTop: 3, color: up ? 'var(--fg-primary)' : 'var(--fg-text-tertiary)' };
}

/**
 * "Sin comparación": no hay período anterior (cuenta nueva) → el delta llegó null.
 * null ≠ 0. Nunca mostramos ▲0 ni el valor como si fuera ganancia; sólo avisamos
 * que no hay con qué comparar. El valor en sí siempre se muestra aparte.
 */
function NoComparison({ size = 12 }: { size?: number }) {
  return (
    <div
      title="No hay un período anterior con que comparar"
      style={{ fontSize: size, fontWeight: 500, marginTop: 3, color: 'var(--fg-text-tertiary)' }}
    >
      — sin comparación
    </div>
  );
}

/** Variación absoluta (misma unidad que el valor): "▲ 1,2k" / "▼ 320". null = sin comparación. */
function TrendNum({ delta, unit, size = 12 }: { delta?: number | null; unit?: string | null; size?: number }) {
  if (delta == null) return <NoComparison size={size} />;
  if (delta === 0) return null;
  const up = delta > 0;
  return <div style={trendStyle(up, size)}>{`${up ? '▲' : '▼'} ${formatByUnit(Math.abs(delta), unit ?? undefined)}`}</div>;
}

/** Variación porcentual: "▲ 12%". null = sin comparación. */
function TrendPct({ pct, size = 12 }: { pct?: number | null; size?: number }) {
  if (pct == null) return <NoComparison size={size} />;
  if (pct === 0) return null;
  const up = pct > 0;
  return <div style={trendStyle(up, size)}>{`${up ? '▲' : '▼'} ${formatPercent(Math.abs(pct))}`}</div>;
}

function thumbSrc(p: ReportPost): string | undefined {
  return p.thumbnailDataUri || p.thumbnailUrl || undefined;
}

function postMeta(p: ReportPost): string {
  return [p.displayType || p.postType, formatDate(p.publishedAtLocal || p.publishedAt)].filter(Boolean).join(' · ');
}

function postMetric(p: ReportPost): string {
  const v = p.metricValue != null ? formatCompact(p.metricValue) : '—';
  return p.metricName ? `${v} ${p.metricName.toLowerCase()}` : v;
}

export function ReportPage() {
  const { clientId } = useParams();
  const id = Number(clientId);

  const [reportType, setReportType] = useState<PreviewReportRequest['reportType']>(
    PreviewReportRequestReportType.SUMMARY,
  );
  const [range, setRange] = useState<RangeDays>(30);
  const [platforms, setPlatforms] = useState<string[] | null>(null);
  const [rankBy, setRankBy] = useState('reach');
  const [exportingFmt, setExportingFmt] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const dr = computeRange(range);

  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const connectedPlatforms = Array.from(
    new Set(
      accounts
        .filter((a) => (a.status ?? '').toUpperCase() === 'CONNECTED')
        .map((a) => (a.platform ?? '').toUpperCase())
        .filter(Boolean),
    ),
  );
  const allPlatforms = Array.from(new Set(accounts.map((a) => (a.platform ?? '').toUpperCase()).filter(Boolean)));
  const platformOptions = connectedPlatforms.length ? connectedPlatforms : allPlatforms;

  const selectedPlatforms = [...(platforms ?? platformOptions)].sort();
  const noAccounts = accountsQ.isSuccess && accounts.length === 0;
  const noNetworks = !noAccounts && selectedPlatforms.length === 0;

  const previewEnabled = Number.isFinite(id) && accountsQ.isSuccess && !noAccounts && !noNetworks;
  const previewQ = useQuery({
    queryKey: qk.feature('reportPreview', id, reportType, dr.from, dr.to, selectedPlatforms, rankBy),
    queryFn: ({ signal }) =>
      postClientsClientIdReportsPreview(
        id,
        {
          reportType,
          from: dr.from,
          to: dr.to,
          platforms: selectedPlatforms.length ? selectedPlatforms : undefined,
          rankBy,
        },
        { signal },
      ).then((r) => r.data as ReportData),
    enabled: previewEnabled,
  });

  const data = previewQ.data;
  const kpis: PlatformKpis[] = data?.kpis ?? [];
  const topPosts: ReportPost[] = data?.topPosts?.length ? data.topPosts : data?.postHighlights?.top ?? [];
  const groups: PostGroup[] = [...(data?.postGroups ?? []), ...(data?.storyGroups ?? [])];
  const hasData =
    kpis.some((k) => (k.metrics?.length ?? 0) > 0 || k.reach?.current != null || k.engagementRate != null) ||
    topPosts.length > 0 ||
    groups.length > 0;
  const canExport = previewQ.isSuccess && hasData;

  const togglePlatform = (p: string) => {
    setExportError(null);
    setPlatforms((prev) => {
      const cur = prev ?? platformOptions;
      return cur.includes(p) ? cur.filter((x) => x !== p) : [...cur, p];
    });
  };

  const onExport = async (format: GenerateReportRequestFormat) => {
    setExportError(null);
    setExportingFmt(format);
    try {
      const res = await postClientsClientIdReports(id, {
        reportType,
        format,
        from: dr.from,
        to: dr.to,
        platforms: selectedPlatforms.length ? selectedPlatforms : undefined,
        rankBy,
      });
      const report = res.data;
      if (!report?.downloadUrl) throw new Error('sin downloadUrl');
      const ext = format === GenerateReportRequestFormat.PDF ? 'pdf' : 'md';
      const fallback = `reporte-${String(reportType).toLowerCase()}-${dr.from}_a_${dr.to}.${ext}`;
      await downloadReport(report.downloadUrl, fallback);
    } catch (e) {
      setExportError(e instanceof ApiError ? e.humanMessage : 'No pudimos exportar el archivo. Probá de nuevo.');
    } finally {
      setExportingFmt(null);
    }
  };

  const rankLabel = (RANK_OPTIONS.find((r) => r.value === rankBy)?.label ?? rankBy).toLowerCase();

  // ---- account-less: nada que reportar ----
  if (noAccounts) {
    return (
      <EmptyState
        title="Este cliente todavía no tiene cuentas"
        description="Cuando haya redes conectadas y datos capturados vas a poder ver y exportar reportes acá."
      />
    );
  }

  return (
    <div>
      {/* ===== controles + exportar ===== */}
      <Card padding={16}>
        <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 22, flexWrap: 'wrap' }}>
            <Field label="Tipo">
              <SegmentedControl<PreviewReportRequest['reportType']>
                ariaLabel="Tipo de reporte"
                value={reportType}
                onChange={(v) => {
                  setExportError(null);
                  setReportType(v);
                }}
                options={[
                  { value: PreviewReportRequestReportType.SUMMARY, label: 'Resumen' },
                  { value: PreviewReportRequestReportType.EXTENDED, label: 'Extendido' },
                ]}
              />
            </Field>

            <Field label="Período">
              <DateRangeControl value={range} onChange={setRange} />
            </Field>

            <Field label="Redes" info="Sin selección se incluyen todas las redes conectadas del cliente.">
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {platformOptions.map((p) => {
                  const checked = selectedPlatforms.includes(p);
                  return (
                    <button
                      key={p}
                      type="button"
                      role="checkbox"
                      aria-checked={checked}
                      onClick={() => togglePlatform(p)}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '7px 11px',
                        borderRadius: 'var(--fg-radius)',
                        border: `1px solid ${checked ? 'var(--fg-primary)' : 'var(--fg-border-strong)'}`,
                        background: checked ? 'var(--fg-blue-50)' : 'var(--fg-bg-surface)',
                        color: 'var(--fg-text-primary)',
                        fontSize: 13,
                        cursor: 'pointer',
                      }}
                    >
                      <NetworkChip platform={p} long />
                      <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>{checked ? '✓' : '+'}</span>
                    </button>
                  );
                })}
              </div>
            </Field>

            <Field label="Ranking por" info="Métrica con la que se rankean las publicaciones destacadas.">
              <SegmentedControl<string>
                ariaLabel="Ranking por"
                value={rankBy}
                onChange={(v) => {
                  setExportError(null);
                  setRankBy(v);
                }}
                options={RANK_OPTIONS}
              />
            </Field>
          </div>

          {/* exportar */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span title={canExport ? undefined : 'Disponible cuando haya datos en el rango'}>
              <Button
                onClick={() => onExport(GenerateReportRequestFormat.PDF)}
                loading={exportingFmt === GenerateReportRequestFormat.PDF}
                disabled={!canExport || exportingFmt != null}
                leftIcon={
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden>
                    <path d="M7 1v8M4 6l3 3 3-3M2 12h10" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                }
              >
                Exportar PDF
              </Button>
            </span>
            <span title={canExport ? undefined : 'Disponible cuando haya datos en el rango'}>
              <Button
                variant="secondary"
                onClick={() => onExport(GenerateReportRequestFormat.MARKDOWN)}
                loading={exportingFmt === GenerateReportRequestFormat.MARKDOWN}
                disabled={!canExport || exportingFmt != null}
              >
                Markdown
              </Button>
            </span>
          </div>
        </div>

        {exportError && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--fg-danger-fg)' }}>{exportError}</div>}
      </Card>

      {/* ===== área del documento ===== */}
      <div
        style={{
          background: 'var(--fg-bg-muted)',
          borderRadius: 'var(--fg-radius-lg)',
          padding: '26px 16px',
          marginTop: 16,
        }}
      >
        {noNetworks ? (
          <Paper>
            <EmptyMsg
              title="Elegí al menos una red"
              description="Activá una o más redes en los controles de arriba para ver el reporte."
            />
          </Paper>
        ) : previewQ.isLoading ? (
          <Paper>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '70px 24px' }}>
              <span
                style={{
                  width: 22,
                  height: 22,
                  border: '2px solid var(--fg-border-strong)',
                  borderTopColor: 'var(--fg-primary)',
                  borderRadius: '50%',
                  animation: 'fg-spin .7s linear infinite',
                }}
              />
              <style>{`@keyframes fg-spin{to{transform:rotate(360deg)}}`}</style>
              <span style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>Armando el reporte…</span>
            </div>
          </Paper>
        ) : previewQ.isError ? (
          <Paper>
            <ErrorState error={previewQ.error} onRetry={() => previewQ.refetch()} />
          </Paper>
        ) : !hasData ? (
          <Paper>
            <EmptyMsg
              title="Aún no hay datos para este rango"
              description="No registramos publicaciones ni métricas para el período elegido. Ampliá el rango o sumá redes para armar el reporte."
              action={
                range !== 90 ? (
                  <Button onClick={() => setRange(90)}>Ampliar rango</Button>
                ) : undefined
              }
            />
          </Paper>
        ) : (
          <Paper>
            <ReportDocument data={data!} reportType={reportType} dr={dr} rankLabel={rankLabel} fallbackPlatforms={selectedPlatforms} />
          </Paper>
        )}
      </div>
    </div>
  );
}

/* ---------------------------------- piezas ---------------------------------- */

function Field({ label, info, children }: { label: string; info?: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--fg-text-tertiary)' }}>
        {label}
        {info && (
          <InfoTooltip title={label} size={13}>
            {info}
          </InfoTooltip>
        )}
      </span>
      {children}
    </div>
  );
}

/** Hoja blanca centrada (el "documento"). */
function Paper({ children }: { children: ReactNode }) {
  return (
    <div style={{ maxWidth: PAPER_MAX, margin: '0 auto' }}>
      <Card padding={0} style={{ overflow: 'hidden', boxShadow: 'var(--fg-shadow-md)' }}>
        {children}
      </Card>
    </div>
  );
}

function EmptyMsg({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div style={{ padding: '64px 32px' }}>
      <EmptyState title={title} description={description} action={action} />
    </div>
  );
}

function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 11,
        textTransform: 'uppercase',
        letterSpacing: '.7px',
        color: 'var(--fg-text-tertiary)',
        marginBottom: 12,
      }}
    >
      {children}
    </div>
  );
}

function ReportDocument({
  data,
  reportType,
  dr,
  rankLabel,
  fallbackPlatforms,
}: {
  data: ReportData;
  reportType: PreviewReportRequest['reportType'];
  dr: { from: string; to: string };
  rankLabel: string;
  fallbackPlatforms: string[];
}) {
  const isSummary = reportType === PreviewReportRequestReportType.SUMMARY;
  const clientName = data.client?.name ?? 'Cliente';
  const from = data.period?.from ?? dr.from;
  const to = data.period?.to ?? dr.to;
  const coverNets = (data.platforms?.length ? data.platforms : fallbackPlatforms).map((p) => networkLabel(p));

  const kpis = data.kpis ?? [];
  const topPosts = data.topPosts?.length ? data.topPosts : data.postHighlights?.top ?? [];
  const groups = [...(data.postGroups ?? []), ...(data.storyGroups ?? [])].filter((g) => (g.posts?.length ?? 0) > 0);

  const reachData: CompareDatum[] = kpis
    .filter((k) => k.reach?.current != null)
    .map((k) => ({ label: networkLabel(k.platform ?? ''), value: k.reach!.current as number }));

  return (
    <div>
      {/* cover */}
      <div style={{ padding: '34px 40px 26px', borderBottom: '1px solid var(--fg-border)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
            <span
              style={{
                width: 24,
                height: 24,
                borderRadius: 7,
                background: 'var(--fg-primary)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <svg width="13" height="13" viewBox="0 0 15 15" fill="none" aria-hidden>
                <polyline points="1,11 5,7 8,9 14,2" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
            <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--fg-text-primary)' }}>Fil-Grama</span>
          </div>
          <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>
            {isSummary ? 'Reporte · Resumen' : 'Reporte · Extendido'}
          </span>
        </div>
        <div style={{ marginTop: 22 }}>
          <div style={{ fontSize: 30, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.5px' }}>{clientName}</div>
          <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', marginTop: 6 }}>
            {formatDate(from)} – {formatDate(to)}
          </div>
          {coverNets.length > 0 && (
            <div style={{ display: 'flex', gap: 7, marginTop: 13, flexWrap: 'wrap' }}>
              {coverNets.map((n) => (
                <span
                  key={n}
                  style={{
                    fontSize: 12,
                    color: 'var(--fg-text-secondary)',
                    background: 'var(--fg-bg-page)',
                    border: '1px solid var(--fg-border)',
                    borderRadius: 7,
                    padding: '4px 11px',
                  }}
                >
                  {n}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* KPIs por red */}
      {kpis.length > 0 && (
        <div style={{ padding: '26px 40px 0' }}>
          <SectionTitle>KPIs por red · variación vs. período anterior</SectionTitle>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {kpis.map((k) => (
              <NetworkKpiCard key={k.platform} kpi={k} />
            ))}
          </div>
        </div>
      )}

      {/* evolución del alcance */}
      {reachData.length > 0 && (
        <div style={{ padding: '26px 40px 0' }}>
          <SectionTitle>Evolución del alcance · este período vs. anterior</SectionTitle>
          <Card padding={16}>
            <CompareBars data={reachData} horizontal height={Math.max(120, reachData.length * 54)} formatValue={(n) => formatByUnit(n, 'count')} />
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginTop: 12 }}>
              {kpis
                .filter((k) => k.reach?.current != null)
                .map((k) => (
                  <div key={k.platform} style={{ fontSize: 12, color: 'var(--fg-text-secondary)' }}>
                    <span style={{ fontWeight: 500, color: 'var(--fg-text-primary)' }}>{networkLabel(k.platform ?? '')}</span>{' '}
                    {formatByUnit(k.reach?.current, 'count')}
                    {k.reach?.deltaPct != null ? (
                      <span style={{ color: (k.reach.deltaPct ?? 0) >= 0 ? 'var(--fg-primary)' : 'var(--fg-text-tertiary)' }}>
                        {' '}
                        ({(k.reach.deltaPct ?? 0) >= 0 ? '▲' : '▼'} {formatPercent(Math.abs(k.reach.deltaPct ?? 0))})
                      </span>
                    ) : (
                      <span style={{ color: 'var(--fg-text-tertiary)' }}> (sin comparación)</span>
                    )}
                    {k.reach?.previous != null && (
                      <span style={{ color: 'var(--fg-text-tertiary)' }}> · antes {formatByUnit(k.reach.previous, 'count')}</span>
                    )}
                  </div>
                ))}
            </div>
          </Card>
        </div>
      )}

      {/* RESUMEN: top 3 */}
      {isSummary && topPosts.length > 0 && (
        <div style={{ padding: '26px 40px 34px' }}>
          <SectionTitle>Top {Math.min(3, topPosts.length)} publicaciones · por {rankLabel}</SectionTitle>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 14 }}>
            {topPosts.slice(0, 3).map((p, i) => (
              <PostTile key={p.id ?? i} post={p} />
            ))}
          </div>
        </div>
      )}

      {/* EXTENDIDO: grilla por tipo */}
      {!isSummary && groups.length > 0 && (
        <div style={{ padding: '26px 40px 34px' }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--fg-text-primary)' }}>Top publicaciones por tipo</div>
          <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', margin: '3px 0 16px' }}>
            Ordenadas por {rankLabel} dentro de cada formato.
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
            {groups.map((g, gi) => (
              <div key={`${g.platform}-${g.displayType}-${gi}`}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 11 }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                    <NetworkChip platform={g.platform} />
                    {g.displayType || 'Publicaciones'}
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--fg-text-tertiary)' }}>
                    {g.posts?.length ?? 0} en el período
                  </span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 12 }}>
                  {(g.posts ?? []).map((p, i) => (
                    <PostTile key={p.id ?? i} post={p} compact />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* narrativa (IA) — en v1 siempre null; render mínimo sin librerías */}
      {data.narrativeMd && data.narrativeMd.trim() && (
        <div style={{ padding: '4px 40px 34px' }}>
          <SectionTitle>Análisis del mes</SectionTitle>
          <MarkdownBlock md={data.narrativeMd} />
        </div>
      )}
    </div>
  );
}

function NetworkKpiCard({ kpi }: { kpi: PlatformKpis }) {
  const metrics = kpi.metrics ?? [];
  return (
    <div style={{ border: '1px solid var(--fg-border)', borderRadius: 12, padding: '16px 18px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 13 }}>
        <NetworkChip platform={kpi.platform} />
        <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{networkLabel(kpi.platform ?? '')}</span>
      </div>

      {metrics.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 16 }}>
          {metrics.map((m, i) => (
            <div key={m.key ?? i}>
              <div style={{ fontSize: 11.5, color: 'var(--fg-text-secondary)' }}>{m.displayName ?? m.key}</div>
              <div style={{ fontSize: 21, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 5, letterSpacing: '-.3px' }}>
                {formatByUnit(m.value, m.unit)}
              </div>
              <TrendNum delta={m.delta} unit={m.unit} />
            </div>
          ))}
        </div>
      )}

      {/* derivados del contrato: engagement, crecimiento de seguidores, alcance vs anterior */}
      {(kpi.engagementRate != null || kpi.followerGrowth != null || kpi.reach?.current != null) && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 22, marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--fg-border)' }}>
          {kpi.engagementRate != null && (
            <div>
              <div style={{ fontSize: 11.5, color: 'var(--fg-text-secondary)' }}>Engagement</div>
              <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 4 }}>
                {formatByUnit(kpi.engagementRate, 'percent')}
              </div>
            </div>
          )}
          {kpi.followerGrowth != null && (
            <div>
              <div style={{ fontSize: 11.5, color: 'var(--fg-text-secondary)' }}>Crecimiento de seguidores</div>
              <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 4 }}>
                {kpi.followerGrowth > 0 ? '+' : ''}
                {formatByUnit(kpi.followerGrowth, 'count')}
              </div>
            </div>
          )}
          {kpi.reach?.current != null && (
            <div>
              <div style={{ fontSize: 11.5, color: 'var(--fg-text-secondary)' }}>Alcance vs. anterior</div>
              <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 4 }}>
                {formatByUnit(kpi.reach?.current, 'count')}
              </div>
              <TrendPct pct={kpi.reach?.deltaPct} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function PostTile({ post, compact = false }: { post: ReportPost; compact?: boolean }) {
  return (
    <div style={{ border: '1px solid var(--fg-border)', borderRadius: 10, overflow: 'hidden' }}>
      <PostThumb src={thumbSrc(post)} postType={post.displayType || post.postType} radius={0} />
      <div style={{ padding: compact ? '8px 9px' : '11px 12px' }}>
        <div style={{ fontSize: 11, color: 'var(--fg-text-tertiary)' }}>{postMeta(post)}</div>
        <div style={{ fontSize: compact ? 12 : 14, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 4 }}>
          {postMetric(post)}
        </div>
      </div>
    </div>
  );
}

/** Render mínimo de Markdown sin dependencias (títulos `#` + párrafos). */
function MarkdownBlock({ md }: { md: string }) {
  const blocks = md.split(/\n\s*\n/).map((b) => b.trim()).filter(Boolean);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {blocks.map((b, i) => {
        if (b.startsWith('### ')) return <h4 key={i} style={{ margin: 0, fontSize: 14, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{b.slice(4)}</h4>;
        if (b.startsWith('## ')) return <h3 key={i} style={{ margin: 0, fontSize: 15, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{b.slice(3)}</h3>;
        if (b.startsWith('# ')) return <h3 key={i} style={{ margin: 0, fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{b.slice(2)}</h3>;
        return <p key={i} style={{ margin: 0, fontSize: 13.5, lineHeight: 1.6, color: 'var(--fg-text-secondary)', whiteSpace: 'pre-wrap' }}>{b}</p>;
      })}
    </div>
  );
}
