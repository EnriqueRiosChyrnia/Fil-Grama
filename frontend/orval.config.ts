import { defineConfig } from 'orval';

/**
 * Codegen del cliente del backend (decisión cerrada PLAN §1): tipos + hooks de
 * React Query desde la spec OpenAPI de springdoc.
 *
 * Anti-drift: `api/generated/*` se commitea al repo y `npm run codegen:check`
 * regenera + `git diff --exit-code` (falla si el cliente quedó desactualizado vs
 * el backend). TODO(CI): enchufar `codegen:check` en el pipeline. Necesita el
 * OpenAPI del backend disponible — o levantar el backend (`:8080`), o publicar el
 * `v3/api-docs` como artefacto/archivo y apuntar `input.target` a él en CI.
 *
 * NOTA: los operationId del backend son genéricos (list, list_1, create...), así
 * que derivamos nombres de hook estables desde verbo + ruta. Convención:
 *   GET  /api/v1/clients              -> useGetClients
 *   GET  /api/v1/clients/{id}         -> useGetClientsId
 *   GET  /api/v1/clients/{clientId}/summary -> useGetClientsClientIdSummary
 *   POST /api/v1/auth/login           -> usePostAuthLogin
 * (verbo + ruta es único → sin colisiones). Esta convención está CONGELADA.
 */
function operationName(_operation: unknown, route: string, verb: string): string {
  const segments = route
    .split('/')
    .filter(Boolean)
    .filter((s) => s !== 'api' && s !== 'v1');

  let name = verb.toLowerCase();
  for (const raw of segments) {
    const cleaned = raw.replace(/[{}]/g, '').replace(/[^a-zA-Z0-9]+/g, ' ');
    const pascal = cleaned
      .split(' ')
      .filter(Boolean)
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join('');
    name += pascal;
  }
  return name;
}

export default defineConfig({
  filgrama: {
    input: {
      target: 'http://localhost:8080/v3/api-docs',
    },
    output: {
      mode: 'split',
      target: 'src/api/generated/endpoints.ts',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      prettier: false,
      override: {
        mutator: {
          path: 'src/lib/api/orval-mutator.ts',
          name: 'orvalFetch',
        },
        operationName,
        query: {
          // Solo queries para GET; orval ya genera mutations para POST/PATCH/DELETE.
          // (Activar useMutation acá hace que los GET salgan como useMutation — bug.)
          useQuery: true,
          signal: true,
        },
      },
    },
  },
});
