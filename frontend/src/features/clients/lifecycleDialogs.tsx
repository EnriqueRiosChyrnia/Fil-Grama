import { useEffect, useRef, useState } from 'react';
import { Button, NetworkChip, networkLabel } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { Dialog } from './Dialog';
import { useCreateConnectLink } from './mutations';

/**
 * Diálogos del ciclo de vida de cuenta (track CV3, spec/09):
 *  - ReauthDialog: cuando `reconnect` responde `requiresReauth`, ofrece las dos vías
 *    (la agencia re-autoriza, o se manda un link al cliente).
 *  - ConnectLinkModal: genera el link compartible y lo muestra con botón Copiar.
 *  - DeleteAccountDialog: confirmación de la baja (solo admin).
 */

/** Fila-opción clickeable dentro de un diálogo. */
function OptionRow({
  icon,
  title,
  desc,
  onClick,
  loading,
  disabled,
}: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  onClick: () => void;
  loading?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 13,
        border: '1px solid var(--fg-border)',
        borderRadius: 11,
        padding: '13px 15px',
        background: 'var(--fg-bg-surface)',
        cursor: disabled || loading ? 'default' : 'pointer',
        textAlign: 'left',
        width: '100%',
        opacity: disabled ? 0.55 : 1,
      }}
    >
      <span style={{ flex: 'none', display: 'flex' }}>{icon}</span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: 14.5, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{title}</span>
        <span style={{ display: 'block', fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 2, lineHeight: 1.5 }}>{desc}</span>
      </span>
      <span style={{ fontSize: 18, color: 'var(--fg-text-tertiary)' }} aria-hidden>
        {loading ? '…' : '›'}
      </span>
    </button>
  );
}

/**
 * `reconnect` pidió re-autorización (token muerto). Dos vías que elige la agencia:
 * (a) reconectar la agencia desde su sesión; (b) mandarle un link al cliente.
 */
