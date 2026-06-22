import type { AppRoute } from '../../routes/types';

/** Rutas del esqueleto auth: Login (pública) + resultado OAuth (protegida). */
export const publicRoutes: AppRoute[] = [
  {
    path: '/login',
    lazy: async () => ({ Component: (await import('./LoginPage')).LoginPage }),
  },
];

export const routes: AppRoute[] = [
  {
    path: 'oauth/result',
    lazy: async () => ({ Component: (await import('./OAuthResultPage')).OAuthResultPage }),
  },
];
