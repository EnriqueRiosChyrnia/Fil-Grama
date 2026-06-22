import { Button } from '../../../components/ui';

/** Paginación simple (page es 0-based, como el backend). */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  if (totalElements === 0) return null;
  const safeTotal = Math.max(totalPages, 1);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        marginTop: 16,
        flexWrap: 'wrap',
      }}
    >
      <span style={{ fontSize: 12.5, color: 'var(--fg-text-tertiary)' }}>
        Página {page + 1} de {safeTotal} · {totalElements} en total
      </span>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          Anterior
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={page >= safeTotal - 1}
          onClick={() => onPage(page + 1)}
        >
          Siguiente
        </Button>
      </div>
    </div>
  );
}
