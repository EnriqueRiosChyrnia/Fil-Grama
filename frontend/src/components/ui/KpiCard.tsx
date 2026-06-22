import type { ReactNode } from 'react';
import { Card } from './Card';
import { InfoTooltip } from './InfoTooltip';
import type { TrendInfo } from '../../lib/format';

function TrendLabel({ trend, size = 13 }: { trend?: TrendInfo | null; size?: number }) {
  if (!trend?.label) return null;
  const color = trend.direction === 'up' ? 'var(--fg-primary)' : 'var(--fg-text-tertiary)';
  return <span style={{ color, fontSize: size, fontWeight: 500 }}>{trend.label}</span>;
}

export interface KpiCardProps {
  label: ReactNode;
  value: ReactNode;
  /** Texto del tooltip ⓘ (opcional). */
  info?: ReactNode;
  trend?: TrendInfo | null;
  /** Variante grande (número 46px) para el KPI principal del Dashboard. */
  hero?: boolean;
  /** Nota debajo del número, ej. "vs. período anterior". */
  caption?: ReactNode;
  /** Slot a la derecha del header (ej. rango). */
  headerRight?: ReactNode;
  children?: ReactNode;
}

/** Tarjeta de KPI: etiqueta + ⓘ + número grande + tendencia. */
export function KpiCard({ label, value, info, trend, hero, caption, headerRight, children }: KpiCardProps) {
  const numberSize = hero ? 46 : 28;
  return (
    <Card padding={hero ? 22 : 18}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
          <span style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>{label}</span>
          {info && (
            <InfoTooltip title={typeof label === 'string' ? label : undefined}>{info}</InfoTooltip>
          )}
        </div>
        {headerRight && <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>{headerRight}</span>}
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: hero ? 13 : 10, marginTop: hero ? 9 : 11 }}>
        <span
          style={{
            fontSize: numberSize,
            fontWeight: 600,
            color: 'var(--fg-text-primary)',
            letterSpacing: hero ? '-1.2px' : '-.6px',
            lineHeight: 1,
          }}
        >
          {value}
        </span>
        <TrendLabel trend={trend} size={hero ? 15 : 13} />
      </div>
      {caption && <div style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', marginTop: 7 }}>{caption}</div>}
      {children}
    </Card>
  );
}
