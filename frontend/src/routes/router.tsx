import { createBrowserRouter } from 'react-router-dom';
import { ProtectedRoute } from '../lib/auth';
import { AppLayout } from '../components/layout';
import { collectRoutes } from './registry';
import { NotFound } from './NotFound';

const { protectedRoutes, publicRoutes } = collectRoutes();

/**
 * Router central (CONGELADO). Estructura:
 *   - publicRoutes (login, …) en la raíz.
 *   - <ProtectedRoute> → <AppLayout> → rutas protegidas de todos los features.
 *   - catch-all → NotFound.
 */
export const router = createBrowserRouter([
  ...publicRoutes,
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [...protectedRoutes, { path: '*', element: <NotFound /> }],
      },
    ],
  },
]);
