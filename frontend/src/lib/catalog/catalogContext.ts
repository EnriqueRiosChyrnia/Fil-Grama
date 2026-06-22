import { createContext } from 'react';
import type { MetricCatalogItem } from '../../api/generated/model';

/** Valor del contexto de catálogo (helpers "lenguaje humano, nunca de API"). */
export interface CatalogValue {
  isLoading: boolean;
  isError: boolean;
  items: MetricCatalogItem[];
  byKey: (key?: string | null) => MetricCatalogItem | undefined;
  /** Nombre humano de una métrica (displayName del catálogo; fallback legible). */
  displayName: (key?: string | null) => string;
  /** Unidad ('count' | 'percent' | …) si la conoce el catálogo. */
  unit: (key?: string | null) => string | undefined;
  /** Texto descriptivo para tooltips ⓘ (hoy: displayName; el backend no trae description). */
  description: (key?: string | null) => string | undefined;
}

export const CatalogContext = createContext<CatalogValue | null>(null);
