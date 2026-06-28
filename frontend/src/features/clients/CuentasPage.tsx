import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useGetClientsClientIdAccounts, useGetClientsId } from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { Button, Card, NetworkChip, networkLabel } from '../../components/ui';
import { EmptyState, ErrorState, LoadingState } from '../../components/layout';
import { useAuth } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import { StatusPill } from './clientBits';
import { isBroken, normStatus, NETWORKS } from './accountStatus';
import { useConnectFlow, useDisconnectAccount, useReconnectAccount, useDeleteAccount, useCreateConnectLink } from './mutations';
import { ReauthDialog, ConnectLinkModal, DeleteAccountDialog } from './lifecycleDialogs';

/** Etiqueta humana de una cuenta (nombre > handle > id). */
function acctLabel(a: AccountResponse): string {
  return a.displayName || a.handle || `Cuenta ${a.id}`;
}
function humanError(e: unknown): string {
  if (e instanceof ApiError) return e.humanMessage;
  if (e instanceof Error) return e.message;
  return 'Ocurrió un error. Probá de nuevo.';
}

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

function LinkIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path d="M10 13a3.5 3.5 0 0 0 5 0l3-3a3.5 3.5 0 1 0-5-5l-1.5 1.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 11a3.5 3.5 0 0 0-5 0l-3 3a3.5 3.5 0 1 0 5 5l1.5-1.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
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

/** Glyph "todas las redes" (mini grafo) para la opción genérica del selector. */
function AllNetsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="6" cy="12" r="2.2" fill="var(--fg-primary)" />
      <circle cx="17" cy="6.5" r="2.2" fill="var(--fg-primary)" />
      <circle cx="17" cy="17.5" r="2.2" fill="var(--fg-primary)" />
      <path d="M8 11 15 7M8 13l7 4" stroke="var(--fg-primary)" strokeWidth="1.4" />
    </svg>
  );
}

/** Opciones del selector de red al generar el link. `value` undefined = link genérico (azul). */
const LINK_NET_OPTIONS: { value?: string; label: string; sub: string }[] = [
  { value: undefined, label: 'Todas las redes', sub: 'El cliente elige · QR azul Fil-Grama' },
  { value: 'INSTAGRAM', label: 'Instagram', sub: 'Link de Instagram · QR de la red' },
  { value: 'FACEBOOK', label: 'Facebook', sub: 'Link de Facebook · QR de la red' },
  { value: 'TIKTOK', label: 'TikTok', sub: 'Link de TikTok · QR de la red' },
];

/**
 * Botón "Generar link" con selector de red (spec/09 §"Link compartible"/"QR"): la agencia elige para
 * qué red es el link —o "todas" (genérico)— ANTES de crearlo, así no se generan tokens al pedo. El
 * connect-link recién se crea al elegir (vía `onPick`); el modal sale con el estilo de QR correcto.
 */
