/**
 * Tipo de publicación en lenguaje humano (HANDOFF §8: distinguir
 * IMAGE/VIDEO/REEL/CAROUSEL/STORY). Las stories son efímeras.
 *
 * NOTA: `PostListItem` generado NO trae `isEphemeral` (sí lo lista el modelo de
 * dominio del HANDOFF §5). Lo derivamos de `postType === 'STORY'`.
 */
const LABELS: Record<string, string> = {
  IMAGE: 'Imagen',
  VIDEO: 'Video',
  REEL: 'Reel',
  CAROUSEL: 'Carrusel',
  STORY: 'Historia',
};

export function postTypeLabel(postType?: string | null): string {
  if (!postType) return 'Publicación';
  return LABELS[postType.toUpperCase()] ?? postType;
}

/** Efímera = historia (HANDOFF §8). */
export function isEphemeralType(postType?: string | null): boolean {
  return (postType ?? '').toUpperCase() === 'STORY';
}
