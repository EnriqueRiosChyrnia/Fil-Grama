import type { AppRoute } from '../../routes/types';

/**
 * Pestaña "Reporte" del workspace de cliente (HANDOFF §8). El layout con pestañas
 * vive en `features/clients/ClientWorkspace`; acá sólo declaramos la página con su
 * path RELATIVO al padre `clients/:clientId`. `clients/routes.tsx` la anida bajo
 * el <Outlet/>. (Sin `routes`: el registry no la agrega como raíz.)
 */
export const clientRoutes: AppRoute[] = [
  {
    path: 'report',
    lazy: async () => ({ Component: (await import('./ReportPage')).ReportPage }),
  },
];
