/**
 * Mutator de orval (httpClient: 'fetch', orval v8). Todas las llamadas generadas
 * pasan por acá → heredan Bearer + refresh en 401 + normalización problem+json.
 *
 * orval v8 espera que el mutator devuelva el wrapper `{ data, status, headers }`
 * (los hooks generados leen `.data`). En error, `coreRequestRaw` lanza `ApiError`
 * y React Query lo expone como `error` (con `humanMessage`).
 *
 * La URL que pasa orval YA incluye `/api/v1/...`; le anteponemos solo el ORIGIN.
 */
import { API_ORIGIN } from './config';
import { coreRequestRaw, parseBody } from './client';

export const orvalFetch = async <T>(url: string, options: RequestInit): Promise<T> => {
  const res = await coreRequestRaw(`${API_ORIGIN}${url}`, options);
  const data = await parseBody<unknown>(res);
  return { data, status: res.status, headers: res.headers } as T;
};

export default orvalFetch;
