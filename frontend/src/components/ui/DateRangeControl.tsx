import { SegmentedControl } from './SegmentedControl';
import type { RangeDays } from '../../lib/format';

/** Etiqueta corta por rango (el segmented no entra en texto largo). */
const SHORT_LABEL: Record<RangeDays, string> = {
  7: '7 días',
  30: '30 días',
  90: '90 días',
  365: '1 año',
  all: 'Todo',
};

/**
 * Selector de rango (Dashboard usa 7/30/90 por defecto). Controlado: el padre
 * guarda el valor y deriva {from,to} con computeRange(). Para Home se usa fijo en
 * 7 días. Pasá `options={WIDE_RANGES}` para sumar "1 año" y "Todo" (detalle de
 * cuenta / posts), donde hace falta ver el histórico.
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
      options={options.map((d) => ({ value: d, label: SHORT_LABEL[d] }))}
    />
  );
}
