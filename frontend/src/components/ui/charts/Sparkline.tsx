import { useId } from 'react';
import { ResponsiveContainer, AreaChart, Area } from 'recharts';

const BRAND = '#1E66BC';

/** Mini gráfico sin ejes para tarjetas (Home). `values` es la serie cruda. */
export function Sparkline({
  values,
  color = BRAND,
  height = 46,
}: {
  values: number[];
  color?: string;
  height?: number;
}) {
  const gradId = useId().replace(/:/g, '');
  const data = values.map((v, i) => ({ i, value: v }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.14} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <Area
          type="monotone"
          dataKey="value"
          stroke={color}
          strokeWidth={2}
          fill={`url(#${gradId})`}
          dot={false}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
