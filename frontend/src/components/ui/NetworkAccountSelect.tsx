import { SegmentedControl } from './SegmentedControl';
import { networkLabel } from './networks';

/**
 * Selector en cascada red → cuenta (HANDOFF §10 multi-cuenta). Un cliente puede
 * tener varias cuentas de la misma red, así que el filtro es POR CUENTA, no sólo
 * por red. Controlado: el padre guarda `value` y reacciona en `onChange`.
 *
 * Comportamiento: se elige la red; si esa red tiene >1 cuenta, aparece el segundo
 * nivel para elegir la cuenta concreta (o "Todas" las de esa red).
 */
export interface AccountLike {
  id: number;
  platform: string;
  handle?: string;
  displayName?: string;
  status?: string;
}

export interface NetworkAccountValue {
  platform: string | 'ALL';
  accountId: number | 'ALL';
}

const NET_ORDER = ['INSTAGRAM', 'FACEBOOK', 'TIKTOK'];

function accountLabel(a: AccountLike): string {
  return a.handle || a.displayName || `Cuenta ${a.id}`;
}

export function NetworkAccountSelect({
  accounts,
  value,
  onChange,
  includeAllNetworks = true,
}: {
  accounts: AccountLike[];
  value: NetworkAccountValue;
  onChange: (value: NetworkAccountValue) => void;
  includeAllNetworks?: boolean;
}) {
  const platforms = Array.from(new Set(accounts.map((a) => a.platform.toUpperCase()))).sort(
    (a, b) => NET_ORDER.indexOf(a) - NET_ORDER.indexOf(b),
  );

  const networkOptions = [
    ...(includeAllNetworks ? [{ value: 'ALL' as const, label: 'Todas' }] : []),
    ...platforms.map((p) => ({ value: p, label: networkLabel(p) })),
  ];

  const inNet =
    value.platform === 'ALL' ? [] : accounts.filter((a) => a.platform.toUpperCase() === value.platform);

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
      <SegmentedControl<string>
        ariaLabel="Red social"
        value={value.platform}
        onChange={(platform) => onChange({ platform, accountId: 'ALL' })}
        options={networkOptions}
      />
      {inNet.length > 1 && (
        <select
          aria-label="Cuenta"
          value={String(value.accountId)}
          onChange={(e) =>
            onChange({
              platform: value.platform,
              accountId: e.target.value === 'ALL' ? 'ALL' : Number(e.target.value),
            })
          }
          style={{
            height: 30,
            borderRadius: 'var(--fg-radius-sm)',
            border: '1px solid var(--fg-border-strong)',
            background: 'var(--fg-bg-surface)',
            color: 'var(--fg-text-primary)',
            fontSize: 13,
            padding: '0 10px',
            cursor: 'pointer',
          }}
        >
          <option value="ALL">Todas las cuentas</option>
          {inNet.map((a) => (
            <option key={a.id} value={a.id}>
              {accountLabel(a)}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
