import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import {
  useGetClientsId,
  useGetClientsClientIdAccounts,
  getGetAccountsIdMetricsQueryOptions,
} from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import {
  Card,
  SegmentedControl,
  NetworkChip,
  InfoTooltip,
  CompareBars,
  Table,
  DateRangeControl,
  networkLabel,
  type Column,
} from '../../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../../components/layout';
import { CONCEPT_BY_KEY, type CoreConcept } from '../../lib/catalog';
import { computeRange, formatCompact, formatPercent, type RangeDays } from '../../lib/format';
import { AccountMultiSelect } from './AccountMultiSelect';
import {
  COMPARE_CONCEPTS,
  MAX_ACCOUNTS,
  aggregateSeries,
  bestValue,
  buildMetricQueryItems,
  cellFor,
  deriveEngagement,
  toCompareAccount,
  type CompareAccount,
  type Totals,
} from './compareData';

const isNum = (x: unknown): x is number => typeof x === 'number' && !Number.isNaN(x);

function useNarrow(maxPx = 640): boolean {
  const [narrow, setNarrow] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(`(max-width:${maxPx}px)`).matches,
  );
  useEffect(() => {
    const mq = window.matchMedia(`(max-width:${maxPx}px)`);
    const onChange = () => setNarrow(mq.matches);
    onChange();
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [maxPx]);
  return narrow;
}

function fmt(concept: CoreConcept, value: number): string {
  return concept === 'engagement' ? formatPercent(value) : formatCompact(value);
}

function Breadcrumb({ to }: { to: string }) {
  return (
    <Link
      to={to}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}
    >
      <span style={{ fontSize: 15 }}>‹</span>
      <span>Volver al cliente</span>
    </Link>
  );
}

/** "—" + ⓘ para una métrica que no es equivalente entre redes (HANDOFF §11). */
function NotComparable() {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: 'var(--fg-text-tertiary)' }}>
      —
      <InfoTooltip title="No comparable" align="right" size={14}>
        Esta métrica no es equivalente entre redes (por ejemplo, el “alcance” de TikTok son reproducciones), así que
        no la enfrentamos con las demás.
      </InfoTooltip>
    </span>
  );
}

export function CompareAccountsPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const narrow = useNarrow();

  const [range, setRange] = useState<RangeDays>(30);
  const [view, setView] = useState<'table' | 'bars'>('table');
  const [selectedIds, setSelectedIds] = useState<number[] | null>(null);
  const dr = computeRange(range);

  const detailQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });

  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const compareAccounts: CompareAccount[] = accounts
    .map(toCompareAccount)
    .filter((a): a is CompareAccount => a !== null);

  // Selección por defecto DERIVADA en render (sin efecto): cuentas conectadas (o
  // todas) hasta el tope. `selectedIds` sólo se llena cuando el usuario elige.
  const connectedAccounts = compareAccounts.filter((a) => (a.status ?? '').toUpperCase() === 'CONNECTED');
  const defaultSelected = (connectedAccounts.length ? connectedAccounts : compareAccounts)
    .slice(0, MAX_ACCOUNTS)
    .map((a) => a.id);
  const selIds = selectedIds ?? defaultSelected;
  const selectedAccounts = compareAccounts.filter((a) => selIds.includes(a.id));

  const toggle = (accId: number) =>
    setSelectedIds((prev) => {
      const cur = prev ?? defaultSelected;
      if (cur.includes(accId)) return cur.filter((x) => x !== accId);
      if (cur.length >= MAX_ACCOUNTS) return cur;
      return [...cur, accId];
    });

  // Fan-out: una query por (cuenta, concepto con key cruda). useQueries soporta
  // longitud variable (no rompe las reglas de hooks como un useQuery en bucle).
  const items = buildMetricQueryItems(selectedAccounts);
  const results = useQueries({
    queries: items.map((it) =>
      getGetAccountsIdMetricsQueryOptions(it.accountId, {
        metric: it.metric,
        from: dr.from,
        to: dr.to,
        granularity: 'day',
      }),
    ),
  });

  // totals[accountId][concept] (+ engagement derivado de alcance/interacciones).
  const totals: Totals = {};
  items.forEach((it, i) => {
    const points = results[i]?.data?.data?.points;
    (totals[it.accountId] ??= {})[it.concept] = aggregateSeries(points, it.concept);
  });
  for (const acc of selectedAccounts) {
    const row = (totals[acc.id] ??= {});
    row.engagement = deriveEngagement(row.alcance, row.interacciones);
  }

  const metricsLoading = items.length > 0 && results.some((r) => r.isLoading);
  const metricsAllError = items.length > 0 && results.length > 0 && results.every((r) => r.isError);
  const hasAnyData = selectedAccounts.some((a) =>
    COMPARE_CONCEPTS.some((c) => isNum(totals[a.id]?.[c])),
  );
  const bestByConcept = Object.fromEntries(
    COMPARE_CONCEPTS.map((c) => [c, bestValue(c, selectedAccounts, totals)]),
  ) as Record<CoreConcept, number | null>;

  // ---- estados de carga / error de la pantalla ----
  if (accountsQ.isLoading || detailQ.isLoading) {
    return (
      <div>
        <Breadcrumb to={`/clients/${id}`} />
        <Card style={{ marginTop: 16 }}>
          <LoadingState message="Cargando las cuentas…" />
        </Card>
      </div>
    );
  }
  if (accountsQ.isError) {
    return (
      <div>
        <Breadcrumb to={`/clients/${id}`} />
        <ErrorState error={accountsQ.error} onRetry={() => accountsQ.refetch()} />
      </div>
    );
  }

  const clientName = detailQ.data?.data?.name;

  return (
    <div>
      <Breadcrumb to={`/clients/${id}`} />

      <div style={{ marginTop: 14 }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
          Comparar cuentas
        </h1>
        <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', marginTop: 6 }}>
          {clientName ? `${clientName} · ` : ''}Totales de {dr.label.toLowerCase()}. Elegí hasta {MAX_ACCOUNTS} cuentas.
        </div>
      </div>

      {compareAccounts.length === 0 ? (
        <EmptyState
          title="Este cliente todavía no tiene cuentas para comparar"
          description="Cuando haya al menos dos cuentas conectadas vas a poder enfrentarlas acá."
        />
      ) : (
        <>
          <Card style={{ marginTop: 18 }} padding={20}>
            <AccountMultiSelect accounts={compareAccounts} selectedIds={selIds} onToggle={toggle} />
          </Card>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              marginTop: 18,
              flexWrap: 'wrap',
            }}
          >
            <DateRangeControl value={range} onChange={setRange} />
            <SegmentedControl<'table' | 'bars'>
              ariaLabel="Vista"
              value={view}
              onChange={setView}
              options={[
                { value: 'table', label: 'Tabla' },
                { value: 'bars', label: 'Barras' },
              ]}
            />
          </div>

          <div style={{ marginTop: 16 }}>
            {selectedAccounts.length === 0 ? (
              <EmptyState
                title="Elegí cuentas para comparar"
                description="Seleccioná dos o más cuentas de la lista de arriba para verlas enfrentadas."
              />
            ) : metricsLoading ? (
              <Card>
                <LoadingState message="Juntando los totales del rango…" />
              </Card>
            ) : metricsAllError ? (
              <ErrorState
                error={results.find((r) => r.isError)?.error}
                onRetry={() => results.forEach((r) => r.refetch())}
              />
            ) : !hasAnyData ? (
              <EmptyState
                title="Aún no hay datos para este rango"
                description="Probá con un rango más amplio o esperá a la próxima captura diaria."
              />
            ) : view === 'table' ? (
              <Card padding={20}>
                <CompareTable accounts={selectedAccounts} totals={totals} best={bestByConcept} />
              </Card>
            ) : (
              <BarsView accounts={selectedAccounts} totals={totals} narrow={narrow} />
            )}
          </div>
        </>
      )}
    </div>
  );
}

