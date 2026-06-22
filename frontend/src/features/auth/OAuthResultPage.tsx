import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Card } from '../../components/ui';

/**
 * Resultado del onboarding OAuth (HANDOFF §9). El backend redirige acá con
 * `?accountId=` (éxito) o `?error=` (ej. unsupported_personal, invalid_state).
 * Esta ruta es del esqueleto (auth); el track FA (clientes) refinará el CTA de
 * "ver cuentas del cliente" cuando tenga el contexto del cliente.
 */
const ERROR_COPY: Record<string, string> = {
  unsupported_personal:
    'Esa cuenta es personal. Para conectar Instagram o Facebook necesitás una cuenta profesional (Business/Creator).',
  invalid_state: 'El enlace de conexión expiró o no es válido. Volvé a iniciar la conexión desde el cliente.',
  access_denied: 'Se canceló la autorización. La cuenta no quedó conectada.',
};

export function OAuthResultPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const accountId = params.get('accountId');
  const errorCode = params.get('error');
  const isSuccess = !!accountId && !errorCode;

  return (
    <div style={{ maxWidth: 560, margin: '8vh auto 0' }}>
      <Card padding="40px 30px 30px" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div
          style={{
            width: 60,
            height: 60,
            borderRadius: 16,
            background: isSuccess ? 'var(--fg-success-bg)' : 'var(--fg-danger-bg)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {isSuccess ? (
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

        <div style={{ fontSize: 19, fontWeight: 600, color: 'var(--fg-text-primary)', marginTop: 15 }}>
          {isSuccess ? 'Cuenta conectada' : 'No se pudo conectar la cuenta'}
        </div>
        <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 440, marginTop: 9 }}>
          {isSuccess
            ? 'Ya quedó vinculada. Las primeras métricas aparecen tras la próxima captura diaria.'
            : (errorCode && ERROR_COPY[errorCode]) ?? 'Hubo un problema durante la autorización. Volvé a intentarlo desde el cliente.'}
        </div>

        <div style={{ marginTop: 18 }}>
          <Button onClick={() => navigate('/')}>Ir a clientes</Button>
        </div>
      </Card>
    </div>
  );
}
