/**
 * QR de marca del connect-link (spec/09 §"QR del link de conexión"). Renderiza la `url`
 * con `qr-code-styling` aplicando el estilo por red (o azul Fil-Grama). Solo presentación;
 * para descargar/copiar la tarjeta usar `buildQrCard` (qrCard.ts).
 *
 * 100 % local: la `url` (con el token) nunca sale del navegador.
 */
import { useEffect, useRef } from 'react';
import QRCodeStyling from 'qr-code-styling';
import { qrOptionsFor } from './qrStyle';

export function BrandedQr({
  url,
  platform,
  forceBrand,
  size = 232,
}: {
  url: string;
  platform?: string;
  forceBrand?: boolean;
  size?: number;
}) {
  const holder = useRef<HTMLDivElement>(null);
  const qr = useRef<QRCodeStyling | null>(null);

  // Crear/insertar la instancia. Recreo solo si cambia el tamaño (append re-monta el nodo);
  // el resto (url/red/override) se aplica con update() en el efecto de abajo.
  useEffect(() => {
    const inst = new QRCodeStyling(qrOptionsFor(url, platform, forceBrand, size));
    qr.current = inst;
    const el = holder.current;
    if (el) {
      el.replaceChildren();
      inst.append(el);
    }
    return () => {
      qr.current = null;
      if (el) el.replaceChildren();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [size]);

  useEffect(() => {
    qr.current?.update(qrOptionsFor(url, platform, forceBrand, size));
  }, [url, platform, forceBrand, size]);

  return <div ref={holder} role="img" aria-label="Código QR del enlace de conexión" style={{ lineHeight: 0 }} />;
}
