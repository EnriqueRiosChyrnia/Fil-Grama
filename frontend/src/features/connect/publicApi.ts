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
import type { AuthorizationUrlResponse } from '../../api/generated/model';

/**
 * Clave de `sessionStorage` con el token del link, guardada antes de redirigir al OAuth para poder
 * VOLVER a la lista tras el callback sin filtrar el token en la URL de la red (no viaja en el `state`).
 * spec/09 §"Token client-side, no en el state".
 */
export const CONNECT_TOKEN_KEY = 'fg_connect_token';

/** Cuenta ya conectada del cliente (mínimo: handle + red). spec/09 §"Onboarding multi-cuenta". */
export interface ConnectedAccount {
  handle?: string;
  platform?: string;
}

/**
 * Metadatos públicos del link + checklist abierto de cuentas ya conectadas. Tipo local (no el
 * generado por orval) porque estos endpoints se llaman con `apiFetch{auth:false}` y el campo
 * `connectedAccounts` lo agregó el backend de onboarding multi-cuenta. spec/03, spec/09.
 */
export interface PublicConnectLink {
  clientName?: string;
  platform?: string | null;
  expiresAt?: string;
  connectedAccounts?: ConnectedAccount[];
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
export function startPublicConnect(token: string, platform: string): Promise<AuthorizationUrlResponse> {
  return apiFetch<AuthorizationUrlResponse>(
    `/public/connect-links/${encodeURIComponent(token)}/connect/${platform.toLowerCase()}`,
    { method: 'POST' },
    { auth: false },
  );
}
