import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useGetClientsClientIdAccounts } from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { Button, Card, NetworkChip, networkLabel } from '../../components/ui';
import { EmptyState, ErrorState, LoadingState } from '../../components/layout';
import { StatusPill } from './clientBits';
import { isBroken, normStatus, NETWORKS } from './accountStatus';
import { useConnectFlow, useDisconnectAccount } from './mutations';

/**
 * Pestaña "Cuentas" del workspace de cliente (diseño "Gestionar redes"). Un solo
 * lugar para conectar y gestionar las redes de un cliente ya creado: lista
 * AGRUPADA POR RED + Conectar / Reconectar / Desconectar. Unifica lo que antes
 * estaba partido entre el paso 2 del alta (wizard) y la pantalla de Reconexión.
 *
 * Reusa las mutaciones bendecidas (NO duplica connect/disconnect):
 *  - `useConnectFlow` → connect(platform): abre el authorizationUrl oficial en otra
 *    pestaña (mismo flujo que el wizard). Reconectar = re-disparar connect.
 *  - `useDisconnectAccount` → postAccountsIdDisconnect, invalida la query de cuentas.
 * Al volver del OAuth (foco de ventana) refrescamos la lista (refetchOnWindowFocus
 * está apagado en el QueryClient), igual que ReconnectPage.
 */

const NET_HINT: Record<string, string> = {
  INSTAGRAM: 'Requiere cuenta profesional (Business o Creator)',
  FACEBOOK: 'Requiere página y cuenta profesional',
  TIKTOK: 'Cuenta personal o de negocios',
};

function PlusIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden>
      <path d="M7 2v10M2 7h10" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function NetworkGlyph() {
  return (
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="6" cy="12" r="2.4" stroke="var(--fg-primary)" strokeWidth="1.6" />
      <circle cx="17" cy="6" r="2.4" stroke="var(--fg-primary)" strokeWidth="1.6" />
      <circle cx="17" cy="18" r="2.4" stroke="var(--fg-primary)" strokeWidth="1.6" />
      <path d="M8.1 10.9 14.8 7.2M8.1 13.1l6.7 3.7" stroke="var(--fg-primary)" strokeWidth="1.6" />
    </svg>
  );
}

