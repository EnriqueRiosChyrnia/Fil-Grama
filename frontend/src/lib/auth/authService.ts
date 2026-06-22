/**
 * Servicio de auth de bajo nivel. Login/refresh van con `auth:false` para que un
 * 401 de credenciales NO dispare el refresh automático del cliente. `/auth/me` va
 * con `auth:true` (Bearer + refresh transparente).
 *
 * El backend no tipa el cuerpo de /auth/login en OpenAPI (es `object`), así que el
 * contrato se fija acá según HANDOFF §6.
 */
import { apiFetch, tokenStore, AUTH_PATHS } from '../api';

export type Role = 'ADMIN' | 'EMPLEADO';

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

interface LoginResult {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

export async function login(email: string, password: string): Promise<AuthUser> {
  const res = await apiFetch<LoginResult>(
    AUTH_PATHS.login,
    { method: 'POST', body: JSON.stringify({ email, password }) },
    { auth: false },
  );
  tokenStore.set(res.accessToken, res.refreshToken);
  return res.user;
}

export async function fetchMe(): Promise<AuthUser> {
  return apiFetch<AuthUser>(AUTH_PATHS.me, { method: 'GET' }, { auth: true });
}

export async function logout(): Promise<void> {
  const refreshToken = tokenStore.getRefresh();
  try {
    if (refreshToken) {
      await apiFetch<void>(
        AUTH_PATHS.logout,
        { method: 'POST', body: JSON.stringify({ refreshToken }) },
        { auth: false },
      );
    }
  } catch {
    /* el logout es best-effort; igual limpiamos local */
  } finally {
    tokenStore.clear();
  }
}
