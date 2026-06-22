import type { AppRoute, FeatureRoutes } from './types';

/**
 * Agregación por glob: descubre TODOS los `features/<x>/routes.tsx` en build-time.
 * Un track nuevo sólo agrega su carpeta + su routes.tsx; el router central NO se
 * toca (anti-hotspot de merge, PLAN §5). Orden determinístico por ruta de archivo.
 */
const modules = import.meta.glob<FeatureRoutes>('../features/*/routes.tsx', { eager: true });

export function collectRoutes(): { protectedRoutes: AppRoute[]; publicRoutes: AppRoute[] } {
  const protectedRoutes: AppRoute[] = [];
  const publicRoutes: AppRoute[] = [];
  for (const key of Object.keys(modules).sort()) {
    const mod = modules[key];
    if (mod.routes) protectedRoutes.push(...mod.routes);
    if (mod.publicRoutes) publicRoutes.push(...mod.publicRoutes);
  }
  return { protectedRoutes, publicRoutes };
}