export function ReauthDialog({
  platform,
  accountLabel,
  reconnecting,
  onReconnectSelf,
  onSendLink,
  onClose,
}: {
  platform: string;
  accountLabel: string;
  reconnecting: boolean;
  onReconnectSelf: () => void;
  onSendLink: () => void;
  onClose: () => void;
}) {
  const net = platform.toUpperCase();
  return (
    <Dialog
      title="Esta cuenta necesita re-autorizarse"
      subtitle={
        <>
          El acceso a <strong>{accountLabel}</strong> ({networkLabel(net, true)}) caducó. Elegí cómo volver a
          habilitarla.
        </>
      }
      onClose={onClose}
      footer={
        <Button variant="ghost" size="sm" onClick={onClose}>
          Cancelar
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <OptionRow
          icon={<NetworkChip platform={net} />}
          title="Reconectar yo"
          desc="Te llevamos a la pantalla oficial de la red para autorizar desde tu sesión."
          loading={reconnecting}
          onClick={onReconnectSelf}
        />
        <OptionRow
          icon={
            <span style={{ width: 26, height: 26, borderRadius: 7, background: 'var(--fg-blue-50)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden>
                <path d="M9 15l11-11M20 4v6M20 4h-6" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" stroke="var(--fg-primary)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </span>
          }
          title="Enviar link al cliente"
          desc="Generás un enlace para que el dueño de la cuenta la reconecte desde su propio navegador."
          disabled={reconnecting}
          onClick={onSendLink}
        />
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 8,
          marginTop: 12,
          padding: '11px 13px',
          background: 'var(--fg-blue-50)',
          borderRadius: 'var(--fg-radius)',
          fontSize: 12,
          color: 'var(--fg-text-secondary)',
          lineHeight: 1.5,
        }}
      >
        <span aria-hidden>ⓘ</span>
        <span>
          Si reconectás vos y tu navegador sigue logueado con otra cuenta, usá incógnito o cerrá sesión en la
          red: reconectamos exactamente la cuenta esperada.
        </span>
      </div>
    </Dialog>
  );
}

function CopyIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="1.7" />
      <path d="M5 15V5a2 2 0 0 1 2-2h8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

/**
 * Genera y muestra el link compartible. Al abrir, dispara la creación (el usuario ya
 * pidió "generar"); muestra el progreso, y al volver la `url` ofrece copiarla + el
 * aviso de vencimiento. `platform`/`accountId` opcionales fijan red / reconexión.
 */
export function ConnectLinkModal({
  clientId,
  platform,
  accountId,
  title = 'Link de conexión para el cliente',
  onClose,
}: {
  clientId: number;
  platform?: string;
  accountId?: number;
  title?: string;
  onClose: () => void;
}) {
  const create = useCreateConnectLink(clientId);
  const { mutate } = create;
  const [copied, setCopied] = useState(false);
  const created = useRef(false);

  // Crear el link una sola vez al abrir. El ref persiste entre el setup/cleanup/setup
  // que StrictMode hace en dev, así no generamos dos links.
  useEffect(() => {
    if (created.current) return;
    created.current = true;
    mutate({ platform, accountId });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const url = create.data?.url ?? '';

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  const subtitle = platform
    ? `Compartí este enlace con el dueño de la cuenta de ${networkLabel(platform.toUpperCase(), true)}. Lo abre en su navegador y autoriza desde su sesión.`
    : 'Compartí este enlace con el cliente. Lo abre en su navegador, elige la red y autoriza desde su propia sesión.';

  return (
    <Dialog
      title={title}
      subtitle={subtitle}
      width={480}
      onClose={onClose}
      footer={
        <Button variant="ghost" size="sm" onClick={onClose}>
          Cerrar
        </Button>
      }
    >
      {create.isPending || (!create.data && !create.isError) ? (
        <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', padding: '8px 0' }}>Generando el enlace…</div>
      ) : create.isError ? (
        <div>
          <div
            style={{
              fontSize: 13,
              color: 'var(--fg-danger-fg)',
              background: 'var(--fg-danger-bg)',
              border: '1px solid var(--fg-danger-border)',
              borderRadius: 'var(--fg-radius)',
              padding: '10px 13px',
              lineHeight: 1.45,
            }}
          >
            {create.error instanceof ApiError ? create.error.humanMessage : 'No pudimos generar el enlace. Probá de nuevo.'}
          </div>
          <div style={{ marginTop: 12 }}>
            <Button size="sm" onClick={() => mutate({ platform, accountId })}>
              Reintentar
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'stretch' }}>
            <div
              style={{
                flex: 1,
                minWidth: 0,
                border: '1px solid var(--fg-border)',
                borderRadius: 'var(--fg-radius)',
                padding: '10px 12px',
                fontSize: 13,
                color: 'var(--fg-text-primary)',
                background: 'var(--fg-bg-muted)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                display: 'flex',
                alignItems: 'center',
              }}
              title={url}
            >
              {url}
            </div>
            <Button size="md" leftIcon={<CopyIcon />} onClick={copy}>
              {copied ? 'Copiado ✓' : 'Copiar'}
            </Button>
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 8,
              marginTop: 13,
              padding: '11px 13px',
              background: 'var(--fg-warning-bg)',
              borderRadius: 'var(--fg-radius)',
              fontSize: 12,
              color: 'var(--fg-warning-fg)',
              lineHeight: 1.5,
            }}
          >
            <span aria-hidden>⏱</span>
            <span>
              El enlace vence en <strong>72 horas</strong> y se puede usar varias veces hasta entonces. Pasado ese
              plazo, generá uno nuevo.
            </span>
          </div>
        </div>
      )}
    </Dialog>
  );
}

/** Confirmación de baja de cuenta (solo admin). Acción destructiva. */
export function DeleteAccountDialog({
  accountLabel,
  deleting,
  error,
  onConfirm,
  onClose,
}: {
  accountLabel: string;
  deleting: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog
      title="Dar de baja la cuenta"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" size="sm" onClick={onClose} disabled={deleting}>
            Cancelar
          </Button>
          <Button variant="danger" size="sm" loading={deleting} onClick={onConfirm}>
            Dar de baja
          </Button>
        </>
      }
    >
      <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', lineHeight: 1.6 }}>
        Vas a dar de baja <strong style={{ color: 'var(--fg-text-primary)' }}>{accountLabel}</strong>. Esto revoca el
        acceso a la red y borra la credencial; <strong>la historia se conserva</strong> (los reportes pasados siguen
        funcionando). Para volver a usarla, habrá que conectarla de nuevo.
      </div>
      {error && (
        <div
          style={{
            marginTop: 12,
            fontSize: 13,
            color: 'var(--fg-danger-fg)',
            background: 'var(--fg-danger-bg)',
            border: '1px solid var(--fg-danger-border)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 13px',
            lineHeight: 1.45,
          }}
        >
          {error}
        </div>
      )}
    </Dialog>
  );
}
