import { useEffect, useState } from 'react';
import { Button, NetworkChip, networkLabel } from '../../components/ui';
import { ApiError } from '../../lib/api';
import { Dialog } from './Dialog';
import { useCreateConnectLink } from './mutations';
import { BrandedQr } from './BrandedQr';
import { buildQrCard } from './qrCard';

/** Estado de la mutation del link compartible, dueño en la página (ver ConnectLinkModal). */
type CreateLink = ReturnType<typeof useCreateConnectLink>;

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

function DownloadIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path d="M12 4v10m0 0 4-4m-4 4-4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5 18h14" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

function ImageIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="4" y="5" width="16" height="14" rx="2" stroke="currentColor" strokeWidth="1.7" />
      <circle cx="9" cy="10" r="1.6" fill="currentColor" />
      <path d="m5 17 4.5-4 3 2.5L16 12l3 3.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** Recuerda el override "azul Fil-Grama" entre aperturas (único uso de localStorage). */
const QR_BRAND_KEY = 'fg.qr.use-brand';
function readBrandOverride(): boolean {
  try {
    return localStorage.getItem(QR_BRAND_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * Genera y muestra el link compartible. Al abrir, dispara la creación (el usuario ya
 * pidió "generar"); muestra el progreso, y al volver la `url` ofrece copiarla + el
 * aviso de vencimiento. `platform`/`accountId` opcionales fijan red / reconexión.
 */
export function ConnectLinkModal({
  create,
  platform,
  accountId,
  clientName,
  title = 'Link de conexión para el cliente',
  onClose,
}: {
  /** Mutation dueña en la página: se dispara al abrir el modal (en el onClick), no acá.
   *  Disparar en un useEffect + ref se cuelga bajo StrictMode (el observer del re-montaje
   *  queda idle y el ref bloquea el re-disparo). El modal es solo presentacional. */
  create: CreateLink;
  platform?: string;
  accountId?: number;
  /** Nombre del cliente para el header de la tarjeta compartible. */
  clientName?: string;
  title?: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const [imgCopied, setImgCopied] = useState(false);
  const [busy, setBusy] = useState<'download' | 'image' | null>(null);
  const [cardError, setCardError] = useState<string | null>(null);
  // Override "azul Fil-Grama": solo aplica cuando hay red; sin red el QR ya es neutro.
  const [brandOverride, setBrandOverride] = useState(readBrandOverride);
  useEffect(() => {
    try {
      localStorage.setItem(QR_BRAND_KEY, brandOverride ? '1' : '0');
    } catch {
      /* sin persistencia: no es crítico */
    }
  }, [brandOverride]);

  const url = create.data?.url ?? '';
  const forceBrand = !platform || brandOverride;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  const cardFileName = () => {
    const net = (platform ?? '').toUpperCase();
    const tag = !brandOverride && net ? net.toLowerCase() : 'fil-grama';
    return `conexion-${tag}.png`;
  };

  const downloadCard = async () => {
    setBusy('download');
    setCardError(null);
    try {
      const blob = await buildQrCard({ url, platform, forceBrand, clientName });
      const href = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = href;
      a.download = cardFileName();
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.setTimeout(() => URL.revokeObjectURL(href), 1000);
    } catch {
      setCardError('No pudimos generar la imagen. Probá de nuevo.');
    } finally {
      setBusy(null);
    }
  };

  const copyImage = async () => {
    setBusy('image');
    setCardError(null);
    try {
      if (typeof ClipboardItem === 'undefined' || !navigator.clipboard?.write) {
        throw new Error('clipboard sin soporte de imagen');
      }
      const blob = await buildQrCard({ url, platform, forceBrand, clientName });
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      setImgCopied(true);
      window.setTimeout(() => setImgCopied(false), 2000);
    } catch {
      setCardError('Tu navegador no dejó copiar la imagen. Usá “Descargar” y adjuntala.');
    } finally {
      setBusy(null);
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
            <Button size="sm" onClick={() => create.mutate({ platform, accountId })}>
              Reintentar
            </Button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {/* QR de marca centrado (estilo por red o azul Fil-Grama según override). */}
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <div
              style={{
                padding: 14,
                background: '#FFFFFF',
                border: '1px solid var(--fg-border)',
                borderRadius: 14,
                boxShadow: '0 2px 10px rgba(15,63,120,.06)',
              }}
            >
              <BrandedQr url={url} platform={platform} forceBrand={forceBrand} size={216} />
            </div>
          </div>

          {/* Override a azul de marca: solo tiene sentido cuando hay una red fija. */}
          {platform && (
            <label
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
                fontSize: 12.5,
                color: 'var(--fg-text-secondary)',
                cursor: 'pointer',
              }}
            >
              <input
                type="checkbox"
                checked={brandOverride}
                onChange={(e) => setBrandOverride(e.target.checked)}
                style={{ accentColor: 'var(--fg-primary)', width: 15, height: 15 }}
              />
              Usar azul Fil-Grama (en vez del estilo {networkLabel(platform.toUpperCase(), true)})
            </label>
          )}

          {/* Acciones de imagen: tarjeta lista para WhatsApp. */}
          <div style={{ display: 'flex', gap: 8 }}>
            <Button
              size="md"
              leftIcon={<DownloadIcon />}
              onClick={downloadCard}
              loading={busy === 'download'}
              disabled={busy != null}
              style={{ flex: 1 }}
            >
              Descargar
            </Button>
            <Button
              size="md"
              variant="secondary"
              leftIcon={<ImageIcon />}
              onClick={copyImage}
              loading={busy === 'image'}
              disabled={busy != null}
              style={{ flex: 1 }}
            >
              {imgCopied ? 'Copiado ✓' : 'Copiar imagen'}
            </Button>
          </div>

          {/* Link en texto + copiar (fallback si la cámara no engancha). */}
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
            <Button size="md" variant="secondary" leftIcon={<CopyIcon />} onClick={copy}>
              {copied ? 'Copiado ✓' : 'Copiar link'}
            </Button>
          </div>

          {cardError && (
            <div
              style={{
                fontSize: 12.5,
                color: 'var(--fg-danger-fg)',
                background: 'var(--fg-danger-bg)',
                border: '1px solid var(--fg-danger-border)',
                borderRadius: 'var(--fg-radius)',
                padding: '9px 12px',
                lineHeight: 1.45,
              }}
            >
              {cardError}
            </div>
          )}

          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 8,
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
