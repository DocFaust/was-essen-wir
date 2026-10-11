import { expect, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';

test('erfasst ein Gericht über die KI und speichert erst nach Prüfung', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const name = `E2E Gratin ${Date.now()}`;

  await login(page);
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('capture-ai').click();
  await page.getByTestId('draft-description').fill(
    'Wir machen oft Kartoffelgratin. Dauert etwa eine Stunde und ist gut zum Vorbereiten.',
  );
  await page.getByTestId('draft-submit').click();

  await expect(page.getByTestId('dish-name')).toHaveValue('Kartoffelgratin');
  await expect(page.getByTestId('draft-summary')).toContainText('Meal Prep');
  await expect(pressed(page, 'Aufwand')).toHaveText('Mittel');
  await expect(pressed(page, 'Meal Prep')).toHaveText('Geeignet');
  await page.getByTestId('dish-name').fill(name);
  await page.getByRole('group', { name: 'Aufwand' }).getByRole('button', { name: 'Einfach', exact: true }).click();
  await page.getByTestId('save-dish').click();

  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await expect(page.getByTestId('dish-card').filter({ hasText: name })).toBeVisible();
  await expectNoHorizontalScroll(page);
});

test('erstellt eine KI-Variante und behält die Vererbung', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const dish = `E2E Lasagne ${Date.now()}`;

  await login(page);
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(dish);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByRole('group', { name: 'Aufwand' }).getByRole('button', { name: 'Mittel', exact: true }).click();
  await page.getByRole('group', { name: 'Meal Prep' }).getByRole('button', { name: 'Geeignet', exact: true }).click();
  await page.getByTestId('ingredient-name').fill('Hackfleisch');
  await page.getByRole('button', { name: 'Zutat hinzufügen' }).click();
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name: dish, exact: true })).toBeVisible();

  await page.getByTestId('add-variant-ai').click();
  await page.getByTestId('draft-description').fill('Mit roten Linsen statt Hackfleisch.');
  await page.getByTestId('draft-submit').click();

  await expect(page.getByTestId('variant-name')).toHaveValue('Mit roten Linsen');
  await expect(page.getByText('roten Linsen')).toBeVisible();
  await expect(pressed(page, 'Aufwand')).toHaveText('Vom Grundgericht');
  await expect(pressed(page, 'Meal Prep')).toHaveText('Vom Grundgericht');
  await page.getByTestId('save-variant').click();

  await expect(page.getByRole('heading', { name: dish, exact: true })).toBeVisible();
  const card = page.getByTestId('variant-card');
  await expect(card).toContainText('Mit roten Linsen');
  await expect(card).toContainText('roten Linsen');
  await expect(card).not.toContainText('Hackfleisch');
  await expect(card).toContainText('Mittel · vom Grundgericht');
  await expectNoHorizontalScroll(page);
});

test('speichert keinen Entwurf, wenn die Erfassung abgebrochen wird', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const name = `E2E Abbruchgratin ${Date.now()}`;

  await login(page);
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('capture-ai').click();
  await page.getByTestId('draft-description').fill(`Wir machen oft ${name}.`);
  await page.getByTestId('draft-submit').click();
  await expect(page.getByTestId('dish-name')).toHaveValue(name);
  await page.getByRole('link', { name: 'Abbrechen' }).click();

  await expect(page.getByRole('heading', { name: 'Gerichte' })).toBeVisible();
  await expect(page.getByText(name)).toHaveCount(0);
  await expectNoHorizontalScroll(page);
});

function pressed(page: Page, group: string) {
  return page.getByRole('group', { name: group }).locator('[aria-pressed="true"]');
}

async function login(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(overflows).toBe(false);
}
