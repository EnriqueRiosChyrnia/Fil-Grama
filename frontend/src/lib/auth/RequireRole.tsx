import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './useAuth';
import type { Role } from './authService';

/**
 * Gate RBAC declarativo. Para rutas de Administración (solo ADMIN, HANDOFF §3).
 * Para ocultar elementos de nav usá `useAuth().isAdmin`/`hasRole`.
 */
export function RequireRole({
  roles,
  children,
  redirectTo = '/',
}: {
  roles: Role[];
  children: ReactNode;
  redirectTo?: string;
}) {
  const { hasRole } = useAuth();
  if (!hasRole(...roles)) return <Navigate to={redirectTo} replace />;
  return <>{children}</>;
}
