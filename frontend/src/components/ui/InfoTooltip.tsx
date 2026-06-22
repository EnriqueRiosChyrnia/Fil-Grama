import { useState, type ReactNode } from 'react';

/**
 * Ícono ⓘ con popover (hover + foco). Para explicar métricas en lenguaje humano
 * (HANDOFF §10). El texto del cuerpo sale del catálogo/glosario, nunca de la API.
 */
export function InfoTooltip({
  title,
  children,
  size = 16,
  align = 'left',
}: {
  title?: ReactNode;
  children: ReactNode;
  size?: number;
  align?: 'left' | 'right';
}) {
  const [open, setOpen] = useState(false);
  return (
    <span
      style={{ position: 'relative', display: 'inline-flex' }}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        aria-label={typeof title === 'string' ? `Qué significa: ${title}` : 'Más información'}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        style={{
          display: 'inline-flex',
          width: size,
          height: size,
          alignItems: 'center',
          justifyContent: 'center',
          border: '1px solid var(--fg-border-strong)',
          borderRadius: '50%',
          fontSize: Math.round(size * 0.56),
          color: 'var(--fg-text-secondary)',
          background: 'transparent',
          cursor: 'help',
          padding: 0,
          lineHeight: 1,
        }}
      >
        i
      </button>
      {open && (
        <span
          role="tooltip"
          style={{
            position: 'absolute',
            top: size + 9,
            [align]: -10,
            width: 248,
            background: 'var(--fg-bg-surface)',
            border: '1px solid var(--fg-border)',
            borderRadius: '11px',
            boxShadow: 'var(--fg-shadow-pop)',
            padding: '13px 15px',
            zIndex: 30,
            textAlign: 'left',
          }}
        >
          {title && (
            <span style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--fg-text-primary)', marginBottom: 4 }}>
              {title}
            </span>
          )}
          <span style={{ display: 'block', fontSize: 12.5, color: 'var(--fg-text-secondary)', lineHeight: 1.5 }}>
            {children}
          </span>
        </span>
      )}
    </span>
  );
}
