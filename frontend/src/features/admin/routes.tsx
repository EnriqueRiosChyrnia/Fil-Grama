import { Outlet } from 'react-router-dom';
import { RequireRole } from '../../lib/auth';
import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Administración. TODO el árbol va detrás de `RequireRole(['ADMIN'])`
 * vía el `element` del padre: un EMPLEADO que entre a /admin* es redirigido a "/".
 * El link "Administración" del Topbar ya lo gatea la central con `isAdmin`.
 */
export const routes: AppRoute[] = [
  {
    path: 'admin',
    element: (
      <RequireRole roles={['ADMIN']}>
        <Outlet />
      </RequireRole>
    ),
    children: [
      { index: true, lazy: async () => ({ Component: (await import('./AdminIndexPage')).AdminIndexPage }) },
      { path: 'users', lazy: async () => ({ Component: (await import('./users/UsersListPage')).UsersListPage }) },
      { path: 'users/:id', lazy: async () => ({ Component: (await import('./users/UserDetailPage')).UserDetailPage }) },
      { path: 'sync', lazy: async () => ({ Component: (await import('./sync/SyncPage')).SyncPage }) },
    ],
  },
];
