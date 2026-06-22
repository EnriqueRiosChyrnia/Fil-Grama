import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  useGetClientsId,
  useGetClientsClientIdAccounts,
  useGetClientsClientIdSummary,
  useGetAccountsIdMetrics,
} from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import {
  Button,
  Card,
  KpiCard,
  NetworkAccountSelect,
  NetworkChip,
  DateRangeControl,
  TrendChart,
  type NetworkAccountValue,
  type AccountLike,
} from '../../components/ui';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '../../components/layout';
import { useCatalog, CONCEPT_BY_KEY } from '../../lib/catalog';
import { primaryMetricKey } from '../../lib/metrics';
import { computeRange, formatByUnit, type RangeDays } from '../../lib/format';
import { heroFromSummary } from './clientMetrics';

function ClockIcon() {
  return (
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="13" r="8" stroke="var(--fg-primary)" strokeWidth="1.7" />
      <path d="M12 9.5V13l2.4 1.6" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9 3.5h6" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

function Breadcrumb() {
  return (
    <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}>
      <span style={{ fontSize: 15 }}>‹</span>
      <span>Clientes</span>
    </Link>
  );
}

export function ClientDashboardPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();
  const catalog = useCatalog();

  const [range, setRange] = useState<RangeDays>(30);
  const [sel, setSel] = useState<NetworkAccountValue>({ platform: 'ALL', accountId: 'ALL' });
  const dr = useMemo(() => computeRange(range), [range]);

  const detailQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const summaryQ = useGetClientsClientIdSummary(
    id,
    { from: dr.from, to: dr.to, platform: sel.platform === 'ALL' ? undefined : sel.platform },
    { query: { enabled: Number.isFinite(id) } },
  );

  const detail = detailQ.data?.data;
  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const summary = summaryQ.data?.data;

  // Cuentas normalizadas (id/platform definidos) para el selector cascada.
  const accountItems: AccountLike[] = accounts
    .filter((a) => a.id != null && a.platform)
    .map((a) => ({ id: a.id as number, platform: a.platform as string, handle: a.handle, displayName: a.displayName, status: a.status }));

  const connected = accounts.filter((a) => (a.status ?? '').toUpperCase() === 'CONNECTED');
  const allError =
    accounts.length > 0 && accounts.every((a) => ['ERROR', 'DISCONNECTED'].includes((a.status ?? '').toUpperCase()));

  const hasData = (summary?.platforms ?? []).some(
    (p) => (p.metrics?.length ?? 0) > 0 || p.engagementRate != null || p.followerGrowth != null,
  );

  // Tendencia: cuenta primaria (la seleccionada o la primera conectada) + métrica del catálogo.
  const primary =
    sel.accountId !== 'ALL' ? accounts.find((a) => a.id === sel.accountId) : connected[0];
  const trendMetric = primary?.platform ? primaryMetricKey('alcance', primary.platform) ?? undefined : undefined;
  const trendQ = useGetAccountsIdMetrics(
    primary?.id ?? 0,
    { metric: trendMetric ?? '', from: dr.from, to: dr.to, granularity: 'day' },
    { query: { enabled: !!primary?.id && !!trendMetric && hasData } },
  );
  const trendPoints = (trendQ.data?.data?.points ?? []).map((p) => ({ x: p.capturedAt ?? '', value: p.value ?? 0 }));

  if (detailQ.isLoading) {
    return (
      <div>
        <Breadcrumb />
        <div style={{ display: 'flex', gap: 14, alignItems: 'center', marginTop: 14 }}>
          <Skeleton width={48} height={48} radius={11} />
          <Skeleton width={200} height={20} />
        </div>
        <Card style={{ marginTop: 18 }} padding={22}>
          <LoadingState message="Cargando el dashboard…" />
        </Card>
      </div>
    );
  }
  if (detailQ.isError) {
    return (
      <div>
        <Breadcrumb />
        <ErrorState error={detailQ.error} onRetry={() => detailQ.refetch()} />
      </div>
    );
  }

  const canReport = hasData;

  return (
    <div>
      <Breadcrumb />

      {/* client header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, marginTop: 14, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{ width: 48, height: 48, borderRadius: 11, background: 'var(--fg-blue-50)', color: 'var(--fg-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 17, fontWeight: 600 }}>
            {(detail?.name ?? '?').slice(0, 2).toUpperCase()}
          </div>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
              {detail?.name}
            </h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6 }}>
              {accounts.length === 0 ? (
                <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>Sin redes conectadas</span>
              ) : (
                Array.from(new Set(accounts.map((a) => a.platform))).map((p) => <NetworkChip key={p} platform={p} long />)
              )}
            </div>
          </div>
        </div>
        <Button
          disabled={!canReport}
          onClick={() => navigate(`/clients/${id}/report`)}
          title={canReport ? undefined : 'Disponible cuando haya datos'}
          leftIcon={
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden>
              <path d="M3 1.5h6l3 3v9H3z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
              <path d="M9 1.5v3h3" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
            </svg>
          }
        >
          Generar reporte
        </Button>
      </div>

      {/* controls */}
      {accounts.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, marginTop: 20, flexWrap: 'wrap' }}>
          <NetworkAccountSelect accounts={accountItems} value={sel} onChange={setSel} />
          <DateRangeControl value={range} onChange={setRange} />
        </div>
      )}

      {/* body */}
      <div style={{ marginTop: 18 }}>
        {accounts.length === 0 ? (
          <EmptyState
            icon={<ClockIcon />}
            title="Este cliente todavía no tiene redes conectadas"
            description="Conectá Instagram, Facebook o TikTok para empezar a capturar métricas. El alta y la conexión se hacen desde el flujo de Clientes."
          />
        ) : allError ? (
          <EmptyState
            tone="warning"
            icon={
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
                <path d="M10.3 4.2 2.9 17.1A2 2 0 0 0 4.6 20h14.8a2 2 0 0 0 1.7-2.9L13.7 4.2a2 2 0 0 0-3.4 0Z" stroke="var(--fg-danger-line)" strokeWidth="1.7" strokeLinejoin="round" />
                <path d="M12 9.5v3.6" stroke="var(--fg-danger-line)" strokeWidth="1.7" strokeLinecap="round" />
                <circle cx="12" cy="16.3" r="1" fill="var(--fg-danger-line)" />
              </svg>
            }
            title="No estamos recibiendo datos de este cliente"
            description="Perdimos el acceso a todas sus cuentas, así que no podemos capturar métricas. Es un problema de conexión, no de datos. Reconectá las cuentas para reanudar la captura."
            action={<Button onClick={() => navigate(`/clients/${id}/reconnect`)}>Ir a reconexión</Button>}
          >
            <AccountList accounts={accounts} />
          </EmptyState>
        ) : !hasData ? (
          <EmptyState
            icon={<ClockIcon />}
            title="Conectamos las cuentas. Estamos juntando los primeros datos."
            description="Las primeras métricas aparecen tras la próxima captura diaria. No tenés que hacer nada: el job corre solo, una vez al día por cuenta."
          >
            <AccountList accounts={accounts} />
            <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', marginTop: 14 }}>
              Mientras tanto podés revisar otros clientes; te avisamos cuando lleguen los primeros datos.
            </div>
          </EmptyState>
        ) : (
          <Dashboard
            summary={summary}
            trendPoints={trendPoints}
            trendLoading={trendQ.isLoading}
            trendMetricLabel={catalog.displayName(trendMetric)}
            rangeLabel={dr.label}
          />
        )}
      </div>
    </div>
  );
}

