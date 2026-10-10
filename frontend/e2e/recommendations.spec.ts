import { expect, type Locator, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';
const memberPassword = 'e2e-mitglied-neu';

test('schlägt drei unterschiedliche Gerichte vor', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const suffix = `${Date.now()}`;
  const pizza = `E2E Pizza ${suffix}`;
  const soup = `E2E Suppe ${suffix}`;
  const gratin = `E2E Gratin ${suffix}`;
  const pan = `E2E Pfanne ${suffix}`;
  const breakfast = `E2E Müsli ${suffix}`;

  await login(page, 'Ada', adminPassword);
  await createDish(page, pizza, 'Abendessen');
  await addVariant(page, 'Margherita');
  await addVariant(page, 'Salami');
  await chooseStar(page.getByTestId('dish-rating'), 4);
  await chooseStar(page.getByTestId('variant-card').filter({ hasText: 'Margherita' }), 5);
  await createDish(page, soup, 'Abendessen', 'Einfach');
  await chooseStar(page.getByTestId('dish-rating'), 5);
  await createDish(page, gratin, 'Abendessen');
  await chooseStar(page.getByTestId('dish-rating'), 3);
  await createDish(page, pan, 'Abendessen');
  await createDish(page, breakfast, 'Frühstück');
  await chooseStar(page.getByTestId('dish-rating'), 5);

  await page.getByTestId('logout').click();
  await login(page, 'Bea', memberPassword);
  await openDish(page, pizza);
  await chooseStar(page.getByTestId('dish-rating'), 2);
  await chooseStar(page.getByTestId('variant-card').filter({ hasText: 'Salami' }), 5);
  await openDish(page, soup);
  await chooseStar(page.getByTestId('dish-rating'), 4);
  await openDish(page, gratin);
  await chooseStar(page.getByTestId('dish-rating'), 5);
  await openDish(page, pan);
  await chooseStar(page.getByTestId('dish-rating'), 4);

  await page.getByTestId('logout').click();
  await login(page, 'Ada', adminPassword);
  await page.getByRole('link', { name: 'Start' }).click();
  await expect(page.getByRole('heading', { name: 'Was essen wir?' })).toBeVisible();
  await page.getByRole('button', { name: 'Abendessen', exact: true }).click();
  await page.getByRole('button', { name: 'Bea Beispiel', exact: true }).click();
  await page.getByRole('switch', { name: 'Einfaches oder schnelles Essen' }).check();
  await page.getByTestId('show-suggestions').click();

  await expect(page.getByTestId('suggestion-card')).toHaveCount(3);
  const firstIds = await dishIds(page);
  expect(new Set(firstIds).size).toBe(3);
  await expect(page.getByTestId('suggestions')).not.toContainText(breakfast);
  await expect(page.getByTestId('suggestions')).not.toContainText('Stern');
  await expect(page.getByTestId('suggestions')).not.toContainText('Lieblingsessen');
  await expect(page.getByTestId('suggestions').locator('input[type="radio"]')).toHaveCount(0);
  await expectNoHorizontalScroll(page);

  await page.getByTestId('suggest-again').click();
  await expect(page.getByTestId('suggestion-card')).toHaveCount(3);
  const rerolled = await dishIds(page);
  expect(new Set(rerolled).size).toBe(3);

  const excludedId = rerolled[0];
  await page.getByTestId('suggestion-card').first().getByTestId('exclude-suggestion').click();
  await expect.poll(async () => {
    const ids = await dishIds(page);
    return ids.length === 3 && !ids.includes(excludedId);
  }).toBe(true);
  await expect(page.getByTestId('suggestions')).not.toContainText('Stern');
  await expectNoHorizontalScroll(page);
});

async function dishIds(page: Page): Promise<string[]> {
  const hrefs = await page.getByTestId('open-suggestion').evaluateAll((links) =>
    links.map((link) => link.getAttribute('href') ?? ''),
  );
  return hrefs.map((href) => href.split('/').pop() ?? '');
}

async function createDish(page: Page, name: string, meal: string, effort?: string) {
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(name);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByRole('group', { name: 'Mahlzeiten' }).getByRole('button', { name: meal, exact: true }).click();
  if (effort) {
    await page.getByRole('group', { name: 'Aufwand' }).getByRole('button', { name: effort, exact: true }).click();
  }
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

async function addVariant(page: Page, name: string) {
  await page.getByTestId('add-variant').click();
  await page.getByTestId('variant-name').fill(name);
  await page.getByTestId('save-variant').click();
  await expect(page.getByTestId('variant-card').filter({ hasText: name })).toBeVisible();
}

function chooseStar(scope: Locator, stars: number) {
  return scope.locator('label').filter({ hasText: new RegExp(`${stars} Stern`) }).click();
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill(username);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir?' })).toBeVisible();
}

async function openDish(page: Page, dishName: string) {
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(dishName);
  await page.getByRole('button', { name: 'Suchen' }).click();
  await page.getByRole('link', { name: dishName }).click();
  await expect(page.getByRole('heading', { name: dishName, exact: true })).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const scrolls = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(scrolls).toBe(false);
}
