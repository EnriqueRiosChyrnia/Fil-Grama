import type { AppRoute } from '../../routes/types';
import { clientRoutes as accountRoutes } from '../accounts/routes';
import { clientRoutes as compareRoutes } from '../compare/routes';
import { clientRoutes as postsRoutes } from '../posts/routes';
import { clientRoutes as reportRoutes } from '../reports/routes';

/**
 * Rutas del track Clientes (FA) + ENSAMBLADO de la navegación client-scoped.
 *
 * Navegación por pestañas (track central): `clients/:clientId` monta el layout
 * <ClientWorkspace/> (header persistente + tabs Dashboard · Publicaciones ·
 * Comparar · Reporte) y las pantallas client-scoped cuelgan como hijos vía
 * <Outlet/>. Cada feature sigue siendo dueño de SU página: exporta `clientRoutes`
 * (paths relativos) y acá sólo se anidan. El router central no se toca (glob).
 * Supersede el breadcrumb suelto de HANDOFF §8.
 */
export const routes: AppRoute[] = [
  {
    index: true,
    lazy: async () => ({ Component: (await import('./ClientsHomePage')).ClientsHomePage }),
  },
  {
    path: 'clients/new',
    lazy: async () => ({ Component: (await import('./NewClientPage')).NewClientPage }),
  },
  {
    path: 'clients/:clientId',
    lazy: async () => ({ Component: (await import('./ClientWorkspace')).ClientWorkspace }),
    children: [
      {
        index: true,
        lazy: async () => ({ Component: (await import('./ClientDashboardPage')).ClientDashboardPage }),
      },
      ...postsRoutes, // publicaciones (selector) + accounts/:accountId/posts (grilla)
      ...accountRoutes, // accounts/:accountId (detalle de cuenta)
      ...compareRoutes, // compare
      ...reportRoutes, // report
    ],
  },
  {
    // Flujo enfocado (pantalla completa, fuera de las pestañas): se llega desde el
    // botón "Reconectar" del header del workspace.
    path: 'clients/:clientId/reconnect',
    lazy: async () => ({ Component: (await import('./ReconnectPage')).ReconnectPage }),
  },
];
