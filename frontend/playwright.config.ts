import { defineConfig, devices } from '@playwright/test';

/**
 * Smoke E2E. Levanta el dev server de Vite (reusa si ya está). Requiere el backend
 * local en :8080 con CORS habilitado para http://localhost:5173 y el seed admin.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
