import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError } from '../../lib/api';
import { Button, NetworkChip, networkLabel, Spinner } from '../../components/ui';
import { Isotipo } from '../../components/brand/Logo';
import { fetchPublicConnectLink, startPublicConnect, CONNECT_TOKEN_KEY, type ConnectedAccount } from './publicApi';

/** Redes que el cliente puede conectar cuando el link no fija una. */
const NETWORKS = ['INSTAGRAM', 'FACEBOOK', 'TIKTOK'] as const;

/**
 * Página PÚBLICA de conexión por link compartible (spec/09 "Link compartible",
 * track CV3 §1). El dueño de la cuenta la abre en SU navegador, sin login en
 * Fil-Grama: elige la red y autoriza en la pantalla oficial desde su propia sesión.
 *
 * Vive FUERA del guard de auth (ver `routes.tsx` → `publicRoutes`), por eso no usa
 * <AppLayout>: trae su propio marco de marca (como Login). Al conectar redirige la
 * MISMA pestaña al OAuth oficial; el callback del backend vuelve a `/connect/done`.
 */
export function PublicConnectPage() {
  const { token = '' } = useParams();
  const [params] = useSearchParams();
  // ?just=<red>: volvimos del callback tras conectar esa red → toast + lista fresca.
  const justNet = params.get('just');

  const linkQ = useQuery({
    queryKey: ['public-connect-link', token],
    queryFn: () => fetchPublicConnectLink(token),
    enabled: token.length > 0,
    retry: false,
  });

  const [connecting, setConnecting] = useState<string | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [finished, setFinished] = useState(false);

  const onConnect = async (platform: string) => {
    setConnectError(null);
    setConnecting(platform);
    try {
      const { authorizationUrl } = await startPublicConnect(token, platform);
      if (!authorizationUrl) throw new Error('No recibimos el enlace de autorización. Probá de nuevo.');
      // Guardamos el token ANTES de irnos: el callback vuelve a /connect/done y desde ahí retomamos
      // esta lista sin filtrar el token en la URL de la red. spec/09 §"Token client-side, no en el state".
      try {
        sessionStorage.setItem(CONNECT_TOKEN_KEY, token);
      } catch {
        /* sin sessionStorage el done cae en "ya podés cerrar" — no rompe el flujo */
      }
      window.location.assign(authorizationUrl); // redirige ESTA pestaña al OAuth oficial
    } catch (e) {
      setConnectError(e instanceof ApiError ? e.humanMessage : 'No pudimos abrir la red. Probá de nuevo.');
      setConnecting(null);
    }
  };

  const onFinish = () => {
    try {
      sessionStorage.removeItem(CONNECT_TOKEN_KEY);
    } catch {
      /* no-op */
    }
    setFinished(true);
  };

  return (
    <Shell>
      {finished ? (
        <Finished />
      ) : linkQ.isLoading ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '20px 0' }}>
          <Spinner />
          <span style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)' }}>Cargando…</span>
        </div>
      ) : linkQ.isError ? (
        <InvalidLink status={linkQ.error instanceof ApiError ? linkQ.error.status : 0} />
      ) : (
        <Connect
          clientName={linkQ.data?.clientName ?? ''}
          platform={linkQ.data?.platform ?? null}
          connectedAccounts={linkQ.data?.connectedAccounts ?? []}
          justNet={justNet}
          connecting={connecting}
          error={connectError}
          onConnect={onConnect}
          onFinish={onFinish}
        />
      )}
    </Shell>
  );
}

