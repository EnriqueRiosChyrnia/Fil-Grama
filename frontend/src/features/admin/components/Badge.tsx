import type { ReactNode } from 'react';

/** Chip de estado neutro (colores los decide quien lo usa, ej. `syncStatusMeta`). */
export function Badge({ children, bg, fg }: { children: ReactNode; bg: string; fg: string }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 22,
        padding: '0 9px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 500,
        background: bg,
        color: fg,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
}
