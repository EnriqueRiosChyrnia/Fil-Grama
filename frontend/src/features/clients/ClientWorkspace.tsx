import { Link, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  useGetClientsId,
  useGetClientsClientIdAccounts,
} from '../../api/generated/endpoints';
import type { AccountResponse } from '../../api/generated/model';
import { NetworkChip, networkLabel } from '../../components/ui';
import { Skeleton } from '../../components/layout';
import { isBroken } from './accountStatus';

/**
 * Layout del workspace de cliente (track central de navegación). Reemplaza el
 * breadcrumb suelto de cada pantalla por un ENCABEZADO PERSISTENTE + barra de
 * pestañas (Dashboard · Publicaciones · Comparar · Reporte). Envuelve todas las
 * pantallas client-scoped vía <Outlet/>, así el header no se re-monta al cambiar
 * de pestaña (diseño "Workspace de cliente", supersede HANDOFF §8 breadcrumb).
 */

type TabKey = 'dashboard' | 'publicaciones' | 'comparar' | 'reporte';

/** Pestaña activa según la ruta. Detalle/posts de cuenta mantienen el contexto:
 *  el grid de posts cuelga de Publicaciones; el detalle de cuenta, de Dashboard. */
function activeTab(rest: string): TabKey {
  if (rest.startsWith('/publicaciones')) return 'publicaciones';
  if (rest.endsWith('/posts')) return 'publicaciones';
  if (rest.startsWith('/compare')) return 'comparar';
  if (rest.startsWith('/report')) return 'reporte';
  return 'dashboard'; // index + /accounts/:id (detalle de cuenta)
}

function HeaderChip({ account }: { account: AccountResponse }) {
  const broken = isBroken(account.status);
  const name = account.handle || account.displayName || `Cuenta ${account.id}`;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 11.5,
        fontWeight: 500,
        color: broken ? 'var(--fg-danger-fg)' : 'var(--fg-text-secondary)',
        background: broken ? 'var(--fg-danger-bg)' : 'var(--fg-bg-surface)',
        border: `1px solid ${broken ? 'var(--fg-danger-border)' : 'var(--fg-border)'}`,
        borderRadius: 6,
        padding: '3px 9px',
        whiteSpace: 'nowrap',
      }}
    >
      <span
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: broken ? 'var(--fg-danger-line)' : 'var(--fg-success-fg)',
        }}
      />
      <NetworkChip platform={account.platform} />
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 140 }}>{name}</span>
    </span>
  );
}

export function ClientWorkspace() {
  const { clientId } = useParams();
  const id = Number(clientId);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const base = `/clients/${id}`;
  const rest = pathname.startsWith(base) ? pathname.slice(base.length) : pathname;
  const tab = activeTab(rest);

  const detailQ = useGetClientsId(id, { query: { enabled: Number.isFinite(id) } });
  const accountsQ = useGetClientsClientIdAccounts(id, { query: { enabled: Number.isFinite(id) } });

  const detail = detailQ.data?.data;
  const accounts: AccountResponse[] = accountsQ.data?.data ?? [];
  const broken = accounts.filter((a) => isBroken(a.status));
  const brokenNets = Array.from(new Set(broken.map((a) => (a.platform ?? '').toUpperCase()).filter(Boolean)));
  const reconnectLabel =
    brokenNets.length === 1 ? `Reconectar ${networkLabel(brokenNets[0])}` : 'Reconectar cuentas';

  const tabs: { key: TabKey; label: string; to: string }[] = [
    { key: 'dashboard', label: 'Dashboard', to: base },
    { key: 'publicaciones', label: 'Publicaciones', to: `${base}/publicaciones` },
    { key: 'comparar', label: 'Comparar', to: `${base}/compare` },
    { key: 'reporte', label: 'Reporte', to: `${base}/report` },
  ];

  return (
    <div>
      {/* hides the horizontal scrollbar of the tab bar on mobile */}
      <style>{`.fg-tabscroll{scrollbar-width:none}.fg-tabscroll::-webkit-scrollbar{display:none}`}</style>

      {/* breadcrumb */}
      <Link
        to="/"
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)' }}
      >
        <span style={{ fontSize: 15 }}>‹</span>
        <span>Clientes</span>
      </Link>

      {/* client identity */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 16,
          marginTop: 13,
          flexWrap: 'wrap',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 }}>
          <div
            style={{
              width: 46,
              height: 46,
              borderRadius: 11,
              background: 'var(--fg-blue-50)',
              color: 'var(--fg-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              fontWeight: 600,
              flex: 'none',
            }}
          >
            {(detail?.name ?? '?').slice(0, 2).toUpperCase()}
          </div>
          <div style={{ minWidth: 0 }}>
            {detailQ.isLoading ? (
              <Skeleton width={180} height={22} />
            ) : (
              <h1
                style={{
                  fontSize: 22,
                  fontWeight: 600,
                  color: 'var(--fg-text-primary)',
                  letterSpacing: '-.3px',
                  margin: 0,
                }}
              >
                {detail?.name ?? 'Cliente'}
              </h1>
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginTop: 8, flexWrap: 'wrap' }}>
              {accounts.length === 0 ? (
                <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>Sin redes conectadas</span>
              ) : (
                accounts.map((a) => <HeaderChip key={a.id} account={a} />)
              )}
            </div>
          </div>
        </div>

        {broken.length > 0 && (
          <button
            type="button"
            onClick={() => navigate(`${base}/reconnect`)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              height: 38,
              padding: '0 15px',
              background: 'var(--fg-danger-bg)',
              border: '1px solid var(--fg-danger-border)',
              borderRadius: 9,
              fontSize: 13,
              fontWeight: 500,
              color: 'var(--fg-danger-fg)',
              cursor: 'pointer',
              flex: 'none',
            }}
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden>
              <path
                d="M12 7a5 5 0 1 1-1.5-3.5M12 1v3H9"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            {reconnectLabel}
          </button>
        )}
      </div>

      {/* tabs */}
      <div
        className="fg-tabscroll"
        role="tablist"
        style={{
          display: 'flex',
          gap: 4,
          marginTop: 16,
          overflowX: 'auto',
          borderBottom: '1px solid var(--fg-border)',
        }}
      >
        {tabs.map((t) => {
          const on = t.key === tab;
          return (
            <Link
              key={t.key}
              to={t.to}
              role="tab"
              aria-selected={on}
              style={{
                flex: 'none',
                padding: '11px 16px',
                fontSize: 14,
                fontWeight: on ? 600 : 400,
                color: on ? 'var(--fg-primary)' : 'var(--fg-text-secondary)',
                borderBottom: `2px solid ${on ? 'var(--fg-primary)' : 'transparent'}`,
                marginBottom: -1,
                whiteSpace: 'nowrap',
                textDecoration: 'none',
              }}
            >
              {t.label}
            </Link>
          );
        })}
      </div>

      {/* tab body */}
      <div style={{ marginTop: 22 }}>
        <Outlet />
      </div>
    </div>
  );
}
