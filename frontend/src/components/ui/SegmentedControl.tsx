import type { ReactNode } from 'react';

export interface SegmentOption<T extends string | number> {
  value: T;
  label: ReactNode;
}

/**
 * Control segmentado (toggle de pestañas pequeño). Base del selector de métrica,
 * del filtro Todos/Prioritarios, del selector de red y del rango 7/30/90.
 */
export function SegmentedControl<T extends string | number>({
  options,
  value,
  onChange,
  size = 'md',
  fullWidth = false,
  ariaLabel,
}: {
  options: SegmentOption<T>[];
  value: T;
  onChange: (value: T) => void;
  size?: 'sm' | 'md';
  fullWidth?: boolean;
  ariaLabel?: string;
}) {
  const height = size === 'sm' ? 28 : 30;
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      style={{
        display: 'flex',
        background: 'var(--fg-bg-muted)',
        borderRadius: '9px',
        padding: 3,
        gap: 2,
        width: fullWidth ? '100%' : undefined,
      }}
    >
      {options.map((opt) => {
        const active = opt.value === value;
        return (
          <button
            key={String(opt.value)}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(opt.value)}
            style={{
              flex: fullWidth ? 1 : undefined,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height,
              padding: '0 14px',
              fontSize: 13,
              fontWeight: active ? 500 : 400,
              color: active ? 'var(--fg-text-primary)' : 'var(--fg-text-secondary)',
              background: active ? 'var(--fg-bg-surface)' : 'transparent',
              border: 'none',
              borderRadius: 'var(--fg-radius-sm)',
              boxShadow: active ? '0 1px 2px rgba(20,25,33,.08)' : 'none',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              transition: 'all .15s',
            }}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
