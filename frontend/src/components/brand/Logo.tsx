/** Logos de marca servidos desde /public/brand (vendorizados de design/*.svg). */
export function Isotipo({ size = 32, rounded = true, shadow = false }: { size?: number; rounded?: boolean; shadow?: boolean }) {
  return (
    <img
      src="/brand/isotipo.svg"
      alt="Fil-Grama"
      width={size}
      height={size}
      style={{
        display: 'block',
        borderRadius: rounded ? Math.round(size * 0.27) : 0,
        boxShadow: shadow ? '0 4px 12px rgba(30,102,188,.28)' : undefined,
      }}
    />
  );
}

export function LogoHorizontal({ height = 30 }: { height?: number }) {
  return <img src="/brand/logo-horizontal.svg" alt="Fil-Grama" style={{ height, display: 'block' }} />;
}
