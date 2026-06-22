import { SegmentedControl } from './SegmentedControl';
import type { RangeDays } from '../../lib/format';

/**
 * Selector de rango 7/30/90 días (Dashboard). Controlado: el padre guarda el valor
 * y deriva {from,to} con computeRange(). Para Home se usa fijo en 7 días.
 */
export function DateRangeControl({
  value,
  onChange,
  options = [7, 30, 90],
  fullWidth,
}: {
  value: RangeDays;
  onChange: (days: RangeDays) => void;
  options?: RangeDays[];
  fullWidth?: boolean;
}) {
  return (
    <SegmentedControl<RangeDays>
      ariaLabel="Rango de fechas"
      value={value}
      onChange={onChange}
      fullWidth={fullWidth}
      options={options.map((d) => ({ value: d, label: `${d} días` }))}
    />
  );
}
