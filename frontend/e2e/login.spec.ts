import { test, expect } from '@playwright/test';

const SEED = { email: 'admin@filgrama.local', password: 'Admin123!' };

test('login con el seed admin entra a la Home de Clientes', async ({ page }) => {
  await page.goto('/login');

  await page.getByLabel('Email').fill(SEED.email);
  await page.getByLabel('Contraseña').fill(SEED.password);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  // Tras login, la Home muestra el encabezado "Clientes".
  await expect(page.getByRole('heading', { name: 'Clientes' })).toBeVisible({ timeout: 10_000 });
  await expect(page).toHaveURL('http://localhost:5173/');
});

test('credenciales inválidas muestran error sin revelar si el email existe', async ({ page }) => {
  await page.goto('/login');

  await page.getByLabel('Email').fill('admin@filgrama.local');
  await page.getByLabel('Contraseña').fill('clave-incorrecta');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await expect(page.getByText('Email o contraseña incorrectos.')).toBeVisible({ timeout: 10_000 });
});
