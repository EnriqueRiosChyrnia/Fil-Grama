import type { ReactNode } from 'react';

export type SortDir = 'asc' | 'desc';

export interface Column<T> {
  key: string;
  header: ReactNode;
  /** Fracción/medida CSS para grid-template-columns (ej. '1.7fr', '52px'). */
  width?: string;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
  render: (row: T) => ReactNode;
}

export interface SortState {
  key: string;
  dir: SortDir;
}

/**
 * Tabla en grilla (estilo "Mejores publicaciones"). Ordenamiento controlado: el
 * padre tiene `sort` y reordena los `rows`; acá sólo se emite `onSort(key)`.
 */
export function Table<T>({
  columns,
  rows,
  rowKey,
  sort,
  onSort,
  onRowClick,
  empty,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string | number;
  sort?: SortState;
  onSort?: (key: string) => void;
  onRowClick?: (row: T) => void;
  empty?: ReactNode;
}) {
  const template = columns.map((c) => c.width ?? '1fr').join(' ');
  const arrow = (key: string) => (sort?.key === key ? (sort.dir === 'desc' ? ' ↓' : ' ↑') : '');

  return (
    <div style={{ width: '100%' }}>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: template,
          gap: 12,
          padding: '0 20px 9px',
          fontSize: 11,
          textTransform: 'uppercase',
          letterSpacing: '.5px',
          color: 'var(--fg-text-tertiary)',
          borderBottom: '1px solid #F0F3F7',
        }}
      >
        {columns.map((c) => (
          <div
            key={c.key}
            onClick={c.sortable && onSort ? () => onSort(c.key) : undefined}
            style={{
              textAlign: c.align ?? 'left',
              cursor: c.sortable && onSort ? 'pointer' : undefined,
              userSelect: 'none',
            }}
          >
            {c.header}
            {c.sortable ? arrow(c.key) : ''}
          </div>
        ))}
      </div>

      {rows.length === 0
        ? empty ?? null
        : rows.map((row, i) => (
            <div
              key={rowKey(row, i)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              style={{
                display: 'grid',
                gridTemplateColumns: template,
                gap: 12,
                alignItems: 'center',
                padding: '11px 20px',
                borderBottom: '1px solid #F4F6F9',
                cursor: onRowClick ? 'pointer' : undefined,
                fontSize: 13,
                color: 'var(--fg-text-primary)',
              }}
            >
              {columns.map((c) => (
                <div key={c.key} style={{ textAlign: c.align ?? 'left', minWidth: 0 }}>
                  {c.render(row)}
                </div>
              ))}
            </div>
          ))}
    </div>
  );
}
