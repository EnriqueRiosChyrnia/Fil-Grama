import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api';

/**
 * Defaults de React Query (PLAN §3.5). App ~95% server-state, herramienta interna:
 *   - staleTime 60s: evita refetch agresivo entre navegaciones.
 *   - retry sensato: NO reintentar 4xx (incl. 401/403/404/422); 5xx y red, 1 vez.
 *   - sin refetch al enfocar la ventana (más calmo para uso de escritorio).
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiError) {
            if (error.status >= 400 && error.status < 500) return false;
          }
          return failureCount < 1;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}
