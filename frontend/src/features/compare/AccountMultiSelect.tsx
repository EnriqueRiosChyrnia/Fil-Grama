import { NetworkChip } from '../../components/ui';
import type { CompareAccount } from './compareData';
import { MAX_ACCOUNTS } from './compareData';

/**
 * Selector multi-cuenta de Comparar (HANDOFF §11): elegí hasta 4 cuentas
 * individuales, incluso varias de la misma red. Es POR CUENTA (no por red), por
 * eso no reutiliza el `NetworkAccountSelect` (que es cascada de selección única).
 */
const NET_ORDER = ['INSTAGRAM', 'FACEBOOK', 'TIKTOK'];

export function AccountMultiSelect({
  accounts,
  selectedIds,
  onToggle,
}: {
  accounts: CompareAccount[];
  selectedIds: number[];
  onToggle: (id: number) => void;
}) {
  const sorted = [...accounts].sort(
    (a, b) => NET_ORDER.indexOf(a.platform.toUpperCase()) - NET_ORDER.indexOf(b.platform.toUpperCase()),
  );
  const full = selectedIds.length >= MAX_ACCOUNTS;

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 10,
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg-text-secondary)' }}>Cuentas a comparar</span>
        <span style={{ fontSize: 12, color: 'var(--fg-text-tertiary)' }}>
          {selectedIds.length} / {MAX_ACCOUNTS}
        </span>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 9 }}>
        {sorted.map((a) => {
          const checked = selectedIds.includes(a.id);
          const disabled = !checked && full;
          const offline = (a.status ?? '').toUpperCase() !== 'CONNECTED';
          return (
            <button
              key={a.id}
              type="button"
              role="checkbox"
              aria-checked={checked}
              disabled={disabled}
              onClick={() => onToggle(a.id)}
              title={disabled ? `Máximo ${MAX_ACCOUNTS} cuentas` : undefined}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: '8px 12px',
                borderRadius: 'var(--fg-radius)',
                border: `1px solid ${checked ? 'var(--fg-primary)' : 'var(--fg-border-strong)'}`,
                background: checked ? 'var(--fg-blue-50)' : 'var(--fg-bg-surface)',
                color: 'var(--fg-text-primary)',
                fontSize: 13,
                cursor: disabled ? 'not-allowed' : 'pointer',
                opacity: disabled ? 0.5 : 1,
                transition: 'border-color .15s, background .15s',
              }}
            >
              <span
                aria-hidden
                style={{
                  width: 15,
                  height: 15,
                  borderRadius: 4,
                  border: `1.5px solid ${checked ? 'var(--fg-primary)' : 'var(--fg-border-strong)'}`,
                  background: checked ? 'var(--fg-primary)' : 'transparent',
                  color: 'var(--fg-on-primary)',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 11,
                  lineHeight: 1,
                }}
              >
                {checked ? '✓' : ''}
              </span>
              <NetworkChip platform={a.platform} />
              <span>{a.label}</span>
              {offline && (
                <span style={{ fontSize: 11, color: 'var(--fg-text-tertiary)' }}>· sin conexión</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
