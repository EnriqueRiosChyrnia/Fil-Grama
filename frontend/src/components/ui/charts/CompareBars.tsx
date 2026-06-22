import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Cell,
  LabelList,
} from 'recharts';
import { formatCompact } from '../../../lib/format';

const BRAND = '#1E66BC';
const MUTED = '#9CC4ED';

export interface CompareDatum {
  /** Etiqueta de la cuenta (handle / nombre corto). */
  label: string;
  value: number;
}

/**
 * Barras de "Comparar cuentas" para UNA métrica (HANDOFF §11). Cada métrica se
 * escala por separado → se usa un CompareBars por métrica. El mejor valor va
 * resaltado (color de marca; el resto, tono suave). `horizontal` para móvil.
 */
export function CompareBars({
  data,
  height = 220,
  horizontal = false,
  formatValue = formatCompact,
}: {
  data: CompareDatum[];
  height?: number;
  horizontal?: boolean;
  formatValue?: (v: number) => string;
}) {
  const max = Math.max(...data.map((d) => d.value), 0);
  const fill = (v: number) => (v === max && max > 0 ? BRAND : MUTED);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        data={data}
        layout={horizontal ? 'vertical' : 'horizontal'}
        margin={{ top: 16, right: 16, bottom: 0, left: horizontal ? 8 : 0 }}
      >
        {horizontal ? (
          <>
            <XAxis type="number" hide />
            <YAxis
              type="category"
              dataKey="label"
              axisLine={false}
              tickLine={false}
              width={92}
              tick={{ fontSize: 12, fill: '#647084' }}
            />
          </>
        ) : (
          <>
            <XAxis
              type="category"
              dataKey="label"
              axisLine={false}
              tickLine={false}
              tick={{ fontSize: 12, fill: '#647084' }}
            />
            <YAxis type="number" hide />
          </>
        )}
        <Bar dataKey="value" radius={horizontal ? [0, 6, 6, 0] : [6, 6, 0, 0]} isAnimationActive={false}>
          {data.map((d, i) => (
            <Cell key={i} fill={fill(d.value)} />
          ))}
          <LabelList
            dataKey="value"
            position={horizontal ? 'right' : 'top'}
            formatter={(v: unknown) => formatValue(Number(v ?? 0))}
            style={{ fontSize: 12, fill: '#232A36', fontWeight: 600 }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