function GenerateLinkMenu({ onPick }: { onPick: (platform?: string) => void }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const choose = (platform?: string) => {
    setOpen(false);
    onPick(platform);
  };

  return (
    <div style={{ position: 'relative', display: 'inline-block' }}>
      <Button
        variant="secondary"
        leftIcon={<LinkIcon />}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        Generar link
      </Button>
      {open && (
        <>
          {/* Capa para cerrar al click fuera. */}
          <div onClick={() => setOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 49 }} />
          <div
            role="menu"
            style={{
              position: 'absolute',
              top: 'calc(100% + 6px)',
              right: 0,
              zIndex: 50,
              width: 270,
              background: 'var(--fg-bg-surface)',
              border: '1px solid var(--fg-border)',
              borderRadius: 'var(--fg-radius)',
              boxShadow: 'var(--fg-shadow-lg)',
              padding: 7,
            }}
          >
            <div
              style={{
                fontSize: 11.5,
                fontWeight: 600,
                color: 'var(--fg-text-tertiary)',
                textTransform: 'uppercase',
                letterSpacing: '.04em',
                padding: '6px 9px 8px',
              }}
            >
              ¿Para qué red es el link?
            </div>
            {LINK_NET_OPTIONS.map((opt) => (
              <button
                key={opt.value ?? 'ALL'}
                type="button"
                role="menuitem"
                onClick={() => choose(opt.value)}
                onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--fg-bg-muted)')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  width: '100%',
                  textAlign: 'left',
                  background: 'transparent',
                  border: 'none',
                  borderRadius: 'var(--fg-radius-sm)',
                  padding: '9px',
                  cursor: 'pointer',
                }}
              >
                <span style={{ width: 26, display: 'flex', justifyContent: 'center', flexShrink: 0 }}>
                  {opt.value ? <NetworkChip platform={opt.value} /> : <AllNetsIcon />}
                </span>
                <span style={{ minWidth: 0 }}>
                  <span style={{ display: 'block', fontSize: 13.5, fontWeight: 500, color: 'var(--fg-text-primary)' }}>{opt.label}</span>
                  <span style={{ display: 'block', fontSize: 11.5, color: 'var(--fg-text-tertiary)' }}>{opt.sub}</span>
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export function CuentasPage() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();

  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });
  const accounts: AccountResponse[] = useMemo(() => accountsQ.data?.data ?? [], [accountsQ.data]);
  // Nombre del cliente para el header de la tarjeta del QR (cacheado: el layout ya lo trae).
  const clientQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const clientName = clientQ.data?.data?.name;

  const { isAdmin } = useAuth();
  const { connect, pending, error: connectError } = useConnectFlow(id);
  const disconnect = useDisconnectAccount(id);
  const reconnect = useReconnectAccount(id);
  const del = useDeleteAccount(id);
  const createLink = useCreateConnectLink(id);

  // Feedback efímero de éxito ("Reactivada", "Dada de baja") y error de operación.
  const [flash, setFlash] = useState<string | null>(null);
  const [opError, setOpError] = useState<string | null>(null);
  useEffect(() => {
    if (!flash) return;
    const t = window.setTimeout(() => setFlash(null), 4000);
    return () => window.clearTimeout(t);
  }, [flash]);

  // Diálogos del ciclo de vida.
  const [reauth, setReauth] = useState<AccountResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AccountResponse | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  // Modal del link compartible: null = cerrado; objeto = abierto con su contexto.
  const [linkModal, setLinkModal] = useState<{ platform?: string; accountId?: number; title?: string } | null>(null);
  // Abrir el modal = disparar la mutation acá, en el handler del click (evento, NO un
  // useEffect): así el observer de React Query vive en esta página estable y el resultado
  // no se pierde con el doble-montaje de StrictMode. El modal solo muestra el estado.
  const openLinkModal = (opts: { platform?: string; accountId?: number; title?: string }) => {
    createLink.reset();
    createLink.mutate({ platform: opts.platform, accountId: opts.accountId });
    setLinkModal(opts);
  };

  // Reconexión inteligente: un POST decide. Si el token vive, el backend reactiva sin
  // OAuth → toast. Si murió (`requiresReauth`) → diálogo con las dos vías.
  const handleReconnect = (a: AccountResponse) => {
    if (a.id == null) return;
    setOpError(null);
    setFlash(null);
    reconnect.mutate(a.id, {
      onSuccess: (res) => {
        if (res?.requiresReauth) setReauth(a);
        else setFlash(`Reactivamos ${acctLabel(a)}.`);
      },
      onError: (e) => setOpError(humanError(e)),
    });
  };

  // Baja (solo admin): tras confirmar, DELETE + refresco; conserva la historia.
  const handleDelete = () => {
    if (deleteTarget?.id == null) return;
    setDeleteError(null);
    const label = acctLabel(deleteTarget);
    del.mutate(deleteTarget.id, {
      onSuccess: () => {
        setDeleteTarget(null);
        setFlash(`Diste de baja ${label}. Su historia se conserva.`);
      },
      onError: (e) => setDeleteError(humanError(e)),
    });
  };

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
          <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
            <GenerateLinkMenu onPick={(platform) => openLinkModal({ platform, title: 'Link de conexión para el cliente' })} />
            <Button leftIcon={<PlusIcon />} onClick={openConnect}>
              Conectar red
            </Button>
          </div>
        )}
      </div>

      {/* feedback efímero de éxito (reactivar / dar de baja) */}
      {flash && (
        <div
          role="status"
          style={{
            marginTop: 16,
            fontSize: 13,
            color: 'var(--fg-success-fg)',
            background: 'var(--fg-success-bg)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 13px',
          }}
        >
          {flash}
        </div>
      )}

      {/* error de operación (reconectar / dar de baja) */}
      {opError && (
        <div
          role="alert"
          style={{
            marginTop: 16,
            fontSize: 13,
            color: 'var(--fg-danger-line)',
            background: 'var(--fg-danger-bg)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 13px',
          }}
        >
          {opError}
        </div>
      )}

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
              <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap', justifyContent: 'center' }}>
                <Button leftIcon={<PlusIcon />} onClick={openConnect}>
                  Conectar red
                </Button>
                <GenerateLinkMenu onPick={(platform) => openLinkModal({ platform, title: 'Link de conexión para el cliente' })} />
              </div>
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
                      isAdmin={isAdmin}
                      reconnecting={reconnect.isPending && reconnect.variables === a.id}
                      disconnecting={disconnect.isPending && disconnect.variables === a.id}
                      deleting={del.isPending && del.variables === a.id}
                      onReconnect={() => handleReconnect(a)}
                      onDisconnect={() => a.id != null && disconnect.mutate(a.id)}
                      onDelete={() => {
                        setDeleteError(null);
                        setDeleteTarget(a);
                      }}
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

      {/* token muerto: elegir re-autorizar la agencia o mandar link al cliente */}
      {reauth && (
        <ReauthDialog
          platform={(reauth.platform ?? '').toUpperCase()}
          accountLabel={acctLabel(reauth)}
          reconnecting={pending === (reauth.platform ?? '').toUpperCase()}
          onReconnectSelf={() => {
            const a = reauth;
            setReauth(null);
            connect((a.platform ?? '').toUpperCase(), a.id);
          }}
          onSendLink={() => {
            const a = reauth;
            setReauth(null);
            openLinkModal({
              platform: (a.platform ?? '').toUpperCase() || undefined,
              accountId: a.id,
              title: 'Reconectar con un link',
            });
          }}
          onClose={() => setReauth(null)}
        />
      )}

      {/* baja de cuenta (solo admin): confirmación destructiva */}
      {deleteTarget && (
        <DeleteAccountDialog
          accountLabel={acctLabel(deleteTarget)}
          deleting={del.isPending}
          error={deleteError}
          onConfirm={handleDelete}
          onClose={() => {
            if (del.isPending) return;
            setDeleteTarget(null);
            setDeleteError(null);
          }}
        />
      )}

      {/* link compartible: generar + copiar */}
      {linkModal && (
        <ConnectLinkModal
          create={createLink}
          platform={linkModal.platform}
          accountId={linkModal.accountId}
          clientName={clientName}
          title={linkModal.title}
          onClose={() => setLinkModal(null)}
        />
      )}
    </div>
  );
}

