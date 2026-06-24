import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  useGetClientsClientIdAccounts,
  useGetClientsClientIdSummary,
} from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { useAccountReport, pointsForMetric } from '../accounts/metricsReport';
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
import { EmptyState, ErrorState, LoadingState } from '../../components/layout';
import { useCatalog, CONCEPT_BY_KEY } from '../../lib/catalog';
import { primaryMetricKey } from '../../lib/metrics';
import { computeRange, formatByUnit, type RangeDays } from '../../lib/format';
import { heroFromSummary } from './clientMetrics';
import { StatusPill } from './clientBits';

/**
 * Dashboard de cliente (pestaña Dashboard del workspace). El encabezado de cliente
 * (nombre, chips de redes, breadcrumb) y las pestañas los provee ClientWorkspace;
 * acá va sólo el CUERPO: controles + KPIs + tendencia + cuentas conectadas
 * (clickeables → detalle de cuenta).
 */

function ClockIcon() {
  return (
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="13" r="8" stroke="var(--fg-primary)" strokeWidth="1.7" />
      <path d="M12 9.5V13l2.4 1.6" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9 3.5h6" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
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

  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const summaryQ = useGetClientsClientIdSummary(
    id,
    { from: dr.from, to: dr.to, platform: sel.platform === 'ALL' ? undefined : sel.platform },
    { query: { enabled: Number.isFinite(id) } },
  );

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
  const primary = sel.accountId !== 'ALL' ? accounts.find((a) => a.id === sel.accountId) : connected[0];
  const trendMetric = primary?.platform ? primaryMetricKey('alcance', primary.platform) ?? undefined : undefined;
  const trendQ = useAccountReport(primary?.id ?? 0, trendMetric ? [trendMetric] : [], dr, {
    enabled: !!primary?.id && !!trendMetric && hasData,
  });
  const trendPoints = pointsForMetric(trendQ.data?.data, trendMetric).map((p) => ({ x: p.date ?? '', value: p.value ?? 0 }));

  if (accountsQ.isLoading) {
    return (
      <Card padding={22}>
        <LoadingState message="Cargando el dashboard…" />
      </Card>
    );
  }
  if (accountsQ.isError) {
    return <ErrorState error={accountsQ.error} onRetry={() => accountsQ.refetch()} />;
  }

  return (
    <div>
      {/* controls */}
      {accounts.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, flexWrap: 'wrap' }}>
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
            description="Conectá una red para empezar a ver métricas, publicaciones y reportes. Te llevamos al inicio de sesión oficial de la red; nunca guardamos la contraseña."
            action={<Button onClick={() => navigate(`/clients/${id}/cuentas`)}>Conectar red</Button>}
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
            <AccountList accounts={accounts} clientId={id} />
          </EmptyState>
        ) : !hasData ? (
          <EmptyState
            icon={<ClockIcon />}
            title="Conectamos las cuentas. Estamos juntando los primeros datos."
            description="Las primeras métricas aparecen tras la próxima captura diaria. No tenés que hacer nada: el job corre solo, una vez al día por cuenta."
          >
            <AccountList accounts={accounts} clientId={id} />
            <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', marginTop: 14 }}>
              Mientras tanto podés revisar otros clientes; te avisamos cuando lleguen los primeros datos.
            </div>
          </EmptyState>
        ) : (
          <>
            <Dashboard
              summary={summary}
              trendPoints={trendPoints}
              trendLoading={trendQ.isLoading}
              trendMetricLabel={catalog.displayName(trendMetric)}
              rangeLabel={dr.label}
            />
            <div style={{ marginTop: 16 }}>
              <AccountList accounts={accounts} clientId={id} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/**
 * Lista "Cuentas conectadas". CLICKEABLE: cada fila entra al detalle de la cuenta
 * (`clients/:id/accounts/:accountId`). Antes la lista no llevaba a ningún lado;
 * ahora es la entrada al drill-down de cuenta (fix de navegación del dashboard).
 */
function AccountList({ accounts, clientId }: { accounts: AccountResponse[]; clientId: number }) {
  const navigate = useNavigate();
  return (
    <div style={{ width: '100%', border: '1px solid var(--fg-border)', borderRadius: 12, overflow: 'hidden', textAlign: 'left' }}>
      <style>{`.fg-acctrow:hover{background:var(--fg-bg-muted)}`}</style>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '13px 16px', borderBottom: '1px solid var(--fg-border)' }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--fg-text-primary)' }}>Cuentas conectadas</span>
        <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>
          {accounts.length} {accounts.length === 1 ? 'cuenta' : 'cuentas'} · tocá una para ver su detalle
        </span>
      </div>
      {accounts.map((a) => (
        <button
          key={a.id}
          type="button"
          className="fg-acctrow"
          onClick={() => navigate(`/clients/${clientId}/accounts/${a.id}`)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 11,
            width: '100%',
            padding: '13px 16px',
            borderTop: '1px solid var(--fg-border)',
            borderLeft: 'none',
            borderRight: 'none',
            borderBottom: 'none',
            background: 'var(--fg-bg-surface)',
            cursor: 'pointer',
            textAlign: 'left',
            transition: 'background .12s',
          }}
        >
          <NetworkChip platform={a.platform} />
          <span style={{ fontSize: 13, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {a.handle || a.displayName || `Cuenta ${a.id}`}
          </span>
          <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 10 }}>
            <StatusPill status={a.status} />
            <span style={{ fontSize: 17, color: 'var(--fg-text-tertiary)' }} aria-hidden>
              ›
            </span>
          </span>
        </button>
      ))}
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
