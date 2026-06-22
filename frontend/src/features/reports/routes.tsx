import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Reportes (FC). El router central las agrega solo por glob
 * (no se toca routes/router.tsx). El botón "Generar reporte" del Dashboard (FA)
 * navega a `clients/:clientId/report` por path string → este destino. HANDOFF §8.
 */
export const routes: AppRoute[] = [
  {
    path: 'clients/:clientId/report',
    lazy: async () => ({ Component: (await import('./ReportPage')).ReportPage }),
  },
];