/** Marco centrado con la marca Fil-Grama (mismo aire que Login; sin barra de app). */
function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '60px 24px',
        background: 'radial-gradient(120% 90% at 50% -10%, #EAF0FA 0%, #F4F6FA 46%, #F4F6FA 100%)',
      }}
    >
      <div
        style={{
          width: 440,
          maxWidth: '100%',
          background: 'var(--fg-bg-surface)',
          border: '1px solid var(--fg-border)',
          borderRadius: 'var(--fg-radius-lg)',
          boxShadow: 'var(--fg-shadow-lg)',
          padding: '38px 40px 34px',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 11, marginBottom: 24 }}>
          <Isotipo size={48} shadow />
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--fg-text-primary)', letterSpacing: '-.3px' }}>Fil-Grama</div>
        </div>
        {children}
      </div>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="9" stroke="var(--fg-success-fg)" strokeWidth="1.7" />
      <path d="M8.5 12.2l2.4 2.3 4.6-4.8" stroke="var(--fg-success-fg)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Estado 200: lista abierta de onboarding multi-cuenta (spec/09 §"Onboarding multi-cuenta").
 * Muestra las cuentas ya conectadas + "conectar otra" + "terminé"; el callback vuelve acá, no a un
 * "listo" muerto, así el cliente conecta cuantas quiera.
 */
