import { test, expect, type Page } from '@playwright/test';

const SEED = { email: 'admin@filgrama.local', password: 'Admin123!' };

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(SEED.email);
  await page.getByLabel('Contraseña').fill(SEED.password);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Clientes' })).toBeVisible({ timeout: 10_000 });
}

// Pestaña "Cuentas" (diseño "Gestionar redes"): lista agrupada por red, conectar,
// reconectar, desconectar, estados vacío + entrada desde el Dashboard.
test('Cuentas: tab, lista por red, conectar, reconectar y vacío', async ({ page }) => {
  test.setTimeout(90_000);
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await login(page);

  // 1) la 5ª pestaña existe en el workspace (Energym = 3 redes)
  await page.goto('/clients/4');
  for (const name of ['Dashboard', 'Publicaciones', 'Comparar', 'Reporte', 'Cuentas']) {
    await expect(page.getByRole('tab', { name })).toBeVisible({ timeout: 10_000 });
  }

  // 2) Cuentas: lista agrupada por red + contador + acción persistente
  await page.getByRole('tab', { name: 'Cuentas' }).click();
  await expect(page).toHaveURL(/\/clients\/4\/cuentas$/, { timeout: 10_000 });
  await expect(page.getByRole('heading', { name: 'Cuentas conectadas' })).toBeVisible({ timeout: 15_000 });
  for (const net of ['Instagram', 'Facebook', 'TikTok']) {
    await expect(page.getByText(net, { exact: true }).first()).toBeVisible();
  }
  const connectBtn = page.getByRole('button', { name: 'Conectar red' });
  await expect(connectBtn).toBeVisible();

  // 3) "Conectar red" abre el selector de red (modal)
  await connectBtn.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText('Conectar una red')).toBeVisible();
  for (const net of ['Instagram', 'Facebook', 'TikTok']) {
    await expect(page.getByRole('dialog').getByText(net, { exact: true })).toBeVisible();
  }
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // 4) Desconectar (UI) → la cuenta pasa a "Desconectada" + acción "Reconectar".
  //    Cliente 16 es de prueba; el test es autónomo (sirve esté conectada o no).
  await page.goto('/clients/16/cuentas');
  await expect(page.getByRole('heading', { name: 'Cuentas conectadas' })).toBeVisible({ timeout: 15_000 });
  const discBtn = page.getByRole('button', { name: 'Desconectar' });
  if (await discBtn.count()) {
    await discBtn.first().click();
  }
  await expect(page.getByText('Desconectada').first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'Reconectar' }).first()).toBeVisible();

  // 5) estado vacío (cliente sin redes) + CTA
  await page.goto('/clients/13/cuentas');
  await expect(page.getByText('Este cliente todavía no tiene redes conectadas')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: 'Conectar red' })).toBeVisible();

  // 6) punto de entrada: el Dashboard vacío lleva a Cuentas
  await page.goto('/clients/13');
  await expect(page.getByText('Este cliente todavía no tiene redes conectadas')).toBeVisible({ timeout: 15_000 });
  await page.getByRole('button', { name: 'Conectar red' }).click();
  await expect(page).toHaveURL(/\/clients\/13\/cuentas$/, { timeout: 10_000 });

  expect(errors, `errores de runtime: ${errors.join(' | ')}`).toEqual([]);
});
