import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useGetAccountsId, useGetAccountsIdPosts } from '../../api/generated/endpoints';
import type { PostListItem } from '../../api/generated/model';
import { Card, Button, NetworkChip, DateRangeControl } from '../../components/ui';
import { EmptyState, ErrorState, Skeleton } from '../../components/layout';
import { useCatalog } from '../../lib/catalog';
import { computeRange, formatByUnit, formatDate, type RangeDays } from '../../lib/format';
import { postMetrics } from '../accounts/catalogMetrics';
import { PostThumb } from './PostThumb';

const PAGE_SIZE = 24;

const selectStyle: React.CSSProperties = {
  height: 30,
  borderRadius: 'var(--fg-radius-sm)',
  border: '1px solid var(--fg-border-strong)',
  background: 'var(--fg-bg-surface)',
  color: 'var(--fg-text-primary)',
  fontSize: 13,
  padding: '0 10px',
  cursor: 'pointer',
  maxWidth: 280,
};

export function AllPostsPage() {
  const { clientId, accountId } = useParams();
  const id = Number(accountId);
  const navigate = useNavigate();
  const catalog = useCatalog();

  const [range, setRange] = useState<RangeDays>(90);
  // valor combinado "key|dir"; 'published_at' = orden cronológico (campo snake_case
  // que acepta el backend; default HANDOFF §8). Por métrica = key del catálogo.
  const [sortValue, setSortValue] = useState('published_at|desc');
  const [page, setPage] = useState(0);
  const dr = useMemo(() => computeRange(range), [range]);

  const accountQ = useGetAccountsId(id, { query: { enabled: Number.isFinite(id) } });
  const account = accountQ.data?.data;
  const platform = account?.platform ?? '';
  const accountName = account?.handle || account?.displayName || `Cuenta ${id}`;

  const metricItems = useMemo(() => postMetrics(catalog, platform), [catalog, platform]);

  // sort=campo,dir (Spring). Para métrica, asumimos que el backend acepta la key
  // de la métrica como campo de orden (HANDOFF §6 "ordenables por métrica").
  const sortParam = sortValue.replace('|', ',');
  const [sortKey] = sortValue.split('|');
  const activeMetricKey = sortKey !== 'published_at' ? sortKey : null;

  const postsQ = useGetAccountsIdPosts(
    id,
    { from: dr.from, to: dr.to, page, size: PAGE_SIZE, sort: sortParam },
    { query: { enabled: Number.isFinite(id) } },
  );
  const data = postsQ.data?.data;
  const posts = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const onRange = (d: RangeDays) => {
    setRange(d);
    setPage(0);
  };
  const onSort = (v: string) => {
    setSortValue(v);
    setPage(0);
  };

  const openPost = (p: PostListItem) =>
    navigate(`/posts/${p.id}`, { state: { post: p, clientId, accountId } });

  return (
    <div>
      <button
        type="button"
        onClick={() => navigate(`/clients/${clientId}/accounts/${accountId}`)}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--fg-text-tertiary)', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
      >
        <span style={{ fontSize: 15 }}>‹</span>
        <span>Volver a la cuenta</span>
      </button>

      {/* header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 11, marginTop: 14, flexWrap: 'wrap' }}>
        <h1 style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
          Publicaciones
        </h1>
        <NetworkChip platform={platform} long />
        <span style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>{accountName}</span>
      </div>

      {/* controls */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, marginTop: 18, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)', textTransform: 'uppercase', letterSpacing: '.6px' }}>Ordenar por</span>
          <select aria-label="Ordenar por" value={sortValue} onChange={(e) => onSort(e.target.value)} style={selectStyle}>
            <option value="published_at|desc">Más recientes</option>
            <option value="published_at|asc">Más antiguas</option>
            {metricItems.map((it) => (
              <option key={it.key} value={`${it.key}|desc`}>
                {catalog.displayName(it.key)} (mayor)
              </option>
            ))}
          </select>
        </div>
        <DateRangeControl value={range} onChange={onRange} />
      </div>

      {/* count */}
      {postsQ.isSuccess && posts.length > 0 && (
        <div style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)', marginTop: 14 }}>
          {totalElements} {totalElements === 1 ? 'publicación' : 'publicaciones'} · {dr.label.toLowerCase()}
        </div>
      )}

      {/* grid */}
      <div style={{ marginTop: 16 }}>
        {postsQ.isLoading ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', gap: 16 }}>
            {Array.from({ length: 8 }).map((_, i) => (
              <Card key={i} padding={10}>
                <Skeleton width="100%" height={150} radius={10} />
                <Skeleton width="85%" height={12} style={{ marginTop: 10 }} />
                <Skeleton width="50%" height={11} style={{ marginTop: 7 }} />
              </Card>
            ))}
          </div>
        ) : postsQ.isError ? (
          <ErrorState error={postsQ.error} onRetry={() => postsQ.refetch()} />
        ) : posts.length === 0 ? (
          <EmptyState
            title="Aún no hay publicaciones para este rango"
            description="Probá ampliando el rango de fechas. Cuando la cuenta publique, sus piezas van a aparecer acá."
          />
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', gap: 16 }}>
            {posts.map((p) => (
              <Card key={p.id} interactive padding={10} onClick={() => openPost(p)}>
                <PostThumb src={p.remoteThumbnailUrl} postType={p.postType} />
                <div style={{ marginTop: 9, fontSize: 12.5, color: 'var(--fg-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {p.caption || 'Sin descripción'}
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8, marginTop: 6 }}>
                  <span style={{ fontSize: 11.5, color: 'var(--fg-text-tertiary)' }}>{formatDate(p.publishedAt)}</span>
                  {activeMetricKey && p.sortValue != null && (
                    <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                      {formatByUnit(p.sortValue, catalog.unit(activeMetricKey))}
                    </span>
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* pagination */}
      {postsQ.isSuccess && totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 14, marginTop: 22 }}>
          <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            ← Anterior
          </Button>
          <span style={{ fontSize: 13, color: 'var(--fg-text-secondary)' }}>
            Página {page + 1} de {totalPages}
          </span>
          <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Siguiente →
          </Button>
        </div>
      )}
    </div>
  );
}
