import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../lib/auth';
import { LogoHorizontal } from '../brand/Logo';

function initials(name?: string): string {
  if (!name) return '··';
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
}

function navStyle({ isActive }: { isActive: boolean }): React.CSSProperties {
  return {
    fontSize: 13,
    color: isActive ? 'var(--fg-text-primary)' : 'var(--fg-text-secondary)',
    fontWeight: isActive ? 600 : 400,
  };
}

/** Barra superior compartida: marca + navegación (RBAC) + menú de usuario. */
export function Topbar() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <header
      style={{
        height: 60,
        background: 'var(--fg-bg-surface)',
        borderBottom: '1px solid var(--fg-border)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 20,
      }}
    >
      <NavLink to="/" aria-label="Inicio" style={{ display: 'flex', alignItems: 'center' }}>
        <LogoHorizontal height={30} />
      </NavLink>

      {/* Nav top-level CONGELADO (Fase1 §0.2): Home siempre; Administración solo ADMIN.
          Los tracks NO editan Topbar; la navegación contextual va por path string. */}
      <nav style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
        <NavLink to="/" end style={navStyle}>
          Clientes
        </NavLink>
        {isAdmin && (
          <NavLink to="/admin" style={navStyle}>
            Administración
          </NavLink>
        )}

        <div style={{ position: 'relative' }}>
          <button
            onClick={() => setMenuOpen((v) => !v)}
            aria-label="Menú de usuario"
            aria-expanded={menuOpen}
            style={{
              width: 32,
              height: 32,
              borderRadius: '50%',
              background: 'var(--fg-gray-100)',
              border: 'none',
              color: 'var(--fg-gray-600)',
              fontSize: 12,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            {initials(user?.fullName)}
          </button>
          {menuOpen && (
            <div
              style={{
                position: 'absolute',
                right: 0,
                top: 40,
                minWidth: 200,
                background: 'var(--fg-bg-surface)',
                border: '1px solid var(--fg-border)',
                borderRadius: 11,
                boxShadow: 'var(--fg-shadow-pop)',
                padding: 8,
                zIndex: 40,
              }}
            >
              <div style={{ padding: '8px 10px 10px' }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--fg-text-primary)' }}>
                  {user?.fullName}
                </div>
                <div style={{ fontSize: 12, color: 'var(--fg-text-secondary)' }}>{user?.email}</div>
                <div style={{ fontSize: 11, color: 'var(--fg-text-tertiary)', marginTop: 2 }}>
                  {user?.role === 'ADMIN' ? 'Administrador' : 'Empleado'}
                </div>
              </div>
              <button
                onClick={onLogout}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '9px 10px',
                  border: 'none',
                  background: 'transparent',
                  color: 'var(--fg-text-primary)',
                  fontSize: 13,
                  borderRadius: 8,
                  cursor: 'pointer',
                }}
              >
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </nav>
    </header>
  );
}
