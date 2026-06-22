import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Comparar (FC). El router central las agrega solo por glob
 * (no se toca routes/router.tsx). HANDOFF §8/§11.
 */
export const routes: AppRoute[] = [
  {
    path: 'clients/:clientId/compare',
    lazy: async () => ({ Component: (await import('./CompareAccountsPage')).CompareAccountsPage }),
  },
];
