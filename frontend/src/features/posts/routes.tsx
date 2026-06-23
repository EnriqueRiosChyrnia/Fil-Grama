import type { AppRoute } from '../../routes/types';

/**
 * Track Posts. Dos piezas client-scoped que se montan dentro del workspace de
 * cliente (pestañas) → se exportan como `clientRoutes` (paths relativos a
 * `clients/:clientId`), que `clients/routes.tsx` anida bajo el <Outlet/>:
 *   - `publicaciones`: selector cascada red → cuenta (pestaña Publicaciones).
 *   - `accounts/:accountId/posts`: grilla "todas las publicaciones" de la cuenta.
 *
 * El detalle de post/story (`posts/:postId`) NO es client-scoped (no lleva cliente
 * en la URL) → va como ruta protegida normal vía `routes`.
 */
export const clientRoutes: AppRoute[] = [
  {
    path: 'publicaciones',
    lazy: async () => ({ Component: (await import('./PublicacionesPage')).PublicacionesPage }),
  },
  {
    path: 'accounts/:accountId/posts',
    lazy: async () => ({ Component: (await import('./AllPostsPage')).AllPostsPage }),
  },
];

export const routes: AppRoute[] = [
  {
    path: 'posts/:postId',
    lazy: async () => ({ Component: (await import('./PostDetailPage')).PostDetailPage }),
  },
];
