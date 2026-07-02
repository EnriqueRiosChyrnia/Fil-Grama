import type { Demographics, Segment } from '../../../api/generated/model';
import { BarRow, BlockSection, SubLabel } from './primitives';

const TOP_N = 5;

function TopList({ title, items }: { title: string; items?: Segment[] }) {
  const rows = (items ?? []).filter((s) => s.label && s.pct != null).slice(0, TOP_N);
  if (rows.length === 0) return null;
  return (
    <div>
      <SubLabel>{title}</SubLabel>
      {rows.map((s, i) => (
        <BarRow key={s.label ?? i} label={s.label!} pct={s.pct!} />
      ))}
    </div>
  );
}

/** "Público": demografía de seguidores (ciudades/países/edad/género). Nullable → oculta. */
export function DemographicsBlock({ data }: { data?: Demographics | null }) {
  if (!data || data.empty) return null;
  const cities = data.cities ?? [];
  const countries = data.countries ?? [];
  const ageRanges = data.ageRanges ?? [];
  const genders = data.genders ?? [];
  if (!cities.length && !countries.length && !ageRanges.length && !genders.length) return null;

  return (
    <BlockSection title="Público">
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 20 }}>
        <TopList title="Principales ciudades" items={cities} />
        <TopList title="Principales países" items={countries} />
        <TopList title="Rangos de edad" items={ageRanges} />
        <TopList title="Género" items={genders} />
      </div>
    </BlockSection>
  );
}
