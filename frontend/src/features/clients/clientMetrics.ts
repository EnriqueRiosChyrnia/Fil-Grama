/**
 * Adaptadores summary → concepto CORE (Alcance/Seguidores/Interacciones/Engagement).
 *
 * ⚠️ BEST-EFFORT del esqueleto: el mapeo concepto→métricas concretas por red es
 * trabajo del track FA/FB sobre el catálogo. Acá hay heurísticas para que el molde
 * muestre un número cuando hay datos; con backend sin captura, devuelven null → "—".
 */
import type { SummaryResponse } from '../../api/generated/model';
import type { CoreConcept } from '../../lib/catalog';

const KEYWORDS: Record<CoreConcept, string[]> = {
  alcance: ['reach', 'alcance', 'impr', 'view', 'vista', 'play'],
  interacciones: ['engagement', 'interac', 'like', 'comment', 'reaction'],
  seguidores: ['follower', 'fan', 'seguidor'],
  engagement: [],
};

export function heroFromSummary(summary: SummaryResponse | undefined, concept: CoreConcept): number | null {
  const platforms = summary?.platforms;
  if (!platforms?.length) return null;

  if (concept === 'engagement') {
    const rates = platforms.map((p) => p.engagementRate).filter((x): x is number => x != null);
    if (!rates.length) return null;
    return rates.reduce((a, b) => a + b, 0) / rates.length;
  }

  if (concept === 'seguidores') {
    const growth = platforms.map((p) => p.followerGrowth).filter((x): x is number => x != null);
    if (growth.length) return growth.reduce((a, b) => a + b, 0);
  }

  const kw = KEYWORDS[concept];
  let total = 0;
  let found = false;
  for (const p of platforms) {
    for (const m of p.metrics ?? []) {
      const key = (m.metric ?? '').toLowerCase();
      if (kw.some((k) => key.includes(k))) {
        total += m.total ?? m.latest ?? 0;
        found = true;
      }
    }
  }
  return found ? total : null;
}

/** Lista de redes presentes en el summary (para chips). */
export function platformsFromSummary(summary: SummaryResponse | undefined): string[] {
  return (summary?.platforms ?? []).map((p) => p.platform ?? '').filter(Boolean);
}

/** Elige una métrica concreta del catálogo para la tendencia/sparkline de una red. */
export function pickTrendMetric(
  items: { key?: string; platform?: string; level?: string; tier?: string }[],
  platform?: string,
): string | undefined {
  if (!platform) return undefined;
  const p = platform.toUpperCase();
  const candidates = items.filter((m) => (m.platform ?? '').toUpperCase() === p && m.level === 'ACCOUNT');
  const reachy = candidates.find((m) => /reach|view|impr/i.test(m.key ?? ''));
  return (reachy ?? candidates.find((m) => m.tier === 'CORE') ?? candidates[0])?.key;
}
