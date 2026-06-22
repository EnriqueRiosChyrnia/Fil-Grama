import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Cuentas (FB). Detalle por red/cuenta. La grilla de posts y el
 * detalle de post viven en `features/posts`. Drill-down entre tracks por path string.
 */
export const routes: AppRoute[] = [
  {
    path: 'clients/:clientId/accounts/:accountId',
    lazy: async () => ({ Component: (await import('./AccountDetailPage')).AccountDetailPage }),
  },
];