/** Lista "Cuentas conectadas" para los estados vacíos. */
function AccountList({ accounts }: { accounts: AccountResponse[] }) {
  return (
    <div style={{ width: '100%', maxWidth: 580, border: '1px solid var(--fg-border)', borderRadius: 12, marginTop: 24, overflow: 'hidden', textAlign: 'left' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 16px', borderBottom: '1px solid #F0F3F7', background: '#FBFCFD' }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--fg-text-secondary)', textTransform: 'uppercase', letterSpacing: '.5px' }}>
          Cuentas conectadas
        </span>
        <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>{accounts.length}</span>
      </div>
      {accounts.map((a) => {
        const st = (a.status ?? '').toUpperCase();
        const ok = st === 'CONNECTED';
        return (
          <div key={a.id} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '12px 16px', borderBottom: '1px solid #F4F6F9' }}>
            <NetworkChip platform={a.platform} />
            <span style={{ fontSize: 13, color: 'var(--fg-text-primary)' }}>{a.handle || a.displayName || `Cuenta ${a.id}`}</span>
            <span
              style={{
                marginLeft: 'auto',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 7,
                background: ok ? 'var(--fg-success-bg)' : 'var(--fg-danger-bg)',
                color: ok ? 'var(--fg-success-fg)' : 'var(--fg-danger-fg)',
                borderRadius: 7,
                padding: '4px 10px',
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor' }} />
              {ok ? 'Conectada' : 'Sin conexión'}
            </span>
          </div>
        );
      })}
    </div>
  );
}

/** Cuerpo con datos: KPI hero + tendencia + KPIs secundarios. */
function Dashboard({
  summary,
  trendPoints,
  trendLoading,
  trendMetricLabel,
  rangeLabel,
}: {
  summary: import('../../api/generated/model').SummaryResponse | undefined;
  trendPoints: { x: string; value: number }[];
  trendLoading: boolean;
  trendMetricLabel: string;
  rangeLabel: string;
}) {
  const alcance = heroFromSummary(summary, 'alcance');
  const interac = heroFromSummary(summary, 'interacciones');
  const eng = heroFromSummary(summary, 'engagement');
  const seg = heroFromSummary(summary, 'seguidores');

  return (
    <>
      <KpiCard
        hero
        label="Alcance"
        info={CONCEPT_BY_KEY.alcance.info}
        value={formatByUnit(alcance, 'count')}
        caption={trendMetricLabel ? `Tendencia: ${trendMetricLabel}` : undefined}
        headerRight={rangeLabel}
      >
        <div style={{ marginTop: 18 }}>
          {trendLoading ? (
            <LoadingState message="Cargando la tendencia…" minHeight={200} />
          ) : trendPoints.length === 0 ? (
            <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--fg-text-tertiary)', fontSize: 13 }}>
              Aún no hay datos para este rango.
            </div>
          ) : (
            <TrendChart data={trendPoints} />
          )}
        </div>
      </KpiCard>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16, marginTop: 16 }}>
        <KpiCard label="Interacciones" info={CONCEPT_BY_KEY.interacciones.info} value={formatByUnit(interac, 'count')} />
        <KpiCard label="Engagement" info={CONCEPT_BY_KEY.engagement.info} value={formatByUnit(eng, 'percent')} />
        <KpiCard label="Seguidores" info={CONCEPT_BY_KEY.seguidores.info} value={formatByUnit(seg, 'count')} />
      </div>
    </>
  );
}
