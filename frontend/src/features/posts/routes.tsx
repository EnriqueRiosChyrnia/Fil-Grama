import type { AppRoute } from '../../routes/types';

/**
 * Rutas del track Posts. Grilla "todas las publicaciones" (scope cuenta) + detalle
 * de post/story. El detalle por cuenta vive en `features/accounts`.
 */
export const routes: AppRoute[] = [
  {
    path: 'clients/:clientId/accounts/:accountId/posts',
    lazy: async () => ({ Component: (await import('./AllPostsPage')).AllPostsPage }),
  },
  {
    path: 'posts/:postId',
    lazy: async () => ({ Component: (await import('./PostDetailPage')).PostDetailPage }),
  },
];
