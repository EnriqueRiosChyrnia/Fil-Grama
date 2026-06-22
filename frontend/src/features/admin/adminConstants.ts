/**
 * Constantes y helpers del track Administración (solo ADMIN).
 * Lenguaje humano, nunca jerga de API (HANDOFF §10).
 */
import type { GetUsersRole } from '../../api/generated/model';

export const ADMIN_PAGE_SIZE = 20;

/** Rol en español para la UI. */
export function roleLabel(role?: string): string {
  if (role === 'ADMIN') return 'Administrador';
  if (role === 'EMPLEADO') return 'Empleado';
  return '—';
}

export type RoleFilter = 'ALL' | 'ADMIN' | 'EMPLEADO';
export type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

export function roleFilterToParam(filter: RoleFilter): GetUsersRole | undefined {
  return filter === 'ALL' ? undefined : filter;
}

export function activeFilterToParam(filter: ActiveFilter): boolean | undefined {
  if (filter === 'ALL') return undefined;
  return filter === 'ACTIVE';
}

/** Colores + etiqueta para el estado de una corrida o cuenta (HANDOFF §5). */
export interface StatusMeta {
  label: string;
  bg: string;
  fg: string;
}

export function syncStatusMeta(status?: string): StatusMeta {
  switch ((status ?? '').toUpperCase()) {
    case 'SUCCESS':
    case 'OK':
      return { label: status?.toUpperCase() === 'OK' ? 'OK' : 'Completada', bg: 'var(--fg-success-bg)', fg: 'var(--fg-success-fg)' };
    case 'RUNNING':
      return { label: 'En curso', bg: 'var(--fg-blue-50)', fg: 'var(--fg-primary)' };
    case 'PARTIAL':
      return { label: 'Parcial', bg: '#FEF3C7', fg: '#92600A' };
    case 'FAILED':
    case 'ERROR':
      return { label: 'Falló', bg: 'var(--fg-danger-bg)', fg: 'var(--fg-danger-fg)' };
    case 'SKIPPED':
      return { label: 'Omitida', bg: 'var(--fg-gray-100)', fg: 'var(--fg-gray-600)' };
    default:
      return { label: status || '—', bg: 'var(--fg-gray-100)', fg: 'var(--fg-gray-600)' };
  }
}

const dateTimeFmt = new Intl.DateTimeFormat('es', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

/** Fecha + hora (las corridas necesitan hora; `formatDate` compartido solo da día/mes). */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : dateTimeFmt.format(d);
}
