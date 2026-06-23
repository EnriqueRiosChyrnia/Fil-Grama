import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useGetClientsClientIdAccounts } from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { Card, NetworkChip, networkLabel } from '../../components/ui';
import { EmptyState, ErrorState, LoadingState } from '../../components/layout';

/**
 * Pestaña "Publicaciones" del workspace de cliente. Las publicaciones son POR
 * CUENTA (HANDOFF §10 multi-cuenta), así que primero se elige la cuenta en
 * cascada red → cuenta. Al elegir, se navega a la grilla existente (AllPostsPage,
 * `clients/:id/accounts/:accountId/posts`) — no duplicamos la grilla ni el detalle
 * de post. Si el cliente tiene una sola cuenta, salto directo a su grilla.
 */

const NET_ORDER = ['INSTAGRAM', 'FACEBOOK', 'TIKTOK'];

function accountName(a: AccountResponse): string {
  return a.handle || a.displayName || `Cuenta ${a.id}`;
}

export function PublicacionesPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();

  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const accounts: AccountResponse[] = (accountsQ.data?.data ?? []).filter((a) => a.id != null && a.platform);

  if (accountsQ.isLoading) {
    return (
      <Card padding={22}>
        <LoadingState message="Cargando las cuentas…" />
      </Card>
    );
  }
  if (accountsQ.isError) {
    return <ErrorState error={accountsQ.error} onRetry={() => accountsQ.refetch()} />;
  }
  if (accounts.length === 0) {
    return (
      <EmptyState
        title="Este cliente todavía no tiene cuentas"
        description="Cuando haya redes conectadas vas a poder explorar sus publicaciones acá."
      />
    );
  }

  // Una sola cuenta → salto directo a su grilla (sin paso de selección).
  if (accounts.length === 1) {
    return <Navigate to={`/clients/${id}/accounts/${accounts[0].id}/posts`} replace />;
  }

  const platforms = Array.from(new Set(accounts.map((a) => (a.platform ?? '').toUpperCase()))).sort(
    (a, b) => NET_ORDER.indexOf(a) - NET_ORDER.indexOf(b),
  );

  const openGrid = (accountId: number) => navigate(`/clients/${id}/accounts/${accountId}/posts`);

  return (
    <div>
      <h2 style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)', margin: 0 }}>Elegí una cuenta</h2>
      <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 4, lineHeight: 1.5, maxWidth: 560 }}>
        Las publicaciones son por cuenta. Elegí cuál querés ver; primero la red, después la cuenta.
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 18, marginTop: 20 }}>
        {platforms.map((p) => (
          <div key={p}>
            <div
              style={{
                fontSize: 11,
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '.5px',
                color: 'var(--fg-text-tertiary)',
                marginBottom: 9,
              }}
            >
              {networkLabel(p)}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 12 }}>
              {accounts
                .filter((a) => (a.platform ?? '').toUpperCase() === p)
                .map((a) => (
                  <Card key={a.id} interactive padding={14} onClick={() => openGrid(a.id as number)}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
                      <NetworkChip platform={a.platform} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontSize: 14,
                            fontWeight: 500,
                            color: 'var(--fg-text-primary)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {accountName(a)}
                        </div>
                        <div style={{ fontSize: 11.5, color: 'var(--fg-text-tertiary)', marginTop: 2 }}>
                          Ver publicaciones
                        </div>
                      </div>
                      <span style={{ fontSize: 17, color: 'var(--fg-text-tertiary)' }}>›</span>
                    </div>
                  </Card>
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
