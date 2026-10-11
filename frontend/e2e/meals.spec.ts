import { expect, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';

test('plant, isst und berücksichtigt die Historie bei Vorschlägen', async ({ page }) => {
  test.setTimeout(180_000);
  await page.setViewportSize({ width: 320, height: 720 });
  const suffix = `${Date.now()}`;
  const pizza = `E2E Planpizza ${suffix}`;
  const soup = `E2E Plansuppe ${suffix}`;
  const tomorrow = shiftDate(1);
  const yesterday = shiftDate(-1);

  await login(page);
  await createDish(page, pizza, 'Abendessen');
  await addVariant(page, 'Salami');
  const pizzaId = page.url().split('/').pop() ?? '';
  await createDish(page, soup, 'Abendessen');
  const soupId = page.url().split('/').pop() ?? '';

  await page.getByRole('link', { name: 'Plan', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Wochenplan' })).toBeVisible();
  await page.getByTestId('plan-next-week').click();
  await page.getByTestId('plan-current-week').click();
  await page.getByTestId('plan-create').click();
  await page.getByTestId('meal-date').fill(tomorrow);
  await page.getByTestId('meal-dish-search').fill(pizza);
  await page.getByTestId('meal-dish-option').filter({ hasText: pizza }).click();
  await page.getByTestId('meal-comment-toggle').click();
  await page.getByRole('button', { name: 'Salami', exact: true }).click();
  await page.getByRole('button', { name: 'Bea Beispiel', exact: true }).click();
  await page.getByTestId('meal-comment').fill('Für morgen');
  await page.getByTestId('meal-save').click();

  const planned = page.getByTestId('plan-entry').filter({ hasText: pizza });
  await expect(planned).toBeVisible();
  await expect(planned).toContainText('Salami');
  await expect(planned).toContainText('Bea Beispiel');
  await expect(planned).toContainText('Für morgen');
  await expectNoHorizontalScroll(page);

  await planned.getByTestId('plan-edit').click();
  await page.getByTestId('meal-comment').fill('Mit extra Käse');
  await page.getByTestId('meal-save').click();
  await expect(page.getByTestId('plan-entry').filter({ hasText: 'Mit extra Käse' })).toBeVisible();

  await page.getByTestId('plan-entry').filter({ hasText: pizza }).getByTestId('plan-eaten').click();
  await page.getByTestId('confirm-eaten').click();
  await expect(page.getByTestId('plan-entry').filter({ hasText: pizza })).toHaveCount(0);

  await page.getByRole('link', { name: 'Historie' }).click();
  await expect(page.getByRole('heading', { name: 'Was haben wir gegessen?' })).toBeVisible();
  const eatenPizza = page.getByTestId('history-entry').filter({ hasText: pizza });
  await expect(eatenPizza).toContainText('Salami');
  await expect(eatenPizza).toContainText('Mit extra Käse');
  await expect(eatenPizza).toContainText('Bea Beispiel');

  await page.getByTestId('history-create').click();
  await page.getByTestId('meal-date').fill(yesterday);
  await page.getByTestId('meal-dish-search').fill(soup);
  await page.getByTestId('meal-dish-option').filter({ hasText: soup }).click();
  await page.getByTestId('meal-save').click();
  const eatenSoup = page.getByTestId('history-entry').filter({ hasText: soup });
  await expect(eatenSoup).toBeVisible();
  await eatenSoup.getByTestId('history-edit').click();
  await page.getByTestId('meal-comment-toggle').click();
  await page.getByTestId('meal-comment').fill('Nachgetragen');
  await page.getByTestId('meal-save').click();
  await expect(page.getByTestId('history-entry').filter({ hasText: 'Nachgetragen' })).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByRole('link', { name: 'Heute' }).click();
  await page.getByRole('button', { name: 'Abendessen', exact: true }).click();
  await page.getByTestId('show-suggestions').click();
  await expect(page.getByTestId('suggestion-card').first()).toBeVisible();

  const before = await recommendationReasons(page, [soupId, pizzaId]);
  expect(before.find((item) => item.dishId === soupId)?.reason).toContain('Kürzlich gegessen');
  expect(before.find((item) => item.dishId === pizzaId)?.reason ?? '').not.toContain('Kürzlich gegessen');

  await page.getByRole('link', { name: 'Historie' }).click();
  await page.getByTestId('history-entry').filter({ hasText: soup }).getByTestId('history-delete').click();
  await page.getByTestId('confirm-delete').click();
  await expect(page.getByTestId('history-entry').filter({ hasText: soup })).toHaveCount(0);

  await page.getByRole('link', { name: 'Heute' }).click();
  await page.getByTestId('show-suggestions').click();
  await expect(page.getByTestId('suggestion-card').first()).toBeVisible();
  const after = await recommendationReasons(page, [soupId, pizzaId]);
  expect(after.find((item) => item.dishId === soupId)?.reason ?? '').not.toContain('Kürzlich gegessen');
  await expectNoHorizontalScroll(page);
});

function shiftDate(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

async function recommendationReasons(page: Page, dishIds: string[]) {
  const dishes = (await (await page.request.get('/api/dishes')).json()) as { id: string }[];
  const me = (await (await page.request.get('/api/auth/me')).json()) as { id: string };
  const cookies = await page.context().cookies();
  const csrf = decodeURIComponent(cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '');
  const response = await page.request.post('/api/recommendations', {
    headers: { 'X-XSRF-TOKEN': csrf },
    data: {
      mealType: 'ABENDESSEN',
      dinerIds: [me.id],
      preferSimple: false,
      preferHomeOffice: false,
      preferMealPrep: false,
      excludedDishIds: dishes.map((dish) => dish.id).filter((id) => !dishIds.includes(id)),
      seed: 11,
      referenceDate: shiftDate(0),
    },
  });
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as { suggestions: { dishId: string; reason: string }[] };
  return body.suggestions;
}

async function login(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
}

async function createDish(page: Page, name: string, meal: string) {
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(name);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByRole('group', { name: 'Mahlzeiten' }).getByRole('button', { name: meal, exact: true }).click();
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
}

async function addVariant(page: Page, name: string) {
  await page.getByTestId('add-variant').click();
  await page.getByTestId('variant-name').fill(name);
  await page.getByTestId('save-variant').click();
  await expect(page.getByTestId('variant-card').filter({ hasText: name })).toBeVisible();
}

async function expectNoHorizontalScroll(page: Page) {
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  );
  expect(overflows).toBe(false);
}
