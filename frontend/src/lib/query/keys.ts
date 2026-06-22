/**
 * Convención de query keys — CONGELADA.
 *
 * REGLA para los tracks:
 *   - Datos de servidor (clients, accounts, posts, reports, users, sync, métricas):
 *     usá los hooks generados por orval (`useGet*`). Sus keys las maneja orval; para
 *     invalidar usá los `getGet*QueryKey(...)` también generados.
 *   - Queries hechas a mano (catálogo, sesión, o composiciones propias del track):
 *     derivá la key de `qk.<dominio>(...)`. Primer elemento = nombre del dominio,
 *     en kebab/camel estable. NUNCA inline arrays ad-hoc: siempre vía `qk`.
 *
 * Ej. invalidar el catálogo: queryClient.invalidateQueries({ queryKey: qk.catalog() })
 */
export const qk = {
  catalog: (params?: { platform?: string; level?: string }) =>
    ['catalog', params ?? {}] as const,
  session: () => ['session', 'me'] as const,
  /** Namespace por feature para composiciones propias del track. */
  feature: (feature: string, ...rest: readonly unknown[]) =>
    [feature, ...rest] as const,
} as const;

export type QueryKeyOf<T extends (...args: never[]) => readonly unknown[]> = ReturnType<T>;
