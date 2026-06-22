/**
 * Conceptos CORE transversales a redes (HANDOFF §8 Home / §11 Comparar):
 * Alcance, Seguidores, Interacciones, Engagement. Son abstracciones humanas; el
 * mapeo concepto→métricas concretas por red lo resuelven los tracks FA/FB sobre
 * el catálogo. Acá sólo el lenguaje humano (label + texto de tooltip ⓘ).
 *
 * Los textos provienen de los diseños de alta fidelidad aprobados.
 */
export type CoreConcept = 'alcance' | 'seguidores' | 'interacciones' | 'engagement';

export interface ConceptMeta {
  key: CoreConcept;
  label: string;
  /** Texto del tooltip ⓘ (humano, nunca jerga de API). */
  info: string;
  /** 'count' | 'percent' — para formato. */
  unit: 'count' | 'percent';
}

export const CORE_CONCEPTS: ConceptMeta[] = [
  { key: 'alcance', label: 'Alcance', unit: 'count', info: 'Cuántas personas distintas vieron el contenido en el período.' },
  { key: 'seguidores', label: 'Seguidores', unit: 'count', info: 'Total de seguidores y cuánto creció en el período.' },
  { key: 'interacciones', label: 'Interacciones', unit: 'count', info: 'Me gusta, comentarios y otras reacciones, sumados.' },
  { key: 'engagement', label: 'Engagement', unit: 'percent', info: 'Qué porcentaje de quienes vieron el contenido interactuó.' },
];

export const CONCEPT_BY_KEY: Record<CoreConcept, ConceptMeta> = Object.fromEntries(
  CORE_CONCEPTS.map((c) => [c.key, c]),
) as Record<CoreConcept, ConceptMeta>;
