/**
 * Descarga del reporte generado. El backend devuelve `downloadUrl` apuntando a
 * `/api/v1/clients/{id}/reports/{rid}/download`, que es un endpoint PROTEGIDO:
 * un <a href> plano no manda el Bearer → 401. Por eso lo bajamos como blob a
 * través del cliente compartido (que adjunta el token y refresca en 401) y
 * disparamos la descarga con un object URL. Tokens nunca se exponen en la UI.
 */
import { API_ORIGIN, coreRequestRaw } from '../../lib/api';

function filenameFromDisposition(cd: string | null): string | null {
  if (!cd) return null;
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(cd);
  if (star) return decodeURIComponent(star[1].replace(/["']/g, '').trim());
  const plain = /filename="?([^";]+)"?/i.exec(cd);
  return plain ? plain[1].trim() : null;
}

/** Baja el archivo del reporte (PDF/MD) y dispara el "Guardar como" del navegador. */
export async function downloadReport(downloadUrl: string, fallbackName: string): Promise<void> {
  const abs = /^https?:\/\//i.test(downloadUrl) ? downloadUrl : `${API_ORIGIN}${downloadUrl}`;
  const res = await coreRequestRaw(abs, { method: 'GET' });
  const blob = await res.blob();
  const name = filenameFromDisposition(res.headers.get('content-disposition')) ?? fallbackName;

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
