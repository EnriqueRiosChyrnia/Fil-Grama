import type { ContentTypeShare, ProfileActivity, ViewsFollowerSplit } from '../../../api/generated/model';
import { formatCompact } from '../../../lib/format';
import { BarRow, BlockSection, StatRow, SubLabel } from './primitives';

/**
 * "Contenido": views por tipo, split seguidor/no-seguidor (solo VIEWS — el split de
 * interacciones no existe en el API) y actividad de perfil. Cada sub-bloque es
 * independiente y nullable; la sección entera se oculta si los tres están vacíos.
 */
export function ContentBlock({
  contentTypes,
  split,
  profileActivity,
}: {
  contentTypes?: ContentTypeShare[] | null;
  split?: ViewsFollowerSplit | null;
  profileActivity?: ProfileActivity | null;
}) {
  const types = (contentTypes ?? []).filter((c) => c.displayType && c.pct != null);
  const hasSplit = split?.followerPct != null && split?.nonFollowerPct != null;
  const activityRows = [
    profileActivity?.profileViews != null
      ? { label: 'Visitas al perfil', value: formatCompact(profileActivity.profileViews) }
      : null,
    profileActivity?.whatsappTaps != null
      ? { label: 'Clics al enlace de WhatsApp', value: formatCompact(profileActivity.whatsappTaps) }
      : null,
    profileActivity?.directionTaps != null
      ? { label: 'Clics en la ubicación', value: formatCompact(profileActivity.directionTaps) }
      : null,
  ].filter((r): r is { label: string; value: string } => r != null);

  if (types.length === 0 && !hasSplit && activityRows.length === 0) return null;

  return (
    <BlockSection title="Contenido">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        {types.length > 0 && (
          <div>
            <SubLabel>Views por tipo de contenido</SubLabel>
            {types.map((c, i) => (
              <BarRow key={c.displayType ?? i} label={c.displayType!} pct={c.pct!} />
            ))}
          </div>
        )}
        {hasSplit && (
          <div>
            <SubLabel>Vistas: seguidores vs. no seguidores</SubLabel>
            <BarRow label="Seguidores" pct={split!.followerPct!} />
            <BarRow label="No seguidores" pct={split!.nonFollowerPct!} />
          </div>
        )}
        {activityRows.length > 0 && (
          <div>
            <SubLabel>Actividad de perfil</SubLabel>
            {activityRows.map((r) => (
              <StatRow key={r.label} label={r.label} value={r.value} />
            ))}
          </div>
        )}
      </div>
    </BlockSection>
  );
}
