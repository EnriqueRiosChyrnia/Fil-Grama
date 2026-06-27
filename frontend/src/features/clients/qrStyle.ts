/**
 * Mapa de estilo del QR del connect-link (spec/09 §"QR del link de conexión").
 *
 * Devuelve las opciones de `qr-code-styling` según la red (auto-detección) con override
 * manual a azul Fil-Grama. Reglas no negociables de legibilidad:
 *  - `errorCorrectionLevel: 'H'` SIEMPRE (hay imagen al centro).
 *  - quiet zone (`margin`) presente, fondo blanco, alto contraste.
 *  - ícono central ≤ ~22 % (`imageSize: 0.22`) con `hideBackgroundDots` (knockout).
 *
 * Generación 100 % local: la `url` (con el token, una credencial) NUNCA va a un servicio
 * externo de QR. Las imágenes del centro son assets propios servidos por Vite (`public/`).
 */
import type { Options } from 'qr-code-styling';

const BLUE = '#1E66BC'; // --fg-primary
const BLUE_DARK = '#0F3F78'; // --fg-blue-700

/** Assets del centro (servidos desde `public/`). */
const CENTER = {
  brand: '/brand/isotipo.svg',
  INSTAGRAM: '/brand/net/instagram.svg',
  TIKTOK: '/brand/net/tiktok.svg',
  FACEBOOK: '/brand/net/facebook.svg',
} as const;

const NETS = ['INSTAGRAM', 'TIKTOK', 'FACEBOOK'] as const;
type Net = (typeof NETS)[number];

function isNet(v: string): v is Net {
  return (NETS as readonly string[]).includes(v);
}

/**
 * Opciones de `qr-code-styling` para `url`. Con `platform` (y sin `forceBrand`) usa el
 * estilo de esa red; sin red o con `forceBrand` usa el azul Fil-Grama (neutro).
 */
export function qrOptionsFor(
  url: string,
  platform?: string,
  forceBrand?: boolean,
  size = 232,
): Options {
  const net = (platform ?? '').toUpperCase();
  const brand = !!forceBrand || !isNet(net);

  // Base común: EC 'H', quiet zone, fondo blanco, ícono al 22 % con knockout.
  const base: Options = {
    type: 'canvas',
    width: size,
    height: size,
    margin: Math.max(8, Math.round(size * 0.045)),
    data: url,
    qrOptions: { errorCorrectionLevel: 'H' },
    backgroundOptions: { color: '#FFFFFF' },
    imageOptions: { imageSize: 0.22, margin: 6, hideBackgroundDots: true, crossOrigin: 'anonymous' },
    dotsOptions: { type: 'extra-rounded', color: BLUE },
    cornersSquareOptions: { type: 'extra-rounded', color: BLUE_DARK },
    cornersDotOptions: { type: 'dot', color: BLUE_DARK },
    image: CENTER.brand,
  };

  if (brand) return base;

  if (net === 'INSTAGRAM') {
    // Degradado IG oscurecido respecto del tile del badge (spec: bajá el contraste del
    // amarillo para que el QR siga siendo escaneable). Ojos sólidos para máximo contraste.
    return {
      ...base,
      image: CENTER.INSTAGRAM,
      dotsOptions: {
        type: 'extra-rounded',
        gradient: {
          type: 'linear',
          rotation: Math.PI / 4,
          colorStops: [
            { offset: 0, color: '#F2742B' },
            { offset: 0.4, color: '#DD2A7B' },
            { offset: 0.75, color: '#B5179E' },
            { offset: 1, color: '#7B2FBF' },
          ],
        },
      },
      cornersSquareOptions: { type: 'extra-rounded', color: '#B5179E' },
      cornersDotOptions: { type: 'dot', color: '#7B2FBF' },
    };
  }

  if (net === 'TIKTOK') {
    return {
      ...base,
      image: CENTER.TIKTOK,
      dotsOptions: { type: 'extra-rounded', color: '#16181F' },
      cornersSquareOptions: { type: 'extra-rounded', color: '#25F4EE' },
      cornersDotOptions: { type: 'dot', color: '#FE2C55' },
    };
  }

  // FACEBOOK — azul FB casi igual al de marca: el glyph "f" del centro desambigua.
  return {
    ...base,
    image: CENTER.FACEBOOK,
    dotsOptions: { type: 'extra-rounded', color: '#1877F2' },
    cornersSquareOptions: { type: 'extra-rounded', color: '#0B5FCC' },
    cornersDotOptions: { type: 'dot', color: '#0B5FCC' },
  };
}
