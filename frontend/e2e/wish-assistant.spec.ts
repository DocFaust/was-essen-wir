import { expect, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';

test('fragt den Assistenten und plant einen Vorschlag', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const soup = `E2E Assistentensuppe ${Date.now()}`;

  await login(page);
  await createDish(page, soup);
  await page.getByRole('link', { name: 'Start' }).click();
  await expect(page.getByTestId('ai-assistant')).toBeVisible();
  await page.getByTestId('ai-message').fill('Heute Abend etwas Schnelles');
  await page.getByTestId('ai-submit').click();

  await expect(page.getByTestId('ai-reply')).toContainText('Abendessen');
  await expect(page.getByTestId('ai-criteria')).toContainText('Abendessen');
  await expect(page.getByTestId('ai-criteria')).toContainText('Schnell oder einfach');
  const card = page.getByTestId('ai-assistant').getByTestId('suggestion-card').filter({ hasText: soup });
  await expect(card).toBeVisible();
  await expectNoHorizontalScroll(page);

  await card.getByTestId('suggestion-plan').click();
  await page.getByTestId('meal-save').click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.getByRole('link', { name: 'Plan' }).click();
  await expect(page.getByRole('heading', { name: 'Wochenplan' })).toBeVisible();
  await expect(page.getByTestId('plan-entry').filter({ hasText: soup })).toBeVisible();
  await expectNoHorizontalScroll(page);
});

async function login(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir?' })).toBeVisible();
}

async function createDish(page: Page, name: string) {
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(name);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByRole('group', { name: 'Mahlzeiten' }).getByRole('button', { name: 'Abendessen', exact: true }).click();
  await page.getByRole('group', { name: 'Aufwand' }).getByRole('button', { name: 'Einfach', exact: true }).click();
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(overflows).toBe(false);
}
