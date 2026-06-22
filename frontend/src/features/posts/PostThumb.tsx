import { useState } from 'react';
import { postTypeLabel } from './postKind';

function PlaceholderIcon() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden>
      <rect x="3" y="4.5" width="18" height="15" rx="2.5" stroke="var(--fg-text-tertiary)" strokeWidth="1.6" />
      <circle cx="8.5" cy="10" r="1.6" stroke="var(--fg-text-tertiary)" strokeWidth="1.4" />
      <path d="M5 17l4.5-4.2 3 2.6L16 12l3 3.2" stroke="var(--fg-text-tertiary)" strokeWidth="1.6" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Miniatura de publicación (cuadrada). Usa `remoteThumbnailUrl`; si falta o falla
 * la carga, muestra un placeholder amable. Badge opcional con el tipo de post.
 */
export function PostThumb({
  src,
  postType,
  showBadge = true,
  radius = 10,
}: {
  src?: string | null;
  postType?: string | null;
  showBadge?: boolean;
  radius?: number;
}) {
  const [broken, setBroken] = useState(false);
  const ok = !!src && !broken;
  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        aspectRatio: '1 / 1',
        borderRadius: radius,
        overflow: 'hidden',
        background: 'var(--fg-bg-muted)',
        border: '1px solid var(--fg-border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {ok ? (
        <img
          src={src as string}
          alt=""
          loading="lazy"
          onError={() => setBroken(true)}
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : (
        <PlaceholderIcon />
      )}
      {showBadge && postType && (
        <span
          style={{
            position: 'absolute',
            top: 7,
            left: 7,
            fontSize: 10,
            fontWeight: 500,
            color: 'var(--fg-on-primary)',
            background: 'rgba(20,25,33,.62)',
            borderRadius: 6,
            padding: '2px 7px',
            lineHeight: 1.3,
            backdropFilter: 'blur(2px)',
          }}
        >
          {postTypeLabel(postType)}
        </span>
      )}
    </div>
  );
}