function Connect({
  clientName,
  platform,
  connectedAccounts,
  justNet,
  connecting,
  error,
  onConnect,
  onFinish,
}: {
  clientName: string;
  platform: string | null;
  connectedAccounts: ConnectedAccount[];
  justNet: string | null;
  connecting: string | null;
  error: string | null;
  onConnect: (platform: string) => void;
  onFinish: () => void;
}) {
  const fixed = platform ? platform.toUpperCase() : null;
  const nets = fixed ? [fixed] : [...NETWORKS];
  const hasAccounts = connectedAccounts.length > 0;

  return (
    <div>
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--fg-text-primary)', lineHeight: 1.4 }}>
          Conectá las redes de{' '}
          <span style={{ color: 'var(--fg-primary)' }}>{clientName || 'tu cuenta'}</span>
        </div>
        <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', lineHeight: 1.6, marginTop: 9 }}>
          {fixed
            ? `Autorizá ${networkLabel(fixed, true)} desde tu propia sesión. Te llevamos a la pantalla oficial de la red; nunca vemos tu contraseña.`
            : 'Conectá las cuentas que quieras, de cualquier red. Autorizás desde tu propia sesión, en la pantalla oficial de la red; nunca vemos tu contraseña.'}
        </div>
      </div>

      {justNet && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            marginBottom: 16,
            fontSize: 13,
            color: 'var(--fg-success-fg)',
            background: 'var(--fg-success-bg)',
            borderRadius: 'var(--fg-radius)',
            padding: '10px 13px',
          }}
        >
          <CheckIcon />
          <span>
            <strong>{networkLabel(justNet.toUpperCase(), true)}</strong> conectada. Podés conectar otra o
            terminar.
          </span>
        </div>
      )}

      {error && (
        <div
          style={{
            marginBottom: 16,
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

      {/* Cuentas conectadas hasta ahora (checklist abierto). */}
      <div style={{ marginBottom: 18 }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 9 }}>
          Cuentas conectadas hasta ahora
        </div>
        {hasAccounts ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {connectedAccounts.map((a, i) => (
              <div
                key={`${a.platform ?? ''}-${a.handle ?? ''}-${i}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 9,
                  padding: '9px 12px',
                  background: 'var(--fg-bg-muted)',
                  border: '1px solid var(--fg-border)',
                  borderRadius: 'var(--fg-radius)',
                }}
              >
                <NetworkChip platform={(a.platform ?? '').toUpperCase()} />
                <span style={{ flex: 1, minWidth: 0, fontSize: 13.5, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {a.handle || 'Cuenta conectada'}
                </span>
                <CheckIcon />
              </div>
            ))}
          </div>
        ) : (
          <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', padding: '10px 12px', background: 'var(--fg-bg-muted)', borderRadius: 'var(--fg-radius)' }}>
            Todavía ninguna. Conectá la primera abajo.
          </div>
        )}
      </div>

      {/* Conectar otra cuenta. */}
      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 9 }}>
        {hasAccounts ? 'Conectar otra cuenta' : 'Conectar una cuenta'}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {nets.map((net) => (
          <Button
            key={net}
            fullWidth
            size="lg"
            leftIcon={<NetworkChip platform={net} />}
            loading={connecting === net}
            disabled={connecting != null && connecting !== net}
            onClick={() => onConnect(net)}
          >
            Conectar {networkLabel(net, true)}
          </Button>
        ))}
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 8,
          marginTop: 14,
          padding: '10px 13px',
          background: 'var(--fg-warning-bg)',
          borderRadius: 'var(--fg-radius)',
          fontSize: 12.5,
          color: 'var(--fg-warning-fg)',
          lineHeight: 1.5,
        }}
      >
        <span aria-hidden>↔️</span>
        <span>
          Para conectar <strong>otra cuenta de la misma red</strong>, cambiá de cuenta en la pantalla de esa
          red antes de autorizar.
        </span>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 8,
          marginTop: 10,
          padding: '10px 13px',
          background: 'var(--fg-blue-50)',
          borderRadius: 'var(--fg-radius)',
          fontSize: 12.5,
          color: 'var(--fg-text-secondary)',
          lineHeight: 1.5,
        }}
      >
        <span aria-hidden>📱</span>
        <span>
          Si se abre la app de la red (por ejemplo TikTok), al terminar <strong>volvé a esta pestaña del
          navegador</strong> para finalizar la conexión.
        </span>
      </div>

      <Button variant="secondary" fullWidth onClick={onFinish} style={{ marginTop: 16 }}>
        Listo, terminé
      </Button>

      <div style={{ textAlign: 'center', fontSize: 11.5, color: 'var(--fg-text-tertiary)', lineHeight: 1.55, marginTop: 16 }}>
        Este enlace te lo compartió tu agencia. Al autorizar, la cuenta queda vinculada para sus reportes.
      </div>
    </div>
  );
}

/** Cierre del onboarding: el cliente tocó "Terminé". Sin callejón: agradece y permite cerrar. */
function Finished() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 14, padding: '8px 0' }}>
      <div
        style={{
          width: 58,
          height: 58,
          borderRadius: 15,
          background: 'var(--fg-success-bg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
          <circle cx="12" cy="12" r="9" stroke="var(--fg-success-fg)" strokeWidth="1.7" />
          <path d="M8.5 12.2l2.4 2.3 4.6-4.8" stroke="var(--fg-success-fg)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <div style={{ fontSize: 19, fontWeight: 600, color: 'var(--fg-text-primary)' }}>¡Gracias! Quedó todo conectado</div>
      <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 360 }}>
        Ya podés cerrar esta pestaña. La agencia verá las cuentas conectadas y empezará a traer sus métricas.
      </div>
    </div>
  );
}

/** Estados 404 (inexistente) y 410 (vencido/revocado): mensaje claro, sin datos sensibles. */
function InvalidLink({ status }: { status: number }) {
  const expired = status === 410;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: 12 }}>
      <div
        style={{
          width: 56,
          height: 56,
          borderRadius: 15,
          background: 'var(--fg-warning-bg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
          <circle cx="12" cy="12" r="9" stroke="var(--fg-warning-fg)" strokeWidth="1.7" />
          <path d="M12 7.5v5.5" stroke="var(--fg-warning-fg)" strokeWidth="1.7" strokeLinecap="round" />
          <circle cx="12" cy="16.2" r="1" fill="var(--fg-warning-fg)" />
        </svg>
      </div>
      <div style={{ fontSize: 17, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
        {expired ? 'Este enlace ya no es válido' : 'No encontramos este enlace'}
      </div>
      <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 320 }}>
        {expired
          ? 'El enlace venció o fue dado de baja. Pedile uno nuevo a la agencia para conectar tu red.'
          : 'El enlace no existe o está mal copiado. Pedile uno nuevo a la agencia.'}
      </div>
    </div>
  );
}
