import type { CSSProperties, ReactNode } from 'react';

/** Superficie blanca estándar (borde + radio + sombra suave). */
export function Card({
  children,
  padding = 18,
  style,
  onClick,
  interactive,
}: {
  children: ReactNode;
  padding?: number | string;
  style?: CSSProperties;
  onClick?: () => void;
  interactive?: boolean;
}) {
  return (
    <div
      onClick={onClick}
      style={{
        background: 'var(--fg-bg-surface)',
        border: '1px solid var(--fg-border)',
        borderRadius: 'var(--fg-radius-md)',
        boxShadow: 'var(--fg-shadow-sm)',
        padding,
        cursor: interactive || onClick ? 'pointer' : undefined,
        transition: 'border-color .15s, box-shadow .15s',
        ...style,
      }}
    >
      {children}
    </div>
  );
}
