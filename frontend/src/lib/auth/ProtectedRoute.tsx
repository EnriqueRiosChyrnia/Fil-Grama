import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';

/** Spinner de pantalla completa para el estado de rehidratación. */
function BootSpinner() {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        color: 'var(--fg-text-secondary)',
      }}
    >
      <span
        style={{
          width: 18,
          height: 18,
          border: '2px solid var(--fg-border-strong)',
          borderTopColor: 'var(--fg-primary)',
          borderRadius: '50%',
          animation: 'fg-spin .7s linear infinite',
        }}
      />
      <span style={{ fontSize: 13 }}>Cargando tu sesión…</span>
    </div>
  );
}

/** Gate de autenticación. Se usa como layout-route (renderiza <Outlet/>). */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <BootSpinner />;
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}
