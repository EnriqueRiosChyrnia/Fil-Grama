import { useEffect, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useGetClientsId, useGetClientsClientIdAccounts } from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { Button, Card, NetworkChip, networkLabel } from '../../components/ui';
import { EmptyState, ErrorState, LoadingState, Skeleton } from '../../components/layout';
import { StatusPill } from './clientBits';
import { isBroken } from './accountStatus';
import { useConnectFlow } from './mutations';

function BackLink({ clientId }: { clientId: number }) {
  return (
    <Link to={`/clients/${clientId}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}>
      <span style={{ fontSize: 15 }}>‹</span>
      <span>Volver al cliente</span>
    </Link>
  );
}

export function ReconnectPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();

  const detailQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const accounts: AccountResponse[] = useMemo(() => accountsQ.data?.data ?? [], [accountsQ.data]);

  const { connect, pending, error } = useConnectFlow(id);

  // Reconectar abre la pantalla de la red en otra pestaña; al volver, refrescamos.
  useEffect(() => {
    const onFocus = () => accountsQ.refetch();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [accountsQ]);

  const broken = accounts.filter((a) => isBroken(a.status));
  const healthy = accounts.filter((a) => !isBroken(a.status));
  const detail = detailQ.data?.data;

  if (detailQ.isLoading) {
    return (
      <div>
        <BackLink clientId={id} />
        <Card style={{ marginTop: 16 }} padding={22}>
          <LoadingState message="Cargando las cuentas…" />
        </Card>
      </div>
    );
  }
  if (detailQ.isError) {
    return (
      <div>
        <BackLink clientId={id} />
        <ErrorState error={detailQ.error} onRetry={() => detailQ.refetch()} />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 680, margin: '0 auto' }}>
      <BackLink clientId={id} />
      <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: '14px 0 6px' }}>
        Reconectar cuentas
      </h1>
      <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', lineHeight: 1.55 }}>
        {detail?.name ? `${detail.name}: ` : ''}volvé a autorizar las cuentas que perdieron el acceso para
        reanudar la captura de métricas.
      </div>

      {error && (
        <div style={{ marginTop: 16, fontSize: 13, color: 'var(--fg-danger-line)', background: 'var(--fg-danger-bg)', borderRadius: 'var(--fg-radius)', padding: '10px 13px' }}>
          {error}
        </div>
      )}

      {broken.length > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 8,
            marginTop: 16,
            padding: '11px 14px',
            background: 'var(--fg-blue-50)',
            borderRadius: 'var(--fg-radius)',
            fontSize: 12.5,
            color: 'var(--fg-text-secondary)',
            lineHeight: 1.5,
          }}
        >
          <span aria-hidden>ⓘ</span>
          <span>
            Si tenés varias cuentas, cerrá sesión en TikTok o usá una ventana de incógnito para elegir la
            correcta. Reconectamos exactamente la cuenta esperada: si autorizás otra, la operación se rechaza.
          </span>
        </div>
      )}

      <div style={{ marginTop: 18 }}>
        {accountsQ.isLoading ? (
          <Card padding={18}>
            <Skeleton width="60%" height={16} />
            <Skeleton width="100%" height={48} style={{ marginTop: 14 }} />
          </Card>
        ) : accountsQ.isError ? (
          <ErrorState error={accountsQ.error} onRetry={() => accountsQ.refetch()} />
        ) : accounts.length === 0 ? (
          <EmptyState
            title="Este cliente no tiene cuentas"
            description="Agregá redes desde el alta del cliente para empezar a capturar métricas."
            action={<Button onClick={() => navigate(`/clients/${id}`)}>Ir al cliente</Button>}
          />
        ) : broken.length === 0 ? (
          <EmptyState
            icon={
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
                <circle cx="12" cy="12" r="9" stroke="var(--fg-success-fg)" strokeWidth="1.7" />
                <path d="M8.5 12.2l2.4 2.3 4.6-4.8" stroke="var(--fg-success-fg)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            }
            title="Todas las cuentas están conectadas"
            description="No hay nada para reconectar. Las métricas se capturan con normalidad."
            action={<Button onClick={() => navigate(`/clients/${id}`)}>Volver al cliente</Button>}
          />
        ) : (
          <Card padding={0} style={{ overflow: 'hidden' }}>
            {broken.map((a, i) => {
              const p = (a.platform ?? '').toUpperCase();
              return (
                <div
                  key={a.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '14px 16px',
                    borderTop: i === 0 ? 'none' : '1px solid var(--fg-border)',
                    flexWrap: 'wrap',
                  }}
                >
                  <NetworkChip platform={a.platform} />
                  <span style={{ fontSize: 13.5, color: 'var(--fg-text-primary)', minWidth: 0 }}>
                    {a.handle || a.displayName || `Cuenta ${a.id}`}
                  </span>
                  <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
                    <StatusPill status={a.status} />
                    <Button
                      size="sm"
                      loading={pending === p}
                      onClick={() => connect(p, a.id)}
                    >
                      Reconectar {networkLabel(p, true)}
                    </Button>
                  </span>
                </div>
              );
            })}
          </Card>
        )}
      </div>

      {healthy.length > 0 && (
        <div style={{ marginTop: 22 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: 10 }}>
            Cuentas en orden
          </div>
          <Card padding={0} style={{ overflow: 'hidden' }}>
            {healthy.map((a, i) => (
              <div
                key={a.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '12px 16px',
                  borderTop: i === 0 ? 'none' : '1px solid var(--fg-border)',
                }}
              >
                <NetworkChip platform={a.platform} />
                <span style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>
                  {a.handle || a.displayName || `Cuenta ${a.id}`}
                </span>
                <span style={{ marginLeft: 'auto' }}>
                  <StatusPill status={a.status} />
                </span>
              </div>
            ))}
          </Card>
        </div>
      )}
    </div>
  );
}
