import { useEffect, type ReactNode } from 'react';

/**
 * Diálogo modal centrado para el ciclo de vida de cuentas (reauth / link / baja).
 * Mismo lenguaje visual que el ConnectModal de CuentasPage; local al feature para no
 * acoplar con `admin/components/Modal`. Cierra con Esc y backdrop.
 */
export function Dialog({
  title,
  subtitle,
  onClose,
  children,
  footer,
  width = 440,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={typeof title === 'string' ? title : undefined}
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
          width,
          maxWidth: '100%',
          background: 'var(--fg-bg-surface)',
          borderRadius: 'var(--fg-radius-lg)',
          boxShadow: '0 18px 50px rgba(20,25,33,.28)',
          overflow: 'hidden',
        }}
      >
        <div style={{ padding: '20px 22px 0', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 17, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{title}</div>
            {subtitle && (
              <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 4, lineHeight: 1.5 }}>{subtitle}</div>
            )}
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
        <div style={{ padding: '18px 22px 8px' }}>{children}</div>
        <div style={{ padding: '8px 22px 20px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>{footer}</div>
      </div>
    </div>
  );
}
