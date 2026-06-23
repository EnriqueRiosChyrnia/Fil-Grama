import type { AppRoute } from '../../routes/types';

/**
 * Pestaña "Comparar" del workspace de cliente (HANDOFF §8/§11). El layout con
 * pestañas vive en `features/clients/ClientWorkspace`; acá sólo declaramos la
 * página con su path RELATIVO al padre `clients/:clientId`. `clients/routes.tsx`
 * la anida bajo el <Outlet/>. (Sin `routes`: el registry no la agrega como raíz.)
 */
export const clientRoutes: AppRoute[] = [
  {
    path: 'compare',
    lazy: async () => ({ Component: (await import('./CompareAccountsPage')).CompareAccountsPage }),
  },
];
