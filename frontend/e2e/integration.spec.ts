import { test, expect, type Page } from '@playwright/test';

const SEED = { email: 'admin@filgrama.local', password: 'Admin123!' };
const NOT_FOUND = 'No encontramos esta página';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(SEED.email);
  await page.getByLabel('Contraseña').fill(SEED.password);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Clientes' })).toBeVisible({ timeout: 10_000 });
}

// goto recarga → la sesión se rehidrata (access en memoria). Esperamos el shell
// (link "Inicio" del Topbar) antes de afirmar que la ruta resolvió a su pantalla.
async function visit(page: Page, path: string) {
  await page.goto(path);
  await expect(page.getByRole('link', { name: 'Inicio' })).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText(NOT_FOUND)).toHaveCount(0);
}

// Integración cross-track: cada ruta de los 4 tracks resuelve a su pantalla (no 404)
// y ninguna lanza excepción en runtime. Valida router por agregación + RBAC + shells.
test('rutas de los 4 tracks resuelven y renderizan sin crash', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await login(page);

  // FA — Dashboard de cliente (drill-down SPA desde Home, sin recarga)
  await page.locator('text=La Cabrera').first().click();
  await expect(page).toHaveURL(/\/clients\/\d+$/, { timeout: 10_000 });
  const id = page.url().match(/clients\/(\d+)/)![1];
  await expect(page.getByText(NOT_FOUND)).toHaveCount(0);

  // FA (wizard/reconexión) · FC (comparar/reporte) · FD (admin)
  for (const path of [
    `/clients/new`,
    `/clients/${id}/reconnect`,
    `/clients/${id}/compare`,
    `/clients/${id}/report`,
    `/admin`,
    `/admin/users`,
    `/admin/sync`,
  ]) {
    await visit(page, path);
  }

  expect(errors, `errores de runtime: ${errors.join(' | ')}`).toEqual([]);
});
