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

// Navegación por pestañas del workspace + Reporte en vivo (:preview) + exportar.
// Cubre el "TERMINADO" del track: tabs, cuentas clickeables → detalle, Publicaciones
// (cascada → grilla → post), Comparar por tab, Reporte preview + descarga PDF.
test('workspace de cliente: tabs, drill-downs, reporte en vivo y exportar', async ({ page }) => {
  test.setTimeout(90_000);
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await login(page);

  // entra a un cliente con datos (drill-down SPA desde Home)
  await page.locator('text=La Cabrera').first().click();
  await expect(page).toHaveURL(/\/clients\/\d+$/, { timeout: 10_000 });
  const id = page.url().match(/clients\/(\d+)/)![1];

  // 1) las 4 pestañas del workspace están presentes
  for (const name of ['Dashboard', 'Publicaciones', 'Comparar', 'Reporte']) {
    await expect(page.getByRole('tab', { name })).toBeVisible();
  }

  // 2) Dashboard: la lista "Cuentas conectadas" es clickeable → detalle de cuenta
  await expect(page.getByText('Cuentas conectadas')).toBeVisible({ timeout: 15_000 });
  await page.locator('.fg-acctrow').first().click();
  await expect(page).toHaveURL(new RegExp(`/clients/${id}/accounts/\\d+$`), { timeout: 10_000 });
  await expect(page.getByText(NOT_FOUND)).toHaveCount(0);
  // el header con tabs persiste (no se re-montó la pantalla completa)
  await expect(page.getByRole('tab', { name: 'Reporte' })).toBeVisible();

  // 3) Reporte: vista en vivo desde :preview + exportar PDF
  await page.getByRole('tab', { name: 'Reporte' }).click();
  await expect(page).toHaveURL(new RegExp(`/clients/${id}/report$`), { timeout: 10_000 });
  await expect(page.getByText(/KPIs por red/i)).toBeVisible({ timeout: 20_000 });
  const exportBtn = page.getByRole('button', { name: /Exportar PDF/i });
  await expect(exportBtn).toBeEnabled({ timeout: 10_000 });
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 40_000 }),
    exportBtn.click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/\.pdf$/i);

  // 4) Comparar: la pantalla existente alcanzable por tab
  await page.getByRole('tab', { name: 'Comparar' }).click();
  await expect(page).toHaveURL(new RegExp(`/clients/${id}/compare$`), { timeout: 10_000 });
  await expect(page.getByText(NOT_FOUND)).toHaveCount(0);

  // 5) Publicaciones: cascada red→cuenta → grilla (AllPostsPage)
  await page.getByRole('tab', { name: 'Publicaciones' }).click();
  // esperar a que asiente la navegación del tab (React Router es async)
  await page.waitForURL(new RegExp(`/clients/${id}/(publicaciones|accounts/\\d+/posts)$`), { timeout: 10_000 });
  // multi-cuenta → selector; una sola cuenta → salto directo a la grilla
  if (/\/publicaciones$/.test(page.url())) {
    await expect(page.getByText('Elegí una cuenta')).toBeVisible({ timeout: 10_000 });
    await page.getByText('Ver publicaciones').first().click();
  }
  await expect(page).toHaveURL(new RegExp(`/clients/${id}/accounts/\\d+/posts$`), { timeout: 10_000 });
  await expect(page.getByRole('combobox', { name: 'Ordenar por' })).toBeVisible({ timeout: 15_000 });

  // 6) grilla → detalle de publicación
  await page.locator('div[style*="aspect-ratio"]').first().click();
  await expect(page).toHaveURL(/\/posts\/\d+$/, { timeout: 10_000 });
  await expect(page.getByText(NOT_FOUND)).toHaveCount(0);

  expect(errors, `errores de runtime: ${errors.join(' | ')}`).toEqual([]);
});
