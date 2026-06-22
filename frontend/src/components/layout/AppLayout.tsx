import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { Topbar } from './Topbar';
import { LoadingState } from './states';

/**
 * Shell de la app autenticada: topbar fija + slot de contenido (Outlet).
 * Responsive: el contenido limita ancho y respira; los KPIs se apilan en móvil
 * (lo resuelve cada pantalla con grids auto-fit).
 */
export function AppLayout() {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--fg-bg-page)', display: 'flex', flexDirection: 'column' }}>
      <Topbar />
      <main style={{ flex: 1, width: '100%', maxWidth: 1180, margin: '0 auto', padding: '22px clamp(16px, 4vw, 28px) 40px' }}>
        <Suspense fallback={<LoadingState message="Cargando…" minHeight="60vh" />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
