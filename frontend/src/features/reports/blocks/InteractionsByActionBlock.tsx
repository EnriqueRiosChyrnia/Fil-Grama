import type { Kpi } from '../../../api/generated/model';
import { formatByUnit } from '../../../lib/format';
import { BlockSection, StatRow } from './primitives';

/** "Interacciones": me gusta/comentarios/compartidos/guardados/reposts sumados del período. */
export function InteractionsByActionBlock({ items }: { items?: Kpi[] | null }) {
  const rows = (items ?? []).filter((k) => k.value != null);
  if (rows.length === 0) return null;

  return (
    <BlockSection title="Interacciones">
      {rows.map((k, i) => (
        <StatRow key={k.key ?? i} label={k.displayName ?? k.key ?? ''} value={formatByUnit(k.value, k.unit)} />
      ))}
    </BlockSection>
  );
}
