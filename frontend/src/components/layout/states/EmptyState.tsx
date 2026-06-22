import type { ReactNode } from 'react';

export type EmptyTone = 'info' | 'warning';

/**
 * Estado vacío AMABLE (HANDOFF §7): bloque cálido, nunca tabla vacía ni error crudo.
 * `tone='warning'` para "sin datos por problema de conexión" (todas las cuentas en error).
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  tone = 'info',
  children,
}: {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  tone?: EmptyTone;
  children?: ReactNode;
}) {
  const iconBg = tone === 'warning' ? 'var(--fg-danger-bg)' : 'var(--fg-blue-50)';
  return (
    <div
      style={{
        background: 'var(--fg-bg-surface)',
        border: '1px solid var(--fg-border)',
        borderRadius: 'var(--fg-radius-lg)',
        padding: '44px 28px 30px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
      }}
    >
      {icon && (
        <div
          style={{
            width: 64,
            height: 64,
            borderRadius: 18,
            background: iconBg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 18,
          }}
        >
          {icon}
        </div>
      )}
      <div style={{ fontSize: 20, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.2px' }}>
        {title}
      </div>
      {description && (
        <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)', lineHeight: 1.6, maxWidth: 540, marginTop: 10 }}>
          {description}
        </div>
      )}
      {action && <div style={{ marginTop: 18 }}>{action}</div>}
      {children}
    </div>
  );
}
