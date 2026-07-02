import type { ReactNode } from 'react';
import { formatPercent } from '../../../lib/format';

/**
 * Piezas mínimas para los bloques v1.1 (Público/Contenido/Interacciones), locales
 * a `features/reports` — no tocan `components/ui` (API congelada).
 */

export function BlockSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--fg-border)' }}>
      <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--fg-text-primary)', marginBottom: 10 }}>{title}</div>
      {children}
    </div>
  );
}

export function SubLabel({ children }: { children: ReactNode }) {
  return <div style={{ fontSize: 11.5, color: 'var(--fg-text-tertiary)', marginBottom: 8 }}>{children}</div>;
}

/** Fila de barra horizontal con % (demografía, tipo de contenido, split). `pct` es ratio 0..1. */
export function BarRow({ label, pct }: { label: string; pct: number }) {
  const width = Math.max(0, Math.min(1, pct)) * 100;
  return (
    <div style={{ marginBottom: 8 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, fontSize: 12, color: 'var(--fg-text-secondary)', marginBottom: 3 }}>
        <span>{label}</span>
        <span style={{ fontWeight: 600, color: 'var(--fg-text-primary)' }}>{formatPercent(pct * 100)}</span>
      </div>
      <div style={{ height: 6, borderRadius: 4, background: 'var(--fg-gray-100)', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${width}%`, background: 'var(--fg-blue-500)', borderRadius: 4 }} />
      </div>
    </div>
  );
}

/** Fila etiqueta/valor simple (interacciones por acción, actividad de perfil). */
export function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, fontSize: 12.5, color: 'var(--fg-text-secondary)', padding: '5px 0' }}>
      <span>{label}</span>
      <span style={{ fontWeight: 600, color: 'var(--fg-text-primary)' }}>{value}</span>
    </div>
  );
}
