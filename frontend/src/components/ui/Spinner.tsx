/** Spinner inline reutilizable. */
export function Spinner({ size = 16, color = 'currentColor', track = 'rgba(255,255,255,.45)' }: {
  size?: number;
  color?: string;
  track?: string;
}) {
  return (
    <span
      aria-hidden
      style={{
        display: 'inline-block',
        width: size,
        height: size,
        border: `2px solid ${track}`,
        borderTopColor: color,
        borderRadius: '50%',
        animation: 'fg-spin .7s linear infinite',
      }}
    />
  );
}