export function CuentasPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();

  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const accounts: AccountResponse[] = useMemo(() => accountsQ.data?.data ?? [], [accountsQ.data]);

  const { connect, pending, error: connectError } = useConnectFlow(id);
  const disconnect = useDisconnectAccount(id);

  // Conectar/Reconectar abre la red en otra pestaña; al volver, refrescamos.
  useEffect(() => {
    const onFocus = () => accountsQ.refetch();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [accountsQ]);

  // Modal "Conectar una red": elegir red → redirigir al OAuth oficial.
  const [connectOpen, setConnectOpen] = useState(false);
  const [redirectNet, setRedirectNet] = useState<string | null>(null);
  const openConnect = () => {
    setRedirectNet(null);
    setConnectOpen(true);
  };
  const closeConnect = () => {
    setConnectOpen(false);
    setRedirectNet(null);
  };
  const pickNetwork = (net: string) => {
    setRedirectNet(net);
    connect(net); // abre el authorizationUrl en otra pestaña
  };

  const total = accounts.length;
  const brokenCount = accounts.filter((a) => isBroken(a.status)).length;
  const subhead =
    `${total} ${total === 1 ? 'cuenta' : 'cuentas'}` +
    (brokenCount > 0
      ? ` · ${brokenCount} ${brokenCount === 1 ? 'requiere' : 'requieren'} reconexión`
      : ' · todas activas');

  // Agrupar por red, en el orden de presentación (IG · FB · TikTok) + cualquier
  // red extra que aparezca en los datos.
  const groups = useMemo(() => {
    const present = Array.from(new Set(accounts.map((a) => (a.platform ?? '').toUpperCase()).filter(Boolean)));
    const order = [...NETWORKS, ...present.filter((p) => !(NETWORKS as readonly string[]).includes(p))];
    return order
      .map((net) => ({ net, list: accounts.filter((a) => (a.platform ?? '').toUpperCase() === net) }))
      .filter((g) => g.list.length > 0);
  }, [accounts]);

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

  return (
    <div>
      {/* encabezado de sección + acción primaria persistente */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ minWidth: 0 }}>
          <h2 style={{ fontSize: 17, fontWeight: 600, color: 'var(--fg-text-primary)', margin: 0 }}>Cuentas conectadas</h2>
          {total > 0 && (
            <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 3 }}>{subhead}</div>
          )}
        </div>
        {total > 0 && (
          <Button leftIcon={<PlusIcon />} onClick={openConnect}>
            Conectar red
          </Button>
        )}
      </div>

      {/* error de conexión (al iniciar el OAuth) */}
      {connectError && !connectOpen && (
        <div
          style={{
            marginTop: 16,
            fontSize: 13,
            color: 'var(--fg-danger-line)',
            background: 'var(--fg-danger-bg)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 13px',
          }}
        >
          {connectError}
        </div>
      )}

      <div style={{ marginTop: 18 }}>
        {total === 0 ? (
          <EmptyState
            icon={<NetworkGlyph />}
            title="Este cliente todavía no tiene redes conectadas"
            description="Conectá Instagram, Facebook o TikTok para empezar a traer sus métricas. Te llevamos al inicio de sesión oficial de la red; nunca guardamos la contraseña."
            action={
              <Button leftIcon={<PlusIcon />} onClick={openConnect}>
                Conectar red
              </Button>
            }
          />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            {groups.map((g) => (
              <div key={g.net}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 9 }}>
                  <NetworkChip platform={g.net} long />
                  <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>
                    {g.list.length} {g.list.length === 1 ? 'cuenta' : 'cuentas'}
                  </span>
                </div>
                <Card padding={0} style={{ overflow: 'hidden' }}>
                  {g.list.map((a, i) => (
                    <AccountRow
                      key={a.id}
                      account={a}
                      first={i === 0}
                      connecting={pending === (a.platform ?? '').toUpperCase()}
                      disconnecting={disconnect.isPending && disconnect.variables === a.id}
                      onReconnect={() => connect((a.platform ?? '').toUpperCase())}
                      onDisconnect={() => a.id != null && disconnect.mutate(a.id)}
                    />
                  ))}
                </Card>
              </div>
            ))}
            <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', textAlign: 'center', marginTop: 2 }}>
              Las cuentas conectadas alimentan el Dashboard, Publicaciones y el Reporte de este cliente.
            </div>
          </div>
        )}
      </div>

      {connectOpen && (
        <ConnectModal
          redirectNet={redirectNet}
          connecting={!!redirectNet && pending === redirectNet}
          error={connectError}
          onPick={pickNetwork}
          onBack={() => setRedirectNet(null)}
          onClose={closeConnect}
        />
      )}

      {/* atajo: reconexión enfocada (pantalla completa) si el operador la prefiere */}
      {brokenCount > 0 && (
        <div style={{ marginTop: 16, fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>
          ¿Varias cuentas caídas?{' '}
          <button
            type="button"
            onClick={() => navigate(`/clients/${id}/reconnect`)}
            style={{ background: 'none', border: 'none', padding: 0, color: 'var(--fg-primary)', fontWeight: 500, cursor: 'pointer', font: 'inherit' }}
          >
            Reconectarlas en una pantalla
          </button>
          .
        </div>
      )}
    </div>
  );
}

