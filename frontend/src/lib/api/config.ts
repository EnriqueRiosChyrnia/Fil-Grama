/**
 * Config base de red. `VITE_API_BASE_URL` incluye el prefijo `/api/v1`
 * (HANDOFF §13). De ahí derivamos el ORIGIN para el cliente generado por orval,
 * cuyos paths ya vienen con `/api/v1/...`.
 */
const RAW = import.meta.env.VITE_API_BASE_URL as string | undefined;

if (!RAW) {
  // Falla temprano y claro: sin base URL nada funciona.
  throw new Error('VITE_API_BASE_URL no está definida. Copiá .env.example a .env.');
}

/** Base completa con prefijo, p.ej. http://localhost:8080/api/v1 */
export const API_BASE_URL = RAW.replace(/\/+$/, '');

/** Solo el origen, p.ej. http://localhost:8080 (para el mutator de orval). */
export const API_ORIGIN = API_BASE_URL.replace(/\/api\/v1$/, '');

/** Endpoints de auth (relativos a API_BASE_URL). */
export const AUTH_PATHS = {
  login: '/auth/login',
  refresh: '/auth/refresh',
  logout: '/auth/logout',
  me: '/auth/me',
} as const;
