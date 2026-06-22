/**
 * Almacén de tokens (decisión cerrada PLAN §1):
 *   - access  → SOLO en memoria (se pierde al recargar; se rehidrata vía refresh).
 *   - refresh → localStorage, habilita "recordar sesión".
 * Riesgo XSS documentado; mitigado con CSP en deploy. Upgradeable a cookie httpOnly.
 *
 * Los tokens NUNCA se muestran en la UI (HANDOFF §13).
 */
const REFRESH_KEY = 'fg.refreshToken';

let accessToken: string | null = null;

/** Listeners de "sesión terminada" (refresh inválido) → el AuthProvider redirige a Login. */
type Listener = () => void;
const sessionEndedListeners = new Set<Listener>();

export const tokenStore = {
  getAccess(): string | null {
    return accessToken;
  },
  getRefresh(): string | null {
    try {
      return localStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  },
  /** Guarda el par tras login/refresh. */
  set(access: string, refresh: string): void {
    accessToken = access;
    try {
      localStorage.setItem(REFRESH_KEY, refresh);
    } catch {
      /* almacenamiento no disponible: la sesión vive solo en memoria */
    }
  },
  setAccess(access: string): void {
    accessToken = access;
  },
  hasRefresh(): boolean {
    return !!this.getRefresh();
  },
  /** Limpia todo (logout o refresh fallido). */
  clear(): void {
    accessToken = null;
    try {
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      /* noop */
    }
  },
  onSessionEnded(fn: Listener): () => void {
    sessionEndedListeners.add(fn);
    return () => sessionEndedListeners.delete(fn);
  },
  /** Lo dispara el cliente HTTP cuando el refresh falla irrecuperablemente. */
  emitSessionEnded(): void {
    sessionEndedListeners.forEach((fn) => fn());
  },
};
