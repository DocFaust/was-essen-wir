import { expect, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';
const temporaryPassword = 'e2e-member-passwort';
const memberPassword = 'e2e-mitglied-neu';

test('richtet die Familie ein und schützt Gerichte nach dem Abmelden', async ({ page }) => {
  test.setTimeout(60_000);
  const token = process.env.E2E_SETUP_TOKEN;
  if (!token) {
    throw new Error('E2E_SETUP_TOKEN fehlt. Das Startskript liest es aus den Backend-Logs.');
  }

  await page.setViewportSize({ width: 320, height: 720 });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Einrichtung' })).toBeVisible();
  await expect(page.getByText('SETUP_TOKEN')).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByTestId('setup-username').fill('Ada');
  await page.getByTestId('setup-display-name').fill('Ada Lovelace');
  await page.getByTestId('setup-password').fill(adminPassword);
  await page.getByTestId('setup-password-confirm').fill(adminPassword);
  await page.getByTestId('setup-token').fill(token);
  await page.getByTestId('setup-submit').click();

  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
  await expect(page.getByTestId('current-user')).toHaveText('Ada Lovelace');

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByRole('link', { name: 'Familie' }).click();
  await page.getByTestId('new-member').click();
  await page.getByTestId('member-username').fill('Bea');
  await page.getByTestId('member-display-name').fill('Bea Beispiel');
  await page.getByTestId('member-password').fill(temporaryPassword);
  await page.getByTestId('member-save').click();
  await expect(page.getByText('Bea Beispiel', { exact: true })).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Bea');
  await page.getByTestId('login-password').fill(temporaryPassword);
  await page.getByTestId('login-submit').click();

  await expect(page.getByRole('heading', { name: 'Passwort ändern' })).toBeVisible();
  await page.getByRole('textbox', { name: 'Bisheriges Passwort', exact: true }).fill(temporaryPassword);
  await page.getByRole('textbox', { name: 'Neues Passwort', exact: true }).fill(memberPassword);
  await page.getByRole('textbox', { name: 'Neues Passwort wiederholen', exact: true }).fill(memberPassword);
  await page.getByRole('button', { name: 'Passwort speichern' }).click();

  const dishName = `E2E Eintopf ${Date.now()}`;
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(dishName);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByTestId('dish-recipe-source').fill('Eigenkreation');
  await page.getByTestId('ingredient-name').fill('Linsen');
  await page.getByRole('button', { name: 'Zutat hinzufügen' }).click();
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name: dishName })).toBeVisible();
  await expect(page.getByTestId('dish-recipe-source-value')).toContainText('Eigenkreation');
  await expect(page.getByText('Linsen', { exact: true })).toBeVisible();

  await page.getByTestId('add-variant').click();
  await page.getByTestId('variant-name').fill('Mit Würstchen');
  await page.getByTestId('save-variant').click();
  await expect(page.getByText('Mit Würstchen', { exact: true })).toBeVisible();
  await expect(page.getByTestId('resolved-recipe-source')).toContainText('Eigenkreation · vom Grundgericht');

  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(dishName);
  await page.getByRole('button', { name: 'Suchen' }).click();
  await page.getByRole('link', { name: dishName }).click();

  await page.getByTestId('edit-dish').click();
  await expect(page.getByTestId('dish-name')).toHaveValue(dishName);
  await page.getByTestId('dish-name').fill(`${dishName} geändert`);
  await page.getByTestId('dish-recipe-source').fill('Von Oma');
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name: `${dishName} geändert` })).toBeVisible();
  await expect(page.getByTestId('dish-recipe-source-value')).toContainText('Von Oma');
  await expect(page.getByTestId('resolved-recipe-source')).toContainText('Von Oma · vom Grundgericht');
  await expect(page.getByText('Gericht gespeichert.')).toBeVisible();
  await expectNoHorizontalScroll(page);

  await expect(page.getByRole('link', { name: 'Familie' })).toHaveCount(0);
  await page.goto('/benutzer');
  await expect(page.getByText('Diese Seite ist nur für die Administration.')).toBeVisible();
  expect((await page.request.get('/api/users')).status()).toBe(403);

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  expect((await page.request.get('/api/dishes')).status()).toBe(401);
});

async function expectNoHorizontalScroll(page: Page) {
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(overflows).toBe(false);
}
