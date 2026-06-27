/**
 * Cliente de los endpoints PÚBLICOS del link compartible de conexión
 * (spec/09 "Link compartible", spec/03). Estos endpoints son `permitAll`: NO
 * llevan Bearer ni disparan el refresh de sesión. Por eso usamos `apiFetch` con
 * `{ auth: false }` en vez del cliente generado por orval (que siempre autentica).
 *
 * Hand-typed contra los contratos: el backend CV1/CV2 todavía no está mergeado, así
 * que estas rutas no existen aún en `api/generated`. Cuando se mergeen, regenerar el
 * cliente (`npm run codegen`) y migrar a los hooks/fns tipados nativos.
 */
import { apiFetch } from '../../lib/api';

/** `GET /public/connect-links/{token}` → metadatos para la página de conexión. */
export interface PublicConnectLink {
  clientName: string;
  /** Si viene fija, el cliente conecta una sola red; si falta, elige entre las habilitadas. */
  platform?: string;
  expiresAt?: string;
}

/** `POST /public/connect-links/{token}/connect/{platform}` → arranca el OAuth acotado. */
export interface PublicConnectStart {
  authorizationUrl: string;
}

/** Metadatos del link público. Lanza `ApiError` (status 404 inexistente, 410 vencido/revocado). */
export function fetchPublicConnectLink(token: string): Promise<PublicConnectLink> {
  return apiFetch<PublicConnectLink>(
    `/public/connect-links/${encodeURIComponent(token)}`,
    { method: 'GET' },
    { auth: false },
  );
}

/** Inicia el OAuth público para una red. Devuelve la `authorizationUrl` oficial a la que redirigir. */
export function startPublicConnect(token: string, platform: string): Promise<PublicConnectStart> {
  return apiFetch<PublicConnectStart>(
    `/public/connect-links/${encodeURIComponent(token)}/connect/${platform.toLowerCase()}`,
    { method: 'POST' },
    { auth: false },
  );
}
