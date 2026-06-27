import type { AppRoute } from '../../routes/types';

/**
 * Rutas PÚBLICAS del onboarding por link compartible (track CV3). Van FUERA del
 * guard de auth: el dueño de la cuenta las abre sin sesión en Fil-Grama. El registry
 * (glob) las recoge solas; el router central no se toca.
 *
 * `/connect/done` (estático) antes que `/connect/:token` (dinámico): el data router
 * de React Router prioriza segmentos estáticos, así que "done" nunca cae en :token.
 */
export const publicRoutes: AppRoute[] = [
  {
    path: '/connect/done',
    lazy: async () => ({ Component: (await import('./ConnectDonePage')).ConnectDonePage }),
  },
  {
    path: '/connect/:token',
    lazy: async () => ({ Component: (await import('./PublicConnectPage')).PublicConnectPage }),
  },
];
