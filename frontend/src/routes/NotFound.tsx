import { Link } from 'react-router-dom';

export function NotFound() {
  return (
    <div style={{ minHeight: '60vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 10, textAlign: 'center' }}>
      <div style={{ fontSize: 22, fontWeight: 600, color: 'var(--fg-text-primary)' }}>No encontramos esta página</div>
      <div style={{ fontSize: 14, color: 'var(--fg-text-secondary)' }}>Puede que el enlace sea viejo o que la sección todavía no exista.</div>
      <Link to="/" style={{ color: 'var(--fg-primary)', fontSize: 14, fontWeight: 600, marginTop: 6 }}>
        Volver a Clientes
      </Link>
    </div>
  );
}
