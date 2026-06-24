/**
 * Rangos de fecha estándar (Home 7d; Dashboard 7/30/90; Comparar 30d).
 * Además de los días numéricos hay `365` ("1 año") y el sentinel `'all'`
 * ("Todo / desde el inicio") para que aparezcan publicaciones/series viejas.
 * Set ancho reusable en `WIDE_RANGES` (detalle de cuenta / posts).
 */
export type RangeDays = 7 | 30 | 90 | 365 | 'all';

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
  365: 'Último año',
  all: 'Todo el período',
};

/**
 * `from` para "Todo": fecha base lejana que precede a cualquier red social
 * (Facebook nació en 2004), así el backend devuelve el histórico completo.
 * No usamos la fecha de conexión porque no siempre la tenemos a mano y una
 * base fija mantiene `computeRange` puro y compartido.
 */
const ALL_TIME_FROM = '2004-01-01';

/** Set ancho de rangos para pantallas con histórico (detalle de cuenta / posts). */
export const WIDE_RANGES: RangeDays[] = [7, 30, 90, 365, 'all'];

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Construye el rango {from,to} para los últimos N días terminando hoy. */
export function computeRange(days: RangeDays, now: Date = new Date()): DateRange {
  const to = new Date(now);
  if (days === 'all') {
    return { days, from: ALL_TIME_FROM, to: isoDate(to), label: LABELS.all };
  }
  const from = new Date(now);
  from.setDate(from.getDate() - (days - 1));
  return { days, from: isoDate(from), to: isoDate(to), label: LABELS[days] };
}

export const RANGE_LABEL = LABELS;
