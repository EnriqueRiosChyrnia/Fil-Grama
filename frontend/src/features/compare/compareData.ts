/**
 * Lógica de "Comparar cuentas" (HANDOFF §11, territorio FC). Compara cuentas
 * INDIVIDUALES (hasta 4, incluso varias de la misma red), nunca agregado por red.
 *
 * Fuente: `/accounts/{id}/metrics` por cuenta+concepto (totales del rango). El
 * mapeo concepto→métrica y la comparabilidad viven en `lib/metrics` (frozen); acá
 * sólo la agregación de la serie y el armado de filas/columnas.
 *
 * Engagement no tiene key cruda en el catálogo (es un % derivado). Como `/summary`
 * agrega por RED y no por cuenta, no hay fuente per-cuenta para el engagement; lo
 * derivamos = interacciones / alcance · 100 (la misma definición humana del
 * tooltip del concepto). Ver nota de coordinación en el reporte de cierre.
 */
import type { CoreConcept } from '../../lib/catalog';
import type { AccountResponse, SeriesPoint } from '../../api/generated/model';
import { primaryMetricKey, isComparable } from '../../lib/metrics';

/** Las 4 métricas de Comparar (HANDOFF §11), en orden de despliegue. */
export const COMPARE_CONCEPTS: CoreConcept[] = ['alcance', 'seguidores', 'interacciones', 'engagement'];

/** Conceptos con key cruda en el catálogo (engagement se deriva, no se pide). */
export const RAW_CONCEPTS: Exclude<CoreConcept, 'engagement'>[] = ['alcance', 'seguidores', 'interacciones'];

/** Tope de cuentas comparables a la vez (HANDOFF §11). */
export const MAX_ACCOUNTS = 4;

const isNum = (x: unknown): x is number => typeof x === 'number' && !Number.isNaN(x);

/**
 * Total del rango para una serie. Stock (seguidores) → último valor conocido;
 * flujos (alcance, interacciones) → suma. Mismo criterio que el `clientMetrics`
 * compartido, para que Comparar no contradiga al Dashboard.
 */
export function aggregateSeries(points: SeriesPoint[] | undefined, concept: CoreConcept): number | null {
  if (!points || points.length === 0) return null;
  if (concept === 'seguidores') {
    for (let i = points.length - 1; i >= 0; i--) {
      if (isNum(points[i].value)) return points[i].value as number;
    }
    return null;
  }
  let sum = 0;
  let found = false;
  for (const p of points) {
    if (isNum(p.value)) {
      sum += p.value as number;
      found = true;
    }
  }
  return found ? sum : null;
}

/** Cuenta normalizada (id + platform presentes) seleccionable para comparar. */
export interface CompareAccount {
  id: number;
  platform: string;
  label: string;
  status?: string;
}

export function toCompareAccount(a: AccountResponse): CompareAccount | null {
  if (a.id == null || !a.platform) return null;
  return {
    id: a.id,
    platform: a.platform,
    label: a.handle || a.displayName || `Cuenta ${a.id}`,
    status: a.status,
  };
}

/** Una consulta `/accounts/{id}/metrics` a disparar (concepto con key cruda). */
export interface MetricQueryItem {
  accountId: number;
  platform: string;
  concept: Exclude<CoreConcept, 'engagement'>;
  metric: string;
}

/** Items {cuenta, concepto, metric} para los conceptos con key cruda. */
export function buildMetricQueryItems(accounts: CompareAccount[]): MetricQueryItem[] {
  const items: MetricQueryItem[] = [];
  for (const acc of accounts) {
    for (const concept of RAW_CONCEPTS) {
      const metric = primaryMetricKey(concept, acc.platform);
      if (metric) items.push({ accountId: acc.id, platform: acc.platform, concept, metric });
    }
  }
  return items;
}

/** totals[accountId][concept] = número | null (incluye engagement derivado). */
export type Totals = Record<number, Partial<Record<CoreConcept, number | null>>>;

/** Engagement derivado per-cuenta = interacciones / alcance · 100. */
export function deriveEngagement(
  reach: number | null | undefined,
  interactions: number | null | undefined,
): number | null {
  if (!isNum(reach) || reach <= 0 || !isNum(interactions)) return null;
  return (interactions / reach) * 100;
}

export interface Cell {
  /** ¿La métrica es equivalente entre redes en esta cuenta? (false ⇒ "—" + ⓘ). */
  comparable: boolean;
  value: number | null;
}

export function cellFor(concept: CoreConcept, platform: string, row: Totals[number] | undefined): Cell {
  if (!isComparable(concept, platform)) return { comparable: false, value: null };
  return { comparable: true, value: row?.[concept] ?? null };
}

/** Mejor (máximo) valor comparable de un concepto entre las cuentas (para negrita). */
export function bestValue(concept: CoreConcept, accounts: CompareAccount[], totals: Totals): number | null {
  let best: number | null = null;
  for (const acc of accounts) {
    const c = cellFor(concept, acc.platform, totals[acc.id]);
    if (c.comparable && isNum(c.value) && (best == null || c.value > best)) best = c.value;
  }
  return best;
}
