import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '../api';
import { AuthContext, type AuthStatus } from './AuthContext';
import type { AuthContextValue } from './AuthContext';
import { fetchMe, login as svcLogin, logout as svcLogout, type AuthUser, type Role } from './authService';

/**
 * Provider de sesión (PLAN §3.4 / HANDOFF §4):
 *   - Al montar, si hay refresh persistido → rehidrata la sesión vía /auth/me
 *     (el cliente HTTP refresca el access en el primer 401).
 *   - Escucha "sesión terminada" (refresh inválido) y manda a unauthenticated.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  // Estado inicial derivado sincrónicamente: sin refresh persistido no hay sesión
  // posible, así evitamos setState dentro del efecto (cascading renders).
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokenStore.hasRefresh() ? 'loading' : 'unauthenticated',
  );
  const [user, setUser] = useState<AuthUser | null>(null);
  const queryClient = useQueryClient();
  const mounted = useRef(true);

  const endSession = useCallback(() => {
    setUser(null);
    setStatus('unauthenticated');
    queryClient.clear();
  }, [queryClient]);

  // Rehidratación inicial: solo si hay refresh (el access se obtiene en el 1er 401).
  useEffect(() => {
    mounted.current = true;
    if (!tokenStore.hasRefresh()) return;
    fetchMe()
      .then((me) => {
        if (!mounted.current) return;
        setUser(me);
        setStatus('authenticated');
      })
      .catch(() => {
        if (!mounted.current) return;
        tokenStore.clear();
        setStatus('unauthenticated');
      });
    return () => {
      mounted.current = false;
    };
  }, []);

  // Refresh fallido en cualquier request → cerrar sesión.
  useEffect(() => tokenStore.onSessionEnded(endSession), [endSession]);

  const login = useCallback(async (email: string, password: string) => {
    const me = await svcLogin(email, password);
    setUser(me);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    await svcLogout();
    endSession();
  }, [endSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      isAdmin: user?.role === 'ADMIN',
      hasRole: (...roles: Role[]) => (user ? roles.includes(user.role) : false),
      login,
      logout,
    }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
