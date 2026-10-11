import { expect, type Page, test } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';
const widths = [320, 375, 430, 1280];

test('alltägliche Abläufe bleiben auf schmalen und breiten Displays bedienbar', async ({ page }) => {
  test.setTimeout(180_000);
  const dish = `E2E Alltag ${Date.now()}`;

  await page.setViewportSize({ width: 320, height: 720 });
  await login(page);
  await expect(page.getByTestId('today-panel')).toBeVisible();
  await expect(page.getByTestId('quick-eaten')).toBeVisible();
  await expect(page.getByTestId('show-suggestions')).toBeVisible();
  await expectChrome(page);

  await page.setViewportSize({ width: 375, height: 812 });
  await page.getByTestId('quick-new-dish').click();
  await page.getByTestId('dish-name').fill(dish);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByRole('group', { name: 'Mahlzeiten' }).getByRole('button', { name: 'Abendessen', exact: true }).click();
  await page.getByTestId('save-dish').click();
  await expect(page.getByRole('heading', { name: dish, exact: true })).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.setViewportSize({ width: 430, height: 932 });
  await page.getByRole('link', { name: 'Heute' }).click();
  await page.getByTestId('show-suggestions').click();
  const card = page.getByTestId('suggestion-card').first();
  await expect(card).toBeVisible();
  const suggestionName = (await card.getByTestId('suggestion-name').innerText()).trim();
  await card.getByTestId('suggestion-plan').click();
  await expect(page.getByTestId('meal-selected-dish')).toBeVisible();
  const save = page.getByTestId('meal-save');
  await expect(save).toBeEnabled();
  await expectInViewport(page, save);
  await save.click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByTestId('success-notice')).toContainText('Eingeplant');
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();

  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('link', { name: 'Plan', exact: true }).click();
  const planned = page.getByTestId('plan-entry').filter({ hasText: suggestionName });
  await expect(planned).toHaveCount(1);
  await expectChrome(page);
  await planned.getByTestId('plan-eaten').click();
  await page.getByTestId('confirm-eaten').click();
  await expect(page.getByTestId('plan-entry').filter({ hasText: suggestionName })).toHaveCount(0);

  await page.setViewportSize({ width: 375, height: 812 });
  await page.getByRole('link', { name: 'Heute' }).click();
  await page.getByTestId('quick-eaten').click();
  await page.getByTestId('meal-dish-search').fill(dish);
  await page.getByTestId('meal-dish-option').filter({ hasText: dish }).click();
  await page.getByTestId('meal-save').click();
  await expect(page.getByTestId('success-notice')).toContainText('Als gegessen eingetragen');
  await page.getByRole('link', { name: 'Historie' }).click();
  await expect(page.getByTestId('history-entry').filter({ hasText: dish })).toHaveCount(1);

  await page.setViewportSize({ width: 430, height: 932 });
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(dish);
  await page.getByRole('button', { name: 'Suchen' }).click();
  const dishCard = page.getByTestId('dish-card').filter({ hasText: dish });
  await expect(dishCard.getByRole('link', { name: dish })).toBeVisible();
  await dishCard.locator('label').filter({ hasText: /4 Stern/ }).click();
  await expect(dishCard.locator('[data-testid$="-status"]')).toHaveText('Esse ich gerne');
  await expectNoHorizontalScroll(page);

  await page.setViewportSize({ width: 320, height: 720 });
  await page.getByRole('link', { name: 'Heute' }).click();
  await page.getByTestId('ai-message').fill('Heute Abend etwas Schnelles, aber keine Pizza.');
  await page.getByTestId('ai-submit').click();
  await expect(page.getByTestId('ai-reply')).toBeVisible();
  await page.getByTestId('quick-eaten').click();
  await expectInViewport(page, page.getByTestId('meal-save'));
  await page.getByRole('dialog').getByRole('button', { name: 'Abbrechen' }).click();
  await expect(page.getByRole('navigation', { name: 'Hauptnavigation' })).toBeVisible();

  for (const width of widths) {
    await page.setViewportSize({ width, height: width >= 900 ? 900 : 720 });
    await page.getByRole('link', { name: 'Heute' }).click();
    await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
    await expectChrome(page);
    await page.getByRole('link', { name: 'Plan', exact: true }).click();
    await expect(page.getByTestId('plan-page')).toBeVisible();
    await expect(page.getByTestId('plan-day').first()).toBeVisible();
    await expectChrome(page);
  }
});

async function login(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
}

async function expectChrome(page: Page) {
  const nav = page.getByRole('navigation', { name: 'Hauptnavigation' });
  await expect(nav).toBeVisible();
  const box = await nav.boundingBox();
  const viewport = page.viewportSize();
  expect(box).not.toBeNull();
  expect(box!.y).toBeGreaterThanOrEqual(-1);
  expect(box!.y + box!.height).toBeLessThanOrEqual((viewport?.height ?? 0) + 1);
  const action = page.getByTestId('show-suggestions');
  if (await action.isVisible()) {
    const actionBox = await action.boundingBox();
    if (actionBox && box) {
      const overlaps = actionBox.y < box.y + box.height && actionBox.y + actionBox.height > box.y;
      expect(overlaps).toBe(false);
    }
  }
  await expectNoHorizontalScroll(page);
}

async function expectInViewport(page: Page, locator: ReturnType<Page['getByTestId']>) {
  await expect(locator).toBeVisible();
  const box = await locator.boundingBox();
  const viewport = page.viewportSize();
  expect(box).not.toBeNull();
  expect(box!.y).toBeGreaterThanOrEqual(-1);
  expect(box!.y + box!.height).toBeLessThanOrEqual((viewport?.height ?? 0) + 1);
}

async function expectNoHorizontalScroll(page: Page) {
  const overflows = await page.evaluate(() => {
    const root = document.documentElement.scrollWidth > document.documentElement.clientWidth + 1;
    const main = document.querySelector('main');
    const mainOverflow = main ? main.scrollWidth > main.clientWidth + 1 : false;
    return root || mainOverflow;
  });
  expect(overflows).toBe(false);
}
