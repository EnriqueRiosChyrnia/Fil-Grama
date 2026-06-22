import { useGetSyncRunsId } from '../../../api/generated/endpoints';
import type { SyncAccountResultResponse } from '../../../api/generated/model';
import { Table, type Column } from '../../../components/ui';
import { EmptyState, ErrorState, LoadingState } from '../../../components/layout';
import { formatDateTime, syncStatusMeta } from '../adminConstants';
import { Badge } from '../components/Badge';
import { Modal } from '../components/Modal';

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.5px', color: 'var(--fg-text-tertiary)' }}>
        {label}
      </div>
      <div style={{ fontSize: 14, color: 'var(--fg-text-primary)', marginTop: 3 }}>{value}</div>
    </div>
  );
}

/** Detalle de una corrida del job: resumen + resultado por cuenta (HANDOFF §6). */
export function SyncRunDetailModal({ runId, onClose }: { runId: number; onClose: () => void }) {
  const detailQ = useGetSyncRunsId(runId);
  const detail = detailQ.data?.data;
  const run = detail?.run;
  const accounts = detail?.accounts ?? [];
  const runMeta = syncStatusMeta(run?.status);

  const columns: Column<SyncAccountResultResponse>[] = [
    { key: 'accountId', header: 'Cuenta', width: '1fr', render: (a) => `#${a.accountId ?? '—'}` },
    {
      key: 'status',
      header: 'Estado',
      width: '110px',
      render: (a) => {
        const m = syncStatusMeta(a.status);
        return <Badge bg={m.bg} fg={m.fg}>{m.label}</Badge>;
      },
    },
    { key: 'metrics', header: 'Métricas', width: '90px', align: 'right', render: (a) => a.metricsCaptured ?? 0 },
    {
      key: 'error',
      header: 'Detalle',
      width: '1.6fr',
      render: (a) => (
        <span style={{ fontSize: 12.5, color: a.errorMessage ? 'var(--fg-danger-fg)' : 'var(--fg-text-tertiary)' }}>
          {a.errorMessage || '—'}
        </span>
      ),
    },
  ];

  return (
    <Modal open title={`Corrida #${runId}`} onClose={onClose} width={620}>
      {detailQ.isLoading ? (
        <LoadingState message="Cargando detalle…" minHeight={160} />
      ) : detailQ.isError ? (
        <ErrorState error={detailQ.error} onRetry={() => detailQ.refetch()} minHeight={160} />
      ) : !run ? (
        <EmptyState title="Sin detalle" description="No encontramos información de esta corrida." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 14 }}>
            <Field label="Estado" value={<Badge bg={runMeta.bg} fg={runMeta.fg}>{runMeta.label}</Badge>} />
            <Field label="Inicio" value={formatDateTime(run.startedAt)} />
            <Field label="Fin" value={run.finishedAt ? formatDateTime(run.finishedAt) : 'En curso'} />
            <Field label="Cuentas OK" value={`${run.accountsOk ?? 0} / ${run.accountsTotal ?? 0}`} />
            <Field label="Con error" value={run.accountsFailed ?? 0} />
          </div>

          {run.errorSummary && (
            <div
              style={{
                fontSize: 13,
                color: 'var(--fg-danger-fg)',
                background: 'var(--fg-danger-bg)',
                border: '1px solid var(--fg-danger-border)',
                borderRadius: 'var(--fg-radius)',
                padding: '10px 12px',
              }}
            >
              {run.errorSummary}
            </div>
          )}

          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg-text-primary)', marginBottom: 8 }}>
              Resultado por cuenta
            </div>
            {accounts.length === 0 ? (
              <div style={{ fontSize: 13, color: 'var(--fg-text-tertiary)', padding: '8px 0' }}>
                Esta corrida no procesó cuentas.
              </div>
            ) : (
              <Table<SyncAccountResultResponse>
                columns={columns}
                rows={accounts}
                rowKey={(a, i) => a.id ?? a.accountId ?? i}
              />
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
