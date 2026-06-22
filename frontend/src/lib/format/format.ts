/**
 * Helpers de formato (es-PY). Números compactos, porcentajes, fechas y tendencias.
 * Reglas: valores ausentes → "—" (nunca "0" engañoso ni vacío crudo).
 */
const EMPTY = '—';

const plain = new Intl.NumberFormat('es');

export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  return plain.format(value);
}

/** 48200 → "48,2 mil" no; usamos estilo "48.2k" alineado a los diseños. */
export function formatCompact(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  if (Math.abs(value) < 1000) return plain.format(value);
  // Intl compact en 'es' da "48 mil"; preferimos el sufijo corto k/M de los diseños.
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1_000_000) return `${sign}${trim(abs / 1_000_000)}M`;
  return `${sign}${trim(abs / 1000)}k`;
}

function trim(n: number): string {
  return (Math.round(n * 10) / 10).toString().replace('.', ',');
}

export function formatPercent(value: number | null | undefined, digits = 1): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  return `${(Math.round(value * 10 ** digits) / 10 ** digits).toString().replace('.', ',')}%`;
}

/** Por unidad del catálogo: 'percent'/'ratio' → %, resto → compacto. */
export function formatByUnit(value: number | null | undefined, unit?: string | null): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  const u = (unit ?? '').toLowerCase();
  if (u === 'percent' || u === 'ratio' || u === '%') {
    return formatPercent(u === 'ratio' ? value * 100 : value);
  }
  return formatCompact(value);
}

const dateFmt = new Intl.DateTimeFormat('es', { day: 'numeric', month: 'short' });

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return EMPTY;
  return dateFmt.format(d);
}

export interface TrendInfo {
  /** 'up' | 'down' | 'flat' — dirección para colorear. */
  direction: 'up' | 'down' | 'flat';
  /** Texto listo, ej. "▲ 12%". null si no hay comparación. */
  label: string | null;
}

const dateTimeFmt = new Intl.DateTimeFormat('es', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

/** Fecha + hora corta (es). Para corridas de sync, timestamps de eventos, etc. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return EMPTY;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return EMPTY;
  return dateTimeFmt.format(d);
}

export function trendFromDelta(deltaPct: number | null | undefined): TrendInfo {
  if (deltaPct == null || Number.isNaN(deltaPct)) return { direction: 'flat', label: null };
  if (deltaPct > 0) return { direction: 'up', label: `▲ ${formatPercent(deltaPct)}` };
  if (deltaPct < 0) return { direction: 'down', label: `▼ ${formatPercent(Math.abs(deltaPct))}` };
  return { direction: 'flat', label: '0%' };
}

export { EMPTY as EMPTY_VALUE };
