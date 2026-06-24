/**
 * Helpers de estado de cuenta (territorio FA). Lenguaje humano, nunca el enum crudo
 * de la API. Compartido por el wizard (clients/new) y la reconexión.
 */
export type AccountStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR' | 'UNSUPPORTED' | 'UNKNOWN';

export function normStatus(s?: string | null): AccountStatus {
  const u = (s ?? '').toUpperCase();
  if (u === 'CONNECTED' || u === 'DISCONNECTED' || u === 'ERROR' || u === 'UNSUPPORTED') return u;
  return 'UNKNOWN';
}

export type StatusTone = 'ok' | 'bad' | 'muted';

export const STATUS_META: Record<AccountStatus, { label: string; tone: StatusTone }> = {
  CONNECTED: { label: 'Conectada', tone: 'ok' },
  ERROR: { label: 'Error de token', tone: 'bad' },
  DISCONNECTED: { label: 'Desconectada', tone: 'bad' },
  UNSUPPORTED: { label: 'No compatible', tone: 'muted' },
  UNKNOWN: { label: 'Sin estado', tone: 'muted' },
};

/** Cuenta que necesita reconexión (perdimos el acceso). */
export function isBroken(s?: string | null): boolean {
  const n = normStatus(s);
  return n === 'ERROR' || n === 'DISCONNECTED';
}

/** Redes que el operador puede conectar (orden de presentación). */
export const NETWORKS = ['INSTAGRAM', 'FACEBOOK', 'TIKTOK'] as const;