/** Fila de cuenta: red + nombre + @handle + estado (+ motivo) + acciones. */
function AccountRow({
  account,
  first,
  connecting,
  disconnecting,
  onReconnect,
  onDisconnect,
}: {
  account: AccountResponse;
  first: boolean;
  connecting: boolean;
  disconnecting: boolean;
  onReconnect: () => void;
  onDisconnect: () => void;
}) {
  const status = normStatus(account.status);
  const broken = isBroken(account.status);
  const primary = account.displayName || account.handle || `Cuenta ${account.id}`;
  const handle = account.handle && account.handle !== primary ? account.handle : null;
  const reason = status === 'ERROR' ? 'dejamos de traer métricas' : null;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        padding: '14px 16px',
        borderTop: first ? 'none' : '1px solid var(--fg-border)',
        flexWrap: 'wrap',
      }}
    >
      <NetworkChip platform={account.platform} />
      <div style={{ flex: 1, minWidth: 140 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9, flexWrap: 'wrap' }}>
          <span
            style={{
              fontSize: 14.5,
              fontWeight: 600,
              color: 'var(--fg-text-primary)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 240,
            }}
          >
            {primary}
          </span>
          {handle && <span style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>{handle}</span>}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 7, flexWrap: 'wrap' }}>
          <StatusPill status={account.status} />
          {reason && <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>{reason}</span>}
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 'none' }}>
        {broken && (
          <Button size="sm" loading={connecting} onClick={onReconnect}>
            Reconectar
          </Button>
        )}
        {status !== 'DISCONNECTED' && (
          <Button variant="secondary" size="sm" loading={disconnecting} onClick={onDisconnect}>
            Desconectar
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * Modal "Conectar una red". Paso 1: elegir red. Al elegir, `connect(net)` abre la
 * pantalla oficial de la red en otra pestaña; acá mostramos el estado de redirección.
 * Los pasos "elegir cuenta" (Meta multi-página) ocurren EN la red + backend, no acá.
 */
function ConnectModal({
  redirectNet,
  connecting,
  error,
  onPick,
  onBack,
  onClose,
}: {
  redirectNet: string | null;
  connecting: boolean;
  error: string | null;
  onPick: (net: string) => void;
  onBack: () => void;
  onClose: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(20,25,33,.42)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: 20,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: 440,
          maxWidth: '100%',
          background: 'var(--fg-bg-surface)',
          borderRadius: 'var(--fg-radius-lg)',
          boxShadow: '0 18px 50px rgba(20,25,33,.28)',
          overflow: 'hidden',
        }}
      >
        {!redirectNet ? (
          <>
            <div style={{ padding: '20px 22px 0', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
              <div>
                <div style={{ fontSize: 17, fontWeight: 600, color: 'var(--fg-text-primary)' }}>Conectar una red</div>
                <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 3 }}>
                  Elegí qué red querés conectar para este cliente.
                </div>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Cerrar"
                style={{ background: 'none', border: 'none', fontSize: 20, lineHeight: 1, color: 'var(--fg-text-tertiary)', cursor: 'pointer' }}
              >
                ×
              </button>
            </div>
            <div style={{ padding: '18px 22px 8px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {NETWORKS.map((net) => (
                <button
                  key={net}
                  type="button"
                  onClick={() => onPick(net)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 13,
                    border: '1px solid var(--fg-border)',
                    borderRadius: 11,
                    padding: '13px 15px',
                    background: 'var(--fg-bg-surface)',
                    cursor: 'pointer',
                    textAlign: 'left',
                    width: '100%',
                  }}
                >
                  <NetworkChip platform={net} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14.5, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                      {networkLabel(net, true)}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 2 }}>{NET_HINT[net]}</div>
                  </div>
                  <span style={{ fontSize: 18, color: 'var(--fg-text-tertiary)' }} aria-hidden>
                    ›
                  </span>
                </button>
              ))}
            </div>
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 8,
                margin: '8px 22px 0',
                padding: '12px 14px',
                background: 'var(--fg-blue-50)',
                borderRadius: 'var(--fg-radius)',
                fontSize: 12,
                color: 'var(--fg-text-secondary)',
                lineHeight: 1.5,
              }}
            >
              <span aria-hidden>ⓘ</span>
              <span>
                Instagram y Facebook requieren una cuenta <strong>profesional</strong> (Business o Creator). La
                contraseña se escribe siempre en la red, nunca en Fil-Grama.
              </span>
            </div>
            <div style={{ padding: '16px 22px 20px', display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="ghost" size="sm" onClick={onClose}>
                Cancelar
              </Button>
            </div>
          </>
        ) : (
          <div style={{ padding: '40px 28px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 14 }}>
            <div
              style={{
                width: 54,
                height: 54,
                borderRadius: 14,
                background: error ? 'var(--fg-danger-bg)' : 'var(--fg-blue-50)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <NetworkChip platform={redirectNet} />
            </div>
            {error ? (
              <>
                <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                  No pudimos abrir {networkLabel(redirectNet, true)}
                </div>
                <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 320 }}>{error}</div>
                <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
                  <Button variant="secondary" size="sm" onClick={onBack}>
                    Elegir otra red
                  </Button>
                  <Button size="sm" onClick={() => onPick(redirectNet)}>
                    Reintentar
                  </Button>
                </div>
              </>
            ) : (
              <>
                <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                  {connecting ? `Te llevamos a ${networkLabel(redirectNet, true)}…` : `Abrimos ${networkLabel(redirectNet, true)} en otra pestaña`}
                </div>
                <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 320 }}>
                  Iniciá sesión y autorizá el acceso en la pantalla oficial de la red. Al volver, la cuenta queda
                  conectada y aparece en la lista.
                </div>
                <Button variant="secondary" size="sm" onClick={onClose} style={{ marginTop: 4 }}>
                  Cerrar
                </Button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
