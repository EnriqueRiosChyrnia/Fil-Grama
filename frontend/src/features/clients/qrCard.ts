/**
 * Composición de la tarjeta PNG para compartir el connect-link (spec/09 §"Tarjeta para
 * compartir"). Vertical ~1080×1350, lista para WhatsApp: header, QR de marca al centro,
 * pie con aviso de vencimiento + marca Fil-Grama y el link en texto (fallback).
 *
 * Todo client-side: el QR se renderiza con `qr-code-styling` y se compone en un `<canvas>`.
 * La `url` (con el token) jamás sale del navegador.
 */
import QRCodeStyling from 'qr-code-styling';
import { networkLabel } from '../../components/ui';
import { qrOptionsFor } from './qrStyle';

const BLUE = '#1E66BC';
const BLUE_50 = '#EAF2FB';
const BLUE_700 = '#0F3F78';
const INK = '#1A2230';
const MUTED = '#5B6676';
const FONT = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';

const NETS = ['INSTAGRAM', 'TIKTOK', 'FACEBOOK'];

/** PNG crudo del QR (sin tarjeta), por si se quiere el QR pelado. 100 % local. */
export async function qrPngBlob(
  url: string,
  platform?: string,
  forceBrand?: boolean,
  size = 760,
): Promise<Blob> {
  const qr = new QRCodeStyling(qrOptionsFor(url, platform, forceBrand, size));
  const raw = await qr.getRawData('png');
  if (raw instanceof Blob) return raw;
  throw new Error('No se pudo generar el QR');
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

/** Texto centrado con wrap a `maxLines`; devuelve la `y` de la línea siguiente. */
function wrapCentered(
  ctx: CanvasRenderingContext2D,
  text: string,
  cx: number,
  y: number,
  maxWidth: number,
  lineHeight: number,
  maxLines = 2,
): number {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = '';
  for (const w of words) {
    const test = line ? `${line} ${w}` : w;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = w;
      if (lines.length === maxLines - 1) break;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  lines.forEach((ln, i) => ctx.fillText(ln, cx, y + i * lineHeight));
  return y + lines.length * lineHeight;
}

/** Recorta con "…" para que entre en `maxWidth`. */
function ellipsize(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
  if (ctx.measureText(text).width <= maxWidth) return text;
  let lo = 0;
  let hi = text.length;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (ctx.measureText(text.slice(0, mid) + '…').width <= maxWidth) lo = mid;
    else hi = mid - 1;
  }
  return text.slice(0, lo) + '…';
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`No se pudo cargar ${src}`));
    img.src = src;
  });
}

/**
 * Compone la tarjeta PNG. Header "Conectá tu {Red}" si hay red (sin override), o
 * "Conectá las redes de {Cliente}" en multi-red/azul.
 */
export async function buildQrCard(opts: {
  url: string;
  platform?: string;
  forceBrand?: boolean;
  clientName?: string;
}): Promise<Blob> {
  const { url, platform, forceBrand, clientName } = opts;
  const net = (platform ?? '').toUpperCase();
  const byNet = !forceBrand && NETS.includes(net);

  // QR a alta resolución + isotipo para el pie, en paralelo.
  const [qrBlob, isotipo] = await Promise.all([
    qrPngBlob(url, platform, forceBrand, 760),
    loadImage('/brand/isotipo.svg'),
  ]);
  const qrImg = await createImageBitmap(qrBlob);

  const W = 1080;
  const H = 1350;
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas 2D no disponible');

  // Fondo + tarjeta.
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, W, H);
  const pad = 56;
  roundRect(ctx, pad, pad, W - pad * 2, H - pad * 2, 40);
  ctx.fillStyle = '#FFFFFF';
  ctx.fill();
  ctx.lineWidth = 3;
  ctx.strokeStyle = BLUE_50;
  ctx.stroke();
  // Acento de marca arriba.
  roundRect(ctx, pad, pad, W - pad * 2, 12, 6);
  ctx.fillStyle = BLUE;
  ctx.fill();

  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';

  // Header.
  const heading = byNet ? `Conectá tu ${networkLabel(net, true)}` : `Conectá las redes de ${clientName || 'tu cuenta'}`;
  ctx.fillStyle = INK;
  ctx.font = `600 52px ${FONT}`;
  const afterHead = wrapCentered(ctx, heading, W / 2, 210, W - pad * 2 - 90, 62, 2);

  // QR centrado.
  const qrSize = 660;
  const qrX = (W - qrSize) / 2;
  const qrY = Math.max(afterHead + 26, 300);
  ctx.drawImage(qrImg, qrX, qrY, qrSize, qrSize);

  // Pie: aviso de vencimiento.
  ctx.fillStyle = MUTED;
  ctx.font = `500 29px ${FONT}`;
  const noticeY = qrY + qrSize + 64;
  ctx.fillText('Escaneá con la cámara · vence en 72 h', W / 2, noticeY);

  // Marca: isotipo + wordmark, centrados.
  const mark = 46;
  ctx.font = `700 34px ${FONT}`;
  const wordmark = 'Fil-Grama';
  const wmW = ctx.measureText(wordmark).width;
  const groupW = mark + 14 + wmW;
  const gx = (W - groupW) / 2;
  const brandY = noticeY + 64;
  ctx.drawImage(isotipo, gx, brandY - mark + 6, mark, mark);
  ctx.fillStyle = BLUE_700;
  ctx.textAlign = 'left';
  ctx.fillText(wordmark, gx + mark + 14, brandY);

  // Link en texto (fallback si la cámara no engancha).
  ctx.textAlign = 'center';
  ctx.fillStyle = MUTED;
  ctx.font = `400 24px ${FONT}`;
  ctx.fillText(ellipsize(ctx, url, W - pad * 2 - 80), W / 2, brandY + 56);

  return await new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('No se pudo exportar la tarjeta'))), 'image/png'),
  );
}
