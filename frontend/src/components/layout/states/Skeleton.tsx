/** Bloque shimmer para cargas (usa la clase global .fg-skeleton). */
export function Skeleton({
  width = '100%',
  height = 16,
  radius = 7,
  style,
}: {
  width?: number | string;
  height?: number | string;
  radius?: number | string;
  style?: React.CSSProperties;
}) {
  return <div className="fg-skeleton" style={{ width, height, borderRadius: radius, ...style }} />;
}
