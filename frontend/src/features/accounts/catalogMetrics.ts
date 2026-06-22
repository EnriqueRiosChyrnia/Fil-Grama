/**
 * Helpers dirigidos por el catálogo (`/metrics`) para el track Cuentas & Posts.
 * NO asumen paridad entre redes: filtran lo que el catálogo realmente expone para
 * una red y un nivel (ACCOUNT | POST). Si una métrica no existe para la red, no
 * aparece (HANDOFF §10 "dirigido por el catálogo").
 */
import type { CatalogValue } from '../../lib/catalog';
import type { MetricCatalogItem } from '../../api/generated/model';

const up = (s?: string | null): string => (s ?? '').toUpperCase();

function metricsFor(catalog: CatalogValue, level: 'ACCOUNT' | 'POST', platform?: string | null): MetricCatalogItem[] {
  const P = up(platform);
  return catalog.items.filter(
    (it) => !!it.key && up(it.level) === level && (!it.platform || up(it.platform) === P),
  );
}

/** Métricas de nivel cuenta disponibles para esa red (orden del catálogo). */
export function accountMetrics(catalog: CatalogValue, platform?: string | null): MetricCatalogItem[] {
  return metricsFor(catalog, 'ACCOUNT', platform);
}

/** Métricas de nivel publicación disponibles para esa red. */
export function postMetrics(catalog: CatalogValue, platform?: string | null): MetricCatalogItem[] {
  return metricsFor(catalog, 'POST', platform);
}

/** Todas las métricas de nivel publicación (sin filtrar por red; para deep-links sin red conocida). */
export function anyPostMetrics(catalog: CatalogValue): MetricCatalogItem[] {
  return catalog.items.filter((it) => !!it.key && up(it.level) === 'POST');
}

/** Métrica principal de publicación de esa red (para rankear "top posts"), o null. */
export function primaryPostMetricKey(catalog: CatalogValue, platform?: string | null): string | null {
  return postMetrics(catalog, platform)[0]?.key ?? null;
}
