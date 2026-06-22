import { Button } from '../../ui/Button';
import { ApiError } from '../../../lib/api';

function WarnIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M10.3 4.2 2.9 17.1A2 2 0 0 0 4.6 20h14.8a2 2 0 0 0 1.7-2.9L13.7 4.2a2 2 0 0 0-3.4 0Z"
        stroke="var(--fg-danger-line)"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <path d="M12 9.5v3.6" stroke="var(--fg-danger-line)" strokeWidth="1.7" strokeLinecap="round" />
      <circle cx="12" cy="16.3" r="1" fill="var(--fg-danger-line)" />
    </svg>
  );
}

/**
 * Estado de error reutilizable. Muestra SIEMPRE el mensaje humano (`humanMessage`
 * de ApiError o el `detail` de problem+json), nunca el stack ni el código crudo.
 */
export function ErrorState({
  error,
  title = 'No pudimos cargar esto',
  onRetry,
  minHeight = 220,
}: {
  error?: unknown;
  title?: string;
  onRetry?: () => void;
  minHeight?: number | string;
}) {
  const message =
    error instanceof ApiError
      ? error.humanMessage
      : error instanceof Error
        ? error.message
        : 'Ocurrió un error inesperado. Probá de nuevo.';

  return (
    <div
      style={{
        minHeight,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        gap: 12,
        padding: 24,
      }}
    >
      <div
        style={{
          width: 56,
          height: 56,
          borderRadius: 16,
          background: 'var(--fg-danger-bg)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <WarnIcon />
      </div>
      <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{title}</div>
      <div style={{ fontSize: 13.5, color: 'var(--fg-text-secondary)', maxWidth: 420, lineHeight: 1.55 }}>
        {message}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry} style={{ marginTop: 4 }}>
          Reintentar
        </Button>
      )}
    </div>
  );
}