/** Tabla: filas = cuentas, columnas = las 4 métricas. Mejor valor en negrita. */
function CompareTable({
  accounts,
  totals,
  best,
}: {
  accounts: CompareAccount[];
  totals: Totals;
  best: Record<CoreConcept, number | null>;
}) {
  const metricColumns: Column<CompareAccount>[] = COMPARE_CONCEPTS.map((concept) => {
    const meta = CONCEPT_BY_KEY[concept];
    return {
      key: concept,
      align: 'right',
      header: (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
          {meta.label}
          <InfoTooltip title={meta.label} align="right" size={14}>
            {meta.info}
          </InfoTooltip>
        </span>
      ),
      render: (row) => {
        const cell = cellFor(concept, row.platform, totals[row.id]);
        if (!cell.comparable) return <NotComparable />;
        if (cell.value == null) return <span style={{ color: 'var(--fg-text-tertiary)' }}>—</span>;
        const isBest = best[concept] != null && cell.value === best[concept];
        return <span style={{ fontWeight: isBest ? 700 : 400 }}>{fmt(concept, cell.value)}</span>;
      },
    };
  });

  const columns: Column<CompareAccount>[] = [
    {
      key: 'account',
      header: 'Cuenta',
      width: '1.6fr',
      render: (row) => (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <NetworkChip platform={row.platform} />
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.label}</span>
        </span>
      ),
    },
    ...metricColumns,
  ];

  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ minWidth: 520 }}>
        <Table columns={columns} rows={accounts} rowKey={(r) => r.id} />
      </div>
    </div>
  );
}

/** Barras: un CompareBars por métrica, cada una escalada por separado (§11). */
function BarsView({
  accounts,
  totals,
  narrow,
}: {
  accounts: CompareAccount[];
  totals: Totals;
  narrow: boolean;
}) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: narrow ? '1fr' : 'repeat(2, 1fr)', gap: 16 }}>
      {COMPARE_CONCEPTS.map((concept) => {
        const meta = CONCEPT_BY_KEY[concept];
        const comparable = accounts.filter((a) => cellFor(concept, a.platform, totals[a.id]).comparable);
        const data = comparable
          .map((a) => ({ label: `${networkLabel(a.platform)} ${a.label}`, value: totals[a.id]?.[concept] ?? null }))
          .filter((d): d is { label: string; value: number } => isNum(d.value));
        const excluded = accounts.filter((a) => !cellFor(concept, a.platform, totals[a.id]).comparable);

        return (
          <Card key={concept} padding={18}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{meta.label}</span>
              <InfoTooltip title={meta.label} size={14}>
                {meta.info}
              </InfoTooltip>
            </div>
            {data.length === 0 ? (
              <div
                style={{
                  height: 120,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--fg-text-tertiary)',
                  fontSize: 13,
                }}
              >
                Sin datos comparables en este rango.
              </div>
            ) : (
              <div style={{ marginTop: 8 }}>
                <CompareBars
                  data={data}
                  horizontal={narrow}
                  height={narrow ? Math.max(120, data.length * 46) : 220}
                  formatValue={concept === 'engagement' ? formatPercent : formatCompact}
                />
              </div>
            )}
            {excluded.length > 0 && (
              <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 8 }}>
                No comparable: {excluded.map((a) => `${networkLabel(a.platform)} ${a.label}`).join(', ')}.
              </div>
            )}
          </Card>
        );
      })}
    </div>
  );
}
