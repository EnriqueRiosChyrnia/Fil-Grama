import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Clientes (FA). El esqueleto deja sembrados Home (índice) y el
 * Dashboard de cliente como molde; FA continúa acá (onboarding, reconexión, etc.).
 */
export const routes: AppRoute[] = [
  {
    index: true,
    lazy: async () => ({ Component: (await import('./ClientsHomePage')).ClientsHomePage }),
  },
  {
    path: 'clients/:clientId',
    lazy: async () => ({ Component: (await import('./ClientDashboardPage')).ClientDashboardPage }),
  },
];