/** Fila de cuenta: red + nombre + @handle + estado (+ motivo) + acciones. */
function AccountRow({
  account,
  first,
  isAdmin,
  reconnecting,
  disconnecting,
  deleting,
  onReconnect,
  onDisconnect,
  onDelete,
}: {
  account: AccountResponse;
  first: boolean;
  isAdmin: boolean;
  reconnecting: boolean;
  disconnecting: boolean;
  deleting: boolean;
  onReconnect: () => void;
  onDisconnect: () => void;
  onDelete: () => void;
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 'none', flexWrap: 'wrap' }}>
        {broken && (
          <Button size="sm" loading={reconnecting} onClick={onReconnect}>
            Reconectar
          </Button>
        )}
        {/* Desconectar (pausar) solo tiene sentido en una cuenta activa; no en ERROR/DISCONNECTED. */}
        {status === 'CONNECTED' && (
          <Button variant="secondary" size="sm" loading={disconnecting} onClick={onDisconnect}>
            Desconectar
          </Button>
        )}
        {/* Dar de baja: solo admin (oculto a empleados, no deshabilitado). */}
        {isAdmin && (
          <Button variant="ghost" size="sm" loading={deleting} onClick={onDelete} style={{ color: 'var(--fg-danger-line)' }}>
            Eliminar
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
