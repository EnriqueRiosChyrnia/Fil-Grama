/** Rangos de fecha estándar (Home 7d; Dashboard 7/30/90; Comparar 30d). */
export type RangeDays = 7 | 30 | 90;

export interface DateRange {
  days: RangeDays;
  /** ISO date (YYYY-MM-DD) inclusivo de inicio. */
  from: string;
  /** ISO date (YYYY-MM-DD) de fin (hoy). */
  to: string;
  label: string;
}

const LABELS: Record<RangeDays, string> = {
  7: 'Últimos 7 días',
  30: 'Últimos 30 días',
  90: 'Últimos 90 días',
};

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Construye el rango {from,to} para los últimos N días terminando hoy. */
export function computeRange(days: RangeDays, now: Date = new Date()): DateRange {
  const to = new Date(now);
  const from = new Date(now);
  from.setDate(from.getDate() - (days - 1));
  return { days, from: isoDate(from), to: isoDate(to), label: LABELS[days] };
}

export const RANGE_LABEL = LABELS;
