import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useGetClients,
  useGetClientsClientIdSummary,
  useGetClientsClientIdAccounts,
  useGetAccountsIdMetrics,
  useGetMePriorityClients,
} from '../../api/generated/endpoints';
import type { ClientResponse } from '../../api/generated/model';
import {
  Card,
  Input,
  SegmentedControl,
  InfoTooltip,
  NetworkChip,
  Sparkline,
} from '../../components/ui';
import { EmptyState, ErrorState, Skeleton } from '../../components/layout';
import { CORE_CONCEPTS, CONCEPT_BY_KEY, type CoreConcept } from '../../lib/catalog';
import { primaryMetricKey } from '../../lib/metrics';
import { computeRange, formatByUnit } from '../../lib/format';
import { heroFromSummary, platformsFromSummary } from './clientMetrics';

const METRIC_KEY = 'fg.home.metric';
const RANGE = computeRange(7); // Home: rango fijo 7 días (HANDOFF §8)

function initials(name?: string): string {
  return (name ?? '')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
}

/** Tarjeta de cliente: cada una resuelve su propio summary (hook por tarjeta). */
function ClientCard({ client, concept, priority }: { client: ClientResponse; concept: CoreConcept; priority: boolean }) {
  const navigate = useNavigate();
  const summaryQ = useGetClientsClientIdSummary(client.id as number, { from: RANGE.from, to: RANGE.to });
  const summary = summaryQ.data?.data;

  const hero = heroFromSummary(summary, concept);
  const networks = platformsFromSummary(summary);
  const meta = CONCEPT_BY_KEY[concept];

  // Sparkline: serie de la cuenta primaria (sólo si el cliente tiene datos).
  const hasHero = hero != null;
  const accountsQ = useGetClientsClientIdAccounts(client.id as number, { query: { enabled: hasHero } });
  const accountsList = accountsQ.data?.data ?? [];
  const primary = accountsList.find((a) => (a.status ?? '').toUpperCase() === 'CONNECTED') ?? accountsList[0];
  const sparkMetric = primary?.platform ? primaryMetricKey('alcance', primary.platform) ?? undefined : undefined;
  const seriesQ = useGetAccountsIdMetrics(
    primary?.id ?? 0,
    { metric: sparkMetric ?? '', from: RANGE.from, to: RANGE.to, granularity: 'day' },
    { query: { enabled: hasHero && !!primary?.id && !!sparkMetric } },
  );
  const sparkValues = (seriesQ.data?.data?.points ?? []).map((p) => p.value ?? 0);

  return (
    <Card interactive onClick={() => navigate(`/clients/${client.id}`)} style={{ display: 'flex', flexDirection: 'column', gap: 13, minHeight: 168 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9, minWidth: 0, flex: 1 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 9,
              background: 'var(--fg-blue-50)',
              color: 'var(--fg-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 13,
              fontWeight: 600,
              flex: 'none',
            }}
          >
            {initials(client.name)}
          </div>
          <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
            {client.name}
          </span>
          {priority && (
            <svg width="15" height="15" viewBox="0 0 15 15" style={{ flex: 'none' }} aria-label="Prioritario">
              <path d="M7.5 1l1.8 3.9 4.2.5-3.1 2.9.8 4.2-3.7-2.1-3.7 2.1.8-4.2L1.5 5.4l4.2-.5z" fill="var(--fg-primary)" />
            </svg>
          )}
        </div>
        <div style={{ display: 'flex', gap: 5, flex: 'none' }}>
          {networks.map((p) => (
            <NetworkChip key={p} platform={p} />
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: 11 }}>
        <span style={{ fontSize: 36, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-1px', lineHeight: 1 }}>
          {summaryQ.isLoading ? <Skeleton width={90} height={30} /> : formatByUnit(hero, meta.unit === 'percent' ? 'percent' : 'count')}
        </span>
      </div>

      <div style={{ marginTop: 'auto', minHeight: 46 }}>
        {hero == null ? (
          <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>
            {summaryQ.isError ? 'No pudimos cargar el resumen.' : 'Aún no hay datos para este rango.'}
          </div>
        ) : sparkValues.length > 1 ? (
          <Sparkline values={sparkValues} />
        ) : null}
      </div>
    </Card>
  );
}

function CardSkeleton() {
  return (
    <Card style={{ minHeight: 168, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', gap: 9, alignItems: 'center' }}>
        <Skeleton width={36} height={36} radius={9} />
        <Skeleton width={120} height={15} />
      </div>
      <Skeleton width={110} height={32} />
      <Skeleton width="100%" height={40} style={{ marginTop: 'auto' }} />
    </Card>
  );
}

export function ClientsHomePage() {
  const [concept, setConcept] = useState<CoreConcept>(
    () => (localStorage.getItem(METRIC_KEY) as CoreConcept) || 'alcance',
  );
  const [filter, setFilter] = useState<'todos' | 'prioritarios'>('todos');
  const [search, setSearch] = useState('');

  const clientsQ = useGetClients({ size: 100, status: 'ACTIVE' });
  const priorityQ = useGetMePriorityClients();

  const clients = useMemo(() => clientsQ.data?.data?.content ?? [], [clientsQ.data]);
  const priorityIds = useMemo(
    () => new Set((priorityQ.data?.data ?? []).map((c: ClientResponse) => c.id)),
    [priorityQ.data],
  );

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return clients
      .filter((c) => (filter === 'prioritarios' ? priorityIds.has(c.id) : true))
      .filter((c) => (q ? (c.name ?? '').toLowerCase().includes(q) : true));
  }, [clients, filter, priorityIds, search]);

  const onConcept = (c: CoreConcept) => {
    setConcept(c);
    localStorage.setItem(METRIC_KEY, c);
  };

  const meta = CONCEPT_BY_KEY[concept];

  return (
    <div>
      {/* header */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
            Clientes
          </h1>
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 5 }}>
            {clientsQ.isSuccess
              ? `${visible.length} ${visible.length === 1 ? 'cliente' : 'clientes'} · resumen de los últimos 7 días`
              : 'Resumen de los últimos 7 días'}
          </div>
        </div>
        <div
          style={{
            height: 38,
            background: 'var(--fg-bg-surface)',
            border: '1px solid var(--fg-border-strong)',
            borderRadius: 9,
            display: 'flex',
            alignItems: 'center',
            padding: '0 14px',
            fontSize: 13,
            color: 'var(--fg-text-primary)',
          }}
        >
          Últimos 7 días
        </div>
      </div>

      {/* controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginTop: 20 }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <Input placeholder="Buscar cliente" value={search} onChange={(e) => setSearch(e.target.value)} style={{ height: 40 }} />
        </div>
        <SegmentedControl
          ariaLabel="Filtro de clientes"
          value={filter}
          onChange={setFilter}
          options={[
            { value: 'todos', label: 'Todos' },
            { value: 'prioritarios', label: 'Prioritarios' },
          ]}
        />
      </div>

      {/* metric selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 14 }}>
        <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.6px' }}>
          Métrica
        </span>
        <SegmentedControl<CoreConcept>
          ariaLabel="Métrica del número grande"
          value={concept}
          onChange={onConcept}
          options={CORE_CONCEPTS.map((c) => ({ value: c.key, label: c.label }))}
        />
        <InfoTooltip title={meta.label}>{meta.info}</InfoTooltip>
      </div>

      {/* grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16, marginTop: 22 }}>
        {clientsQ.isLoading ? (
          <>
            <CardSkeleton />
            <CardSkeleton />
            <CardSkeleton />
          </>
        ) : clientsQ.isError ? (
          <div style={{ gridColumn: '1 / -1' }}>
            <ErrorState error={clientsQ.error} onRetry={() => clientsQ.refetch()} />
          </div>
        ) : clients.length === 0 ? (
          <div style={{ gridColumn: '1 / -1' }}>
            <EmptyState
              title="Todavía no hay clientes"
              description="Cuando sumes el primer cliente y conectes sus redes, vas a ver acá su resumen."
            />
          </div>
        ) : visible.length === 0 ? (
          <div style={{ gridColumn: '1 / -1' }}>
            <EmptyState
              title="No encontramos clientes con ese filtro"
              description="Probá con otro nombre o cambiá el filtro de prioritarios."
            />
          </div>
        ) : (
          visible.map((c) => (
            <ClientCard key={c.id} client={c} concept={concept} priority={priorityIds.has(c.id)} />
          ))
        )}
      </div>
    </div>
  );
}
