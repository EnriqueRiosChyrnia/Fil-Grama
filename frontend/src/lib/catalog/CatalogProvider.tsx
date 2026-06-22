import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { MetricCatalogItem } from '../../api/generated/model';
import { apiFetch } from '../api';
import { qk } from '../query';
import { useAuth } from '../auth';
import { CatalogContext, type CatalogValue } from './catalogContext';

/**
 * Carga `/metrics` UNA vez (PLAN §3.7 / HANDOFF §5,§10) y expone helpers de
 * lenguaje humano. NOTA: `/metrics` hoy NO trae `description`; `description()`
 * degrada a `displayName`. El texto largo de tooltips de conceptos CORE sale del
 * glosario local (concepts.ts).
 */
function humanizeKey(key: string): string {
  const stripped = key.replace(/^(ig|fb|tt|tiktok)_/i, '');
  const words = stripped.replace(/_/g, ' ').trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function CatalogProvider({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();
  const query = useQuery({
    queryKey: qk.catalog(),
    queryFn: () => apiFetch<MetricCatalogItem[]>('/metrics', { method: 'GET' }),
    staleTime: Infinity,
    enabled: status === 'authenticated', // /metrics requiere sesión
  });

  const value = useMemo<CatalogValue>(() => {
    const items = query.data ?? [];
    const map = new Map<string, MetricCatalogItem>();
    for (const it of items) if (it.key) map.set(it.key, it);
    const byKey = (key?: string | null) => (key ? map.get(key) : undefined);

    return {
      isLoading: query.isLoading,
      isError: query.isError,
      items,
      byKey,
      displayName: (key?: string | null) => (!key ? '' : byKey(key)?.displayName ?? humanizeKey(key)),
      unit: (key?: string | null) => byKey(key)?.unit ?? undefined,
      description: (key?: string | null) => {
        const it = byKey(key);
        return (it as { description?: string } | undefined)?.description ?? it?.displayName;
      },
    };
  }, [query.data, query.isLoading, query.isError]);

  return <CatalogContext.Provider value={value}>{children}</CatalogContext.Provider>;
}
