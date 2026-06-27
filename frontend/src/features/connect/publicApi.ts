/**
 * Cliente de los endpoints PÚBLICOS del link compartible de conexión
 * (spec/09 "Link compartible", spec/03). Estos endpoints son `permitAll`: NO
 * llevan Bearer ni disparan el refresh de sesión. Por eso usamos `apiFetch` con
 * `{ auth: false }` en vez del cliente generado por orval (cuyo mutator SIEMPRE
 * autentica → inservible para un cliente deslogueado abriendo el link).
 *
 * Backend CV2 ya mergeado: las rutas existen en `api/generated`. Reconciliado a los
 * TIPOS generados (`PublicLinkInfo`, `AuthorizationUrlResponse`); las LLAMADAS se
 * quedan en `apiFetch{auth:false}` a propósito (las fns generadas autenticarían).
 */
import { apiFetch } from '../../lib/api';
import type { PublicLinkInfo, AuthorizationUrlResponse } from '../../api/generated/model';

/** Metadatos del link público. Lanza `ApiError` (status 404 inexistente, 410 vencido/revocado). */
export function fetchPublicConnectLink(token: string): Promise<PublicLinkInfo> {
  return apiFetch<PublicLinkInfo>(
    `/public/connect-links/${encodeURIComponent(token)}`,
    { method: 'GET' },
    { auth: false },
  );
}

/** Inicia el OAuth público para una red. Devuelve la `authorizationUrl` oficial a la que redirigir. */
export function startPublicConnect(token: string, platform: string): Promise<AuthorizationUrlResponse> {
  return apiFetch<AuthorizationUrlResponse>(
    `/public/connect-links/${encodeURIComponent(token)}/connect/${platform.toLowerCase()}`,
    { method: 'POST' },
    { auth: false },
  );
}
