/**
 * Adaptadores summary → concepto CORE para Home/Dashboard (territorio FA).
 * El mapeo concepto→métricas vive compartido en `lib/metrics`; acá solo la
 * agregación sobre el `SummaryResponse`.
 */
import type { SummaryResponse } from '../../api/generated/model';
import type { CoreConcept } from '../../lib/catalog';
import { metricKeysForConcept } from '../../lib/metrics';

const isNum = (x: unknown): x is number => typeof x === 'number' && !Number.isNaN(x);

/** Número agregado del cliente para un concepto, sobre todas sus redes. null = sin datos. */
export function heroFromSummary(summary: SummaryResponse | undefined, concept: CoreConcept): number | null {
  const platforms = summary?.platforms;
  if (!platforms?.length) return null;

  if (concept === 'engagement') {
    const rates = platforms.map((p) => p.engagementRate).filter(isNum);
    return rates.length ? rates.reduce((a, b) => a + b, 0) / rates.length : null;
  }

  const useLatest = concept === 'seguidores'; // stock (último valor); flujos usan total
  let total = 0;
  let found = false;
  for (const p of platforms) {
    const keys = metricKeysForConcept(concept, p.platform ?? '');
    const metrics = p.metrics ?? [];
    // Las keys están en orden de preferencia: tomamos la PRIMERA presente por red
    // (no sumamos alternativas → evita doble conteo, ej. fb_page_views + _total).
    for (const key of keys) {
      const m = metrics.find((mm) => mm.metric === key);
      if (!m) continue;
      const v = useLatest ? m.latest ?? m.total : m.total ?? m.latest;
      if (isNum(v)) {
        total += v;
        found = true;
      }
      break;
    }
  }
  return found ? total : null;
}

/** Lista de redes presentes en el summary (para chips). */
export function platformsFromSummary(summary: SummaryResponse | undefined): string[] {
  return (summary?.platforms ?? []).map((p) => p.platform ?? '').filter(Boolean);
}
