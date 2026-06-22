import { createContext } from 'react';
import type { AuthUser, Role } from './authService';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  /** Atajo: ¿el usuario tiene rol ADMIN? Para gating de Administración. */
  isAdmin: boolean;
  hasRole: (...roles: Role[]) => boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
