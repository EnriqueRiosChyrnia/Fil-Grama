/** Normalización de redes sociales (sin componentes → seguro para fast-refresh). */
export type Platform = string;

const SHORT: Record<string, string> = { INSTAGRAM: 'IG', FACEBOOK: 'FB', TIKTOK: 'TT' };
const LONG: Record<string, string> = { INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook', TIKTOK: 'TikTok' };

export function networkLabel(platform?: Platform | null, long = false): string {
  if (!platform) return '';
  const k = platform.toUpperCase();
  return (long ? LONG[k] : SHORT[k]) ?? platform;
}
