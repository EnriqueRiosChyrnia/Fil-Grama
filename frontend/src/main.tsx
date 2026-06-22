import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import './index.css';
import { createQueryClient } from './lib/query';
import { AuthProvider } from './lib/auth';
import { CatalogProvider } from './lib/catalog';
import { router } from './routes/router';

const queryClient = createQueryClient();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CatalogProvider>
          <RouterProvider router={router} />
        </CatalogProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
