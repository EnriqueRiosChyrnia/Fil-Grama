/**
 * Núcleo del cliente HTTP (frozen — lo usan auth y el mutator de orval).
 *
 * Responsabilidades (PLAN §3.3 / HANDOFF §4):
 *   - Adjuntar `Authorization: Bearer <access>` en requests protegidos.
 *   - En 401 por access expirado → `POST /auth/refresh` (rotado, single-flight) →
 *     reintentar UNA vez. Si el refresh falla → limpiar sesión y avisar (→ Login).
 *   - Normalizar errores `application/problem+json` a `ApiError` con `detail` humano.
 *
 * `coreRequest` recibe una URL ABSOLUTA; los helpers de arriba arman la URL.
 */
import { API_BASE_URL } from './config';
import { tokenStore } from './tokens';
import { ApiError, toApiError } from './problem';

export interface CoreOptions {
  /** Adjuntar Bearer + manejar refresh en 401. Default true. */
  auth?: boolean;
  signal?: AbortSignal;
}

let refreshPromise: Promise<boolean> | null = null;

/** Refresh rotado con single-flight: múltiples 401 concurrentes comparten un refresh. */
async function refreshAccessToken(): Promise<boolean> {
  const refresh = tokenStore.getRefresh();
  if (!refresh) return false;
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: refresh }),
        });
        if (!res.ok) {
          tokenStore.clear();
          return false;
        }
        const data = (await res.json()) as { accessToken: string; refreshToken: string };
        tokenStore.set(data.accessToken, data.refreshToken);
        return true;
      } catch {
        tokenStore.clear();
        return false;
      }
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function doFetch(url: string, init: RequestInit, auth: boolean): Promise<Response> {
  const headers = new Headers(init.headers);
  const access = tokenStore.getAccess();
  if (auth && access) headers.set('Authorization', `Bearer ${access}`);
  if (init.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', headers.get('Accept') ?? 'application/json, application/problem+json');
  return fetch(url, { ...init, headers });
}

/** Request de bajo nivel sobre una URL absoluta. Devuelve la Response cruda. */
export async function coreRequestRaw(
  url: string,
  init: RequestInit = {},
  opts: CoreOptions = {},
): Promise<Response> {
  const auth = opts.auth ?? true;
  const init2: RequestInit = { ...init, signal: opts.signal ?? init.signal };

  let res: Response;
  try {
    res = await doFetch(url, init2, auth);
  } catch {
    throw new ApiError(0, null); // error de red
  }

  if (res.status === 401 && auth) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      try {
        res = await doFetch(url, init2, auth);
      } catch {
        throw new ApiError(0, null);
      }
    }
    if (res.status === 401) {
      // refresh ausente/fallido o sigue 401 → cerrar sesión.
      tokenStore.clear();
      tokenStore.emitSessionEnded();
      throw await toApiError(res);
    }
  }

  if (!res.ok) throw await toApiError(res);
  return res;
}

/** Parsea el cuerpo según content-type (JSON / texto / vacío). */
export async function parseBody<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T;
  }
  const ct = res.headers.get('content-type') ?? '';
  if (ct.includes('json')) return (await res.json()) as T;
  if (ct.includes('text') || ct === '') return (await res.text()) as unknown as T;
  return (await res.blob()) as unknown as T;
}

/** Request tipado sobre URL absoluta (parsea el cuerpo). */
export async function coreRequest<T>(
  url: string,
  init: RequestInit = {},
  opts: CoreOptions = {},
): Promise<T> {
  const res = await coreRequestRaw(url, init, opts);
  return parseBody<T>(res);
}

/** Helper para paths relativos a la API (prepende `/api/v1`). Lo usa lib/auth. */
export function apiFetch<T>(path: string, init: RequestInit = {}, opts: CoreOptions = {}): Promise<T> {
  return coreRequest<T>(`${API_BASE_URL}${path}`, init, opts);
}
