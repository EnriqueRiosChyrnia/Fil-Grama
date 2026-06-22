import { Spinner } from '../../ui/Spinner';

/** Estado de carga reutilizable (centrado, con mensaje calmo). */
export function LoadingState({ message = 'Cargando…', minHeight = 220 }: { message?: string; minHeight?: number | string }) {
  return (
    <div
      style={{
        minHeight,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 9,
        color: 'var(--fg-text-secondary)',
      }}
    >
      <Spinner size={15} track="var(--fg-border-strong)" color="var(--fg-primary)" />
      <span style={{ fontSize: 12.5 }}>{message}</span>
    </div>
  );
}
