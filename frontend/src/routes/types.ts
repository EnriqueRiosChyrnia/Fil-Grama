import type { RouteObject } from 'react-router-dom';

/** Tipo de ruta de un feature. Igual a RouteObject de React Router. */
export type AppRoute = RouteObject;

/**
 * Contrato de `features/<x>/routes.tsx` (CONGELADO). Cada track exporta:
 *   - `routes`: rutas PROTEGIDAS (van dentro de <ProtectedRoute> + <AppLayout>).
 *   - `publicRoutes` (opcional): rutas sin sesión (ej. login).
 * El router central las agrega solo (glob), así NADIE edita el router por track.
 *
 * Ej:
 *   export const routes: AppRoute[] = [
 *     { path: 'clients/:clientId', lazy: async () => ({ Component: (await import('./X')).X }) },
 *   ];
 */
export interface FeatureRoutes {
  routes?: AppRoute[];
  publicRoutes?: AppRoute[];
}
