import { useState } from 'react';
import { useGetSyncRuns } from '../../../api/generated/endpoints';
import type { SyncRunResponse } from '../../../api/generated/model';
import { Button, Card, Table, type Column } from '../../../components/ui';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '../../../components/layout';
import { ApiError } from '../../../lib/api';
import { ADMIN_PAGE_SIZE, formatDateTime, syncStatusMeta } from '../adminConstants';
import { Badge } from '../components/Badge';
import { Pagination } from '../components/Pagination';
import { useTriggerSync } from '../hooks/useAdminMutations';
import { SyncRunDetailModal } from './SyncRunDetailModal';

export function SyncPage() {
  const [page, setPage] = useState(0);
  const [openRunId, setOpenRunId] = useState<number | null>(null);

  const runsQ = useGetSyncRuns({ page, size: ADMIN_PAGE_SIZE });
  const pageData = runsQ.data?.data;
  const rows = pageData?.content ?? [];

  const trigger = useTriggerSync();
  const lastRunId = trigger.data?.data?.runId;
  const triggerError = trigger.error instanceof ApiError ? trigger.error.humanMessage : null;

  const columns: Column<SyncRunResponse>[] = [
    { key: 'startedAt', header: 'Inicio', width: '1.3fr', render: (r) => formatDateTime(r.startedAt) },
    {
      key: 'status',
      header: 'Estado',
      width: '120px',
      render: (r) => {
        const m = syncStatusMeta(r.status);
        return <Badge bg={m.bg} fg={m.fg}>{m.label}</Badge>;
      },
    },
    {
      key: 'accounts',
      header: 'Cuentas OK',
      width: '110px',
      align: 'right',
      render: (r) => `${r.accountsOk ?? 0} / ${r.accountsTotal ?? 0}`,
    },
    {
      key: 'failed',
      header: 'Con error',
      width: '90px',
      align: 'right',
      render: (r) => (
        <span style={{ color: (r.accountsFailed ?? 0) > 0 ? 'var(--fg-danger-fg)' : 'var(--fg-text-tertiary)' }}>
          {r.accountsFailed ?? 0}
        </span>
      ),
    },
    {
      key: 'finishedAt',
      header: 'Fin',
      width: '1.3fr',
      align: 'right',
      render: (r) => (
        <span style={{ color: 'var(--fg-text-tertiary)' }}>{r.finishedAt ? formatDateTime(r.finishedAt) : 'En curso'}</span>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
            Sincronización
          </h1>
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 5 }}>
            El job captura las métricas a diario. Acá ves el historial y podés dispararlo manualmente.
          </div>
        </div>
        <Button onClick={() => trigger.mutate()} loading={trigger.isPending}>
          Ejecutar ahora
        </Button>
      </div>

      {lastRunId != null && (
        <div
          style={{
            marginTop: 16,
            fontSize: 13,
            color: 'var(--fg-success-fg)',
            background: 'var(--fg-success-bg)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 12px',
          }}
        >
          Sincronización iniciada — corrida #{lastRunId}. El historial se actualiza solo.
        </div>
      )}
      {triggerError && (
        <div
          style={{
            marginTop: 16,
            fontSize: 13,
            color: 'var(--fg-danger-fg)',
            background: 'var(--fg-danger-bg)',
            border: '1px solid var(--fg-danger-border)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 12px',
          }}
        >
          {triggerError}
        </div>
      )}

      <Card style={{ marginTop: 18, padding: '12px 0 4px' }}>
        {runsQ.isLoading ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '8px 20px' }}>
            <Skeleton height={36} />
            <Skeleton height={36} />
            <Skeleton height={36} />
            <LoadingState message="Cargando historial…" minHeight={60} />
          </div>
        ) : runsQ.isError ? (
          <div style={{ padding: '8px 20px' }}>
            <ErrorState error={runsQ.error} onRetry={() => runsQ.refetch()} />
          </div>
        ) : rows.length === 0 ? (
          <div style={{ padding: '8px 20px' }}>
            <EmptyState
              title="Todavía no hubo corridas"
              description="Cuando el job se ejecute (automático o manual), vas a ver acá cada corrida con su resultado."
            />
          </div>
        ) : (
          <Table<SyncRunResponse>
            columns={columns}
            rows={rows}
            rowKey={(r, i) => r.id ?? i}
            onRowClick={(r) => r.id != null && setOpenRunId(r.id)}
          />
        )}
      </Card>

      {rows.length > 0 && (
        <Pagination
          page={pageData?.page ?? page}
          totalPages={pageData?.totalPages ?? 1}
          totalElements={pageData?.totalElements ?? rows.length}
          onPage={setPage}
        />
      )}

      {openRunId != null && <SyncRunDetailModal runId={openRunId} onClose={() => setOpenRunId(null)} />}
    </div>
  );
}
