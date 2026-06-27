import { useSearchParams } from 'react-router-dom';
import { Isotipo } from '../../components/brand/Logo';

/**
 * Página PÚBLICA de resultado del onboarding por link (track CV3 §2). El callback
 * del backend redirige acá cuando el flujo vino de un connect-link (`origin=link`,
 * spec/09): el cliente no tiene sesión en la app, así que esta pantalla es pública y
 * sin acciones de navegación internas. Lee `?status=ok|error&reason=`.
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
  const ok = params.get('status') === 'ok';
  const reason = params.get('reason');

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

        <div style={{ fontSize: 19, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
          {ok ? '¡Listo! Ya conectaste tu red' : 'No pudimos conectar tu red'}
        </div>
        <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 360 }}>
          {ok
            ? 'Ya podés cerrar esta pestaña. La agencia verá la cuenta conectada y empezará a traer sus métricas.'
            : (reason && ERROR_COPY[reason]) ??
              'Hubo un problema durante la autorización. Probá de nuevo con el enlace de la agencia o pedile uno nuevo.'}
        </div>
      </div>
    </div>
  );
}
