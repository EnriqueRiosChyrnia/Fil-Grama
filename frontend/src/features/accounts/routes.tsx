import type { AppRoute } from '../../routes/types';

/**
 * Detalle por red/cuenta (track Cuentas, FB). Pantalla client-scoped: se monta
 * dentro del workspace de cliente (pestañas), por eso se exporta como `clientRoutes`
 * con path RELATIVO a `clients/:clientId`. `clients/routes.tsx` la anida bajo el
 * <Outlet/>. La grilla de posts y el detalle de post viven en `features/posts`.
 */
export const clientRoutes: AppRoute[] = [
  {
    path: 'accounts/:accountId',
    lazy: async () => ({ Component: (await import('./AccountDetailPage')).AccountDetailPage }),
  },
];
