import { expect, type Locator, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';
const memberPassword = 'e2e-mitglied-neu';

test('bewertet Gerichte und Varianten nur für die angemeldete Person', async ({ page }) => {
  test.setTimeout(90_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const dishName = `E2E Bewertung ${Date.now()}`;

  await login(page, 'Ada', adminPassword);
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(dishName);
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name: dishName })).toBeVisible();

  await page.getByTestId('add-variant').click();
  await page.getByTestId('variant-name').fill('Margherita');
  await page.getByTestId('save-variant').click();
  await expect(page.getByTestId('variant-card').filter({ hasText: 'Margherita' })).toBeVisible();

  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(dishName);
  await page.getByRole('button', { name: 'Suchen' }).click();
  const card = page.getByTestId('dish-card').filter({ hasText: dishName });
  await chooseStar(card, 4);
  await expect(ratingStatus(card)).toHaveText('Esse ich gerne');
  await expectNoHorizontalScroll(page);

  await card.getByRole('link', { name: dishName }).click();
  const variant = page.getByTestId('variant-card').filter({ hasText: 'Margherita' });
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Esse ich gerne');
  await expect(ratingStatus(variant)).toHaveText('Nicht bewertet');
  await chooseStar(variant, 5);
  await expect(ratingStatus(variant)).toHaveText('Lieblingsessen');

  await page.reload();
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Esse ich gerne');
  await expect(ratingStatus(page.getByTestId('variant-card').filter({ hasText: 'Margherita' }))).toHaveText('Lieblingsessen');

  await chooseStar(page.getByTestId('dish-rating'), 2);
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Esse ich ungern');
  await page
    .getByTestId('variant-card')
    .filter({ hasText: 'Margherita' })
    .getByRole('button', { name: /Bewertung für Margherita entfernen/ })
    .click();
  await expect(ratingStatus(page.getByTestId('variant-card').filter({ hasText: 'Margherita' }))).toHaveText('Nicht bewertet');
  await expectNoHorizontalScroll(page);
  expect(await page.evaluate(() => localStorage.length)).toBe(0);

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await login(page, 'Bea', memberPassword);
  await openDish(page, dishName);
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Nicht bewertet');
  await expect(ratingStatus(page.getByTestId('variant-card').filter({ hasText: 'Margherita' }))).toHaveText('Nicht bewertet');
  await expect(page.getByTestId('dish-rating').locator('input[value="2"]')).not.toBeChecked();

  await chooseStar(page.getByTestId('dish-rating'), 1);
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Mag ich überhaupt nicht');

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await login(page, 'Ada', adminPassword);
  await openDish(page, dishName);
  await expect(ratingStatus(page.getByTestId('dish-rating'))).toHaveText('Esse ich ungern');
  await expect(page.getByTestId('dish-rating').locator('input[value="2"]')).toBeChecked();
  await expect(ratingStatus(page.getByTestId('variant-card').filter({ hasText: 'Margherita' }))).toHaveText('Nicht bewertet');
  await expect(page.getByTestId('dish-rating').locator('input[value="1"]')).not.toBeChecked();
});

function ratingStatus(scope: Locator) {
  return scope.locator('[data-testid$="-status"]');
}

async function chooseStar(scope: Locator, stars: number) {
  await scope.locator('label').filter({ hasText: new RegExp(`${stars} Stern`) }).click();
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill(username);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
}

async function openDish(page: Page, dishName: string) {
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(dishName);
  await page.getByRole('button', { name: 'Suchen' }).click();
  await page.getByRole('link', { name: dishName }).click();
  await expect(page.getByRole('heading', { name: dishName })).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const scrolls = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(scrolls).toBe(false);
}
