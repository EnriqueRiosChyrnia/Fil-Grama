/**
 * Mapeo CONGELADO concepto-CORE → métricas concretas del catálogo, por red.
 * Compartido (lo necesitan FA Home/Dashboard, FB cuentas, FC Comparar). Formalizado
 * sobre `/metrics` (catálogo real del backend). Si el catálogo cambia, actualizar acá.
 *
 * Conceptos CORE (HANDOFF §8/§11): alcance, seguidores, interacciones, engagement.
 * Nivel ACCOUNT (lo que agrega `/clients/{id}/summary`); 'engagement' es derivado
 * (PlatformSummary.engagementRate), sin key cruda.
 *
 * Equivalencia entre redes (HANDOFF §11): el "alcance" de TikTok = reproducciones
 * (no es alcance único) → `isComparable('alcance','TIKTOK') === false`.
 */
import type { CoreConcept } from '../catalog';

export type Platform = string;

interface ConceptEntry {
  /** Metric keys del catálogo que representan el concepto en esa red (orden = preferencia). */
  keys: string[];
  /** ¿El valor es equivalente entre redes para Comparar? (HANDOFF §11) */
  comparable: boolean;
}

type PlatformMap = Partial<Record<'INSTAGRAM' | 'FACEBOOK' | 'TIKTOK', ConceptEntry>>;

const MAP: Record<CoreConcept, PlatformMap> = {
  alcance: {
    INSTAGRAM: { keys: ['ig_reach'], comparable: true },
    FACEBOOK: { keys: ['fb_page_views', 'fb_page_views_total'], comparable: true },
    // TikTok account no tiene alcance real; reproducciones (post) como proxy → NO comparable.
    TIKTOK: { keys: ['tt_view_count'], comparable: false },
  },
  seguidores: {
    INSTAGRAM: { keys: ['ig_followers_count'], comparable: true },
    FACEBOOK: { keys: ['fb_page_fan_adds'], comparable: true }, // catálogo FB solo expone altas
    TIKTOK: { keys: ['tt_follower_count'], comparable: true },
  },
  interacciones: {
    INSTAGRAM: { keys: ['ig_total_interactions', 'ig_accounts_engaged'], comparable: true },
    FACEBOOK: { keys: ['fb_page_post_engagements'], comparable: true },
    TIKTOK: { keys: ['tt_likes_count'], comparable: true },
  },
  // Derivado (rate %); sin key cruda. Comparable como porcentaje entre redes.
  engagement: {
    INSTAGRAM: { keys: [], comparable: true },
    FACEBOOK: { keys: [], comparable: true },
    TIKTOK: { keys: [], comparable: true },
  },
};

function entry(concept: CoreConcept, platform: Platform): ConceptEntry | undefined {
  return MAP[concept]?.[platform.toUpperCase() as keyof PlatformMap];
}

/** Metric keys del catálogo que representan el concepto en esa red ([] si no aplica). */
export function metricKeysForConcept(concept: CoreConcept, platform: Platform): string[] {
  return entry(concept, platform)?.keys ?? [];
}

/** Métrica principal (primera) del concepto en esa red, o null si no hay key cruda. */
export function primaryMetricKey(concept: CoreConcept, platform: Platform): string | null {
  return entry(concept, platform)?.keys[0] ?? null;
}

/** ¿El concepto es comparable entre redes en esa red? (false ⇒ marcar "—" + "no comparable"). */
export function isComparable(concept: CoreConcept, platform: Platform): boolean {
  return entry(concept, platform)?.comparable ?? false;
}
