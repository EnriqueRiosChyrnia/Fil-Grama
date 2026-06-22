import { useEffect, type ReactNode } from 'react';

/** Diálogo modal liviano (no hay primitiva compartida). Cierra con Esc y backdrop. */
export function Modal({
  open,
  title,
  onClose,
  children,
  footer,
  width = 460,
}: {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(20,25,33,.42)',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        padding: '8vh 16px 16px',
        zIndex: 50,
        overflowY: 'auto',
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: width,
          background: 'var(--fg-bg-surface)',
          border: '1px solid var(--fg-border)',
          borderRadius: 'var(--fg-radius-lg)',
          boxShadow: 'var(--fg-shadow-lg)',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '17px 20px',
            borderBottom: '1px solid var(--fg-border)',
          }}
        >
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{title}</div>
          <button
            type="button"
            aria-label="Cerrar"
            onClick={onClose}
            style={{
              border: 'none',
              background: 'transparent',
              color: 'var(--fg-text-tertiary)',
              fontSize: 20,
              lineHeight: 1,
              cursor: 'pointer',
              padding: 4,
            }}
          >
            ×
          </button>
        </div>
        <div style={{ padding: 20 }}>{children}</div>
        {footer && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 10,
              padding: '14px 20px',
              borderTop: '1px solid var(--fg-border)',
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
