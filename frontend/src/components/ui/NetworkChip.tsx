import { networkLabel, type Platform } from './networks';

/** Chip de red social. `long` usa "TikTok" en vez de "TT". */
export function NetworkChip({ platform, long = false }: { platform?: Platform | null; long?: boolean }) {
  const label = networkLabel(platform, long);
  if (!label) return null;
  return (
    <span
      style={{
        fontSize: 10,
        fontWeight: 500,
        color: 'var(--fg-text-secondary)',
        background: 'var(--fg-gray-50)',
        border: '1px solid var(--fg-border)',
        borderRadius: 6,
        padding: '3px 6px',
        lineHeight: 1.2,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </span>
  );
}
