import { useNavigate } from 'react-router-dom';
import { Card } from '../../components/ui';

interface Entry {
  to: string;
  title: string;
  description: string;
  icon: string;
}

const ENTRIES: Entry[] = [
  {
    to: '/admin/users',
    title: 'Usuarios',
    description: 'Altas, edición de rol y estado, y clientes prioritarios de cada empleado.',
    icon: '👤',
  },
  {
    to: '/admin/sync',
    title: 'Sincronización',
    description: 'Historial del job de captura diaria, detalle por cuenta y disparo manual.',
    icon: '🔄',
  },
];

export function AdminIndexPage() {
  const navigate = useNavigate();

  return (
    <div>
      <h1 style={{ fontSize: 24, fontWeight: 600, color: 'var(--fg-text-primary)', letterSpacing: '-.3px', margin: 0 }}>
        Administración
      </h1>
      <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', marginTop: 5 }}>
        Gestión interna de la agencia. Fuera del uso diario.
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 16,
          marginTop: 24,
        }}
      >
        {ENTRIES.map((e) => (
          <Card
            key={e.to}
            interactive
            onClick={() => navigate(e.to)}
            style={{ display: 'flex', flexDirection: 'column', gap: 10, minHeight: 132 }}
          >
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: 12,
                background: 'var(--fg-blue-50)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 22,
              }}
            >
              {e.icon}
            </div>
            <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--fg-text-primary)' }}>{e.title}</div>
            <div style={{ fontSize: 13, color: 'var(--fg-text-secondary)', lineHeight: 1.5 }}>{e.description}</div>
          </Card>
        ))}
      </div>
    </div>
  );
}
