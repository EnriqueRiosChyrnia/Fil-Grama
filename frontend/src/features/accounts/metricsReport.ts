/**
 * Hooks de informe de métricas — contrato `:report` / `:batchReport` (sin legacy).
 *
 * Estos endpoints son POST pero semánticamente LECTURAS (patrón GA4 `runReport`):
 * por eso se envuelve la fn cruda generada (`postAccountsIdMetricsReport`,
 * `postPostsIdMetricsReport`, `postMetricsBatchReport`) en un `useQuery` con key
 * propia vía `qk.feature(...)` — PATRÓN BENDECIDO (frontend/CLAUDE.md). NO
 * `useMutation`; NO `getGet*QueryKey` (no existe GET equivalente).
 *
 * La response agrupa por `series` (una por métrica): el punto usa `date`/`value`
 * (no `capturedAt`) y `unit` viaja en cada serie. Se localiza la serie por su
 * `metric` key con `pointsForMetric`.
 */
import { useQueries, useQuery } from '@tanstack/react-query';
import {
  postAccountsIdMetricsReport,
  postMetricsBatchReport,
  postPostsIdMetricsReport,
} from '../../api/generated/endpoints';
import type {
  AccountReportRequest,
  AccountReportResponse,
  PostReportResponse,
  SeriesPoint,
} from '../../api/generated/model';
import { qk } from '../../lib/query';

export interface ReportRange {
  from: string;
  to: string;
}

const GRANULARITY = 'day';
/** Máx. requests por batch (contrato `:batchReport`). */
const BATCH_LIMIT = 20;

/** Puntos {date,value} de una métrica dentro de un informe. Sin serie → []. */
export function pointsForMetric(
  report: AccountReportResponse | PostReportResponse | undefined,
  metricKey: string | null | undefined,
): SeriesPoint[] {
  if (!report || !metricKey) return [];
  return report.series?.find((s) => s.metric === metricKey)?.points ?? [];
}

/**
 * Informe de series de una cuenta: N métricas + rango en UNA request.
 * `enabled` se apaga sin cuenta o sin métricas (request inválida → 400).
 */
export function useAccountReport(
  accountId: number,
  metrics: string[],
  range: ReportRange,
  opts?: { enabled?: boolean },
) {
  return useQuery({
    // Misma combo (cuenta, métricas ordenadas, rango, granularidad) = misma key.
    queryKey: qk.feature('accountReport', accountId, [...metrics].sort(), range.from, range.to, GRANULARITY),
    queryFn: ({ signal }) =>
      postAccountsIdMetricsReport(
        accountId,
        { metrics, dateRange: { from: range.from, to: range.to }, granularity: GRANULARITY },
        { signal },
      ),
    enabled: (opts?.enabled ?? true) && accountId > 0 && metrics.length > 0,
  });
}

/** Informe de series de un post (mismo shape; la response trae `postId`). */
export function usePostReport(
  postId: number,
  metrics: string[],
  range: ReportRange,
  opts?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: qk.feature('postReport', postId, [...metrics].sort(), range.from, range.to, GRANULARITY),
    queryFn: ({ signal }) =>
      postPostsIdMetricsReport(
        postId,
        { metrics, dateRange: { from: range.from, to: range.to }, granularity: GRANULARITY },
        { signal },
      ),
    enabled: (opts?.enabled ?? true) && postId > 0 && metrics.length > 0,
  });
}

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

/**
 * Varios informes de cuenta en pocos round-trips (espejo GA4 `batchRunReports`).
 * Reemplaza el N+1 (una request por tarjeta) por una `:batchReport` por cada 20
 * cuentas (límite del contrato). Devuelve un mapa `accountId → valores` de la
 * primera serie pedida de cada informe (pensado para sparklines de una métrica).
 */
export function useBatchAccountReports(requests: AccountReportRequest[]): {
  byAccount: Map<number, number[]>;
  isLoading: boolean;
} {
  const groups = chunk(requests, BATCH_LIMIT);
  const results = useQueries({
    queries: groups.map((group, i) => ({
      queryKey: qk.feature('batchReport', i, group),
      queryFn: ({ signal }: { signal?: AbortSignal }) =>
        postMetricsBatchReport({ requests: group }, { signal }),
      enabled: group.length > 0,
    })),
  });

  const byAccount = new Map<number, number[]>();
  for (const r of results) {
    for (const rep of r.data?.data?.reports ?? []) {
      if (rep.accountId != null) {
        byAccount.set(rep.accountId, (rep.series?.[0]?.points ?? []).map((p) => p.value ?? 0));
      }
    }
  }
  return { byAccount, isLoading: results.some((r) => r.isLoading) };
}
