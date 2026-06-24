/** Piezas chicas de UI del track Clientes (wizard + reconexión). */
import { STATUS_META, normStatus, type StatusTone } from './accountStatus';

const TONE_STYLE: Record<StatusTone, { bg: string; fg: string }> = {
  ok: { bg: 'var(--fg-success-bg)', fg: 'var(--fg-success-fg)' },
  bad: { bg: 'var(--fg-danger-bg)', fg: 'var(--fg-danger-fg)' },
  neutral: { bg: 'var(--fg-gray-100)', fg: 'var(--fg-gray-600)' },
  muted: { bg: 'var(--fg-bg-muted)', fg: 'var(--fg-text-secondary)' },
};

/** Pastilla de estado de cuenta en lenguaje humano. */
export function StatusPill({ status }: { status?: string | null }) {
  const meta = STATUS_META[normStatus(status)];
  const s = TONE_STYLE[meta.tone];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 7,
        background: s.bg,
        color: s.fg,
        borderRadius: 7,
        padding: '4px 10px',
        fontSize: 12,
        fontWeight: 500,
        whiteSpace: 'nowrap',
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'currentColor' }} />
      {meta.label}
    </span>
  );
}

/** Indicador de pasos del wizard (1 · 2 · 3). */
export function Stepper({ steps, current }: { steps: string[]; current: number }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
      {steps.map((label, i) => {
        const done = i < current;
        const active = i === current;
        const on = done || active;
        return (
          <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <span
                style={{
                  width: 24,
                  height: 24,
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 12,
                  fontWeight: 600,
                  background: on ? 'var(--fg-primary)' : 'var(--fg-bg-muted)',
                  color: on ? 'var(--fg-on-primary)' : 'var(--fg-text-tertiary)',
                  flex: 'none',
                }}
              >
                {done ? '✓' : i + 1}
              </span>
              <span
                style={{
                  fontSize: 13,
                  fontWeight: active ? 600 : 500,
                  color: active ? 'var(--fg-text-primary)' : 'var(--fg-text-tertiary)',
                }}
              >
                {label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <span style={{ width: 26, height: 1, background: 'var(--fg-border-strong)' }} />
            )}
          </div>
        );
      })}
    </div>
  );
}
