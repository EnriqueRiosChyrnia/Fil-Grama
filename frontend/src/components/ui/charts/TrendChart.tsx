import { useId } from 'react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts';
import { formatCompact, formatDate } from '../../../lib/format';

export interface TrendPoint {
  /** Etiqueta del eje X (fecha ISO o texto). */
  x: string;
  value: number;
}

const BRAND = '#1E66BC';

/**
 * Tendencia principal (línea + área suave). Primitiva Recharts del Dashboard.
 * `xIsDate`: formatea las etiquetas como fecha corta.
 */
export function TrendChart({
  data,
  color = BRAND,
  height = 200,
  showGrid = true,
  showAxis = true,
  xIsDate = true,
}: {
  data: TrendPoint[];
  color?: string;
  height?: number;
  showGrid?: boolean;
  showAxis?: boolean;
  xIsDate?: boolean;
}) {
  const gradId = useId().replace(/:/g, '');
  const fmtX = (v: string) => (xIsDate ? formatDate(v) : v);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: 4 }}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.12} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        {showGrid && <CartesianGrid vertical={false} stroke="#F0F3F7" />}
        <XAxis
          dataKey="x"
          hide={!showAxis}
          tickFormatter={fmtX}
          axisLine={false}
          tickLine={false}
          tick={{ fontSize: 11, fill: '#8A95A6' }}
          minTickGap={24}
        />
        <YAxis hide domain={['auto', 'auto']} />
        <Tooltip
          formatter={(v: unknown) => [formatCompact(Number(v ?? 0)), '']}
          labelFormatter={(label: unknown) => fmtX(String(label ?? ''))}
          contentStyle={{
            border: '1px solid var(--fg-border)',
            borderRadius: 10,
            boxShadow: 'var(--fg-shadow-pop)',
            fontSize: 12,
          }}
        />
        <Area
          type="monotone"
          dataKey="value"
          stroke={color}
          strokeWidth={2.5}
          fill={`url(#${gradId})`}
          dot={false}
          activeDot={{ r: 4 }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
