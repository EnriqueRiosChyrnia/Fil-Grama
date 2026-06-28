import { useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Isotipo } from '../../components/brand/Logo';
import { Button, Spinner, networkLabel } from '../../components/ui';
import { CONNECT_TOKEN_KEY } from './publicApi';

/**
 * Página PÚBLICA de resultado del onboarding por link (track CV3 §2, spec/09 §"Onboarding
 * multi-cuenta"). El callback del backend redirige acá (`origin=link`) con `?status=ok&net=` o
 * `?status=error&reason=`. Para NO ser un callejón sin salida: si hay token en `sessionStorage`
 * (lo guardó la lista antes del OAuth) volvemos a `/connect/{token}?just=<net>` para que el cliente
 * vea la cuenta nueva y conecte otra. Sin token → agradecemos y se cierra la pestaña.
 */
const ERROR_COPY: Record<string, string> = {
  unsupported_personal:
    'Esa cuenta es personal. Para conectar Instagram o Facebook se necesita una cuenta profesional (Business o Creator).',
  invalid_state: 'El enlace expiró o ya no es válido. Pedile uno nuevo a la agencia.',
  expired: 'El enlace expiró. Pedile uno nuevo a la agencia.',
  access_denied: 'Se canceló la autorización. La cuenta no quedó conectada.',
  account_mismatch: 'Autorizaste con otra cuenta. Cerrá sesión en la red e intentá de nuevo con la cuenta correcta.',
};

export function ConnectDonePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const ok = params.get('status') === 'ok';
  const net = params.get('net');
  const reason = params.get('reason');
  const netLabel = net ? networkLabel(net.toUpperCase(), true) : null;
  // Token del tab (lo dejó la lista antes de redirigir al OAuth); jamás vino por la URL de la red.
  const token = useMemo(() => {
    try {
      return sessionStorage.getItem(CONNECT_TOKEN_KEY);
    } catch {
      return null;
    }
  }, []);

  // Éxito con token → volver a la lista con ?just=<net> (no callejón): aparece la cuenta nueva.
  const redirecting = ok && !!token;
  useEffect(() => {
    if (redirecting) {
      const q = net ? `?just=${encodeURIComponent(net)}` : '';
      navigate(`/connect/${encodeURIComponent(token as string)}${q}`, { replace: true });
    }
  }, [redirecting, token, net, navigate]);

  return (
    <Shell>
      {redirecting ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '16px 0' }}>
          <Spinner />
          <span style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)' }}>Volviendo a tus cuentas…</span>
        </div>
      ) : ok ? (
        <Result
          ok
          title={netLabel ? `¡Listo! ${netLabel} conectada` : '¡Listo! Ya conectaste tu red'}
          message="Ya podés cerrar esta pestaña. La agencia verá la cuenta conectada y empezará a traer sus métricas."
        />
      ) : (
        <Result
          ok={false}
          title="No pudimos conectar tu red"
          message={(reason && ERROR_COPY[reason]) ?? 'Hubo un problema durante la autorización. Probá de nuevo con el enlace de la agencia o pedile uno nuevo.'}
          action={
            token ? (
              <Button variant="secondary" onClick={() => navigate(`/connect/${encodeURIComponent(token)}`)}>
                Volver a intentar
              </Button>
            ) : undefined
          }
        />
      )}
    </Shell>
  );
}

/** Marco centrado con la marca Fil-Grama (igual aire que la lista pública / Login). */
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
          padding: '40px 40px 34px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          textAlign: 'center',
          gap: 14,
        }}
      >
        <Isotipo size={44} shadow />
        {children}
      </div>
    </div>
  );
}

function Result({ ok, title, message, action }: { ok: boolean; title: string; message: string; action?: React.ReactNode }) {
  return (
    <>
      <div
        style={{
          width: 58,
          height: 58,
          borderRadius: 15,
          marginTop: 4,
          background: ok ? 'var(--fg-success-bg)' : 'var(--fg-danger-bg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {ok ? (
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
            <circle cx="12" cy="12" r="9" stroke="var(--fg-success-fg)" strokeWidth="1.7" />
            <path d="M8.5 12.2l2.4 2.3 4.6-4.8" stroke="var(--fg-success-fg)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : (
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
            <circle cx="12" cy="12" r="9" stroke="var(--fg-danger-line)" strokeWidth="1.7" />
            <path d="M12 7.5v5.5" stroke="var(--fg-danger-line)" strokeWidth="1.7" strokeLinecap="round" />
            <circle cx="12" cy="16.2" r="1" fill="var(--fg-danger-line)" />
          </svg>
        )}
      </div>

      <div style={{ fontSize: 19, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{title}</div>
      <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 360 }}>{message}</div>
      {action && <div style={{ marginTop: 4 }}>{action}</div>}
    </>
  );
}
