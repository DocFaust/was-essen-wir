import { expect, test } from '@playwright/test';

test('Gericht anlegen, ergänzen, finden und bearbeiten', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 });
  const name = `Linsensuppe ${Date.now()}`;

  await page.goto('/');
  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('new-dish').click();
  await page.getByTestId('dish-name').fill(name);
  await page.getByRole('button', { name: 'Weitere Angaben' }).click();
  await page.getByTestId('dish-recipe-source').fill('Eigenkreation');
  await page.getByTestId('ingredient-name').fill('Linsen');
  await page.getByRole('button', { name: 'Zutat hinzufügen' }).click();
  await page.getByTestId('save-dish').click();

  await expect(page.getByRole('heading', { name })).toBeVisible();
  await expect(page.getByTestId('dish-recipe-source-value')).toContainText('Eigenkreation');
  await expect(page.getByText('Linsen', { exact: true })).toBeVisible();

  await page.getByTestId('add-variant').click();
  await page.getByTestId('variant-name').fill('Mit Würstchen');
  await page.getByTestId('save-variant').click();

  await expect(page.getByText('Mit Würstchen')).toBeVisible();
  await expect(page.getByTestId('resolved-recipe-source')).toContainText('Eigenkreation · vom Grundgericht');

  await page.getByRole('link', { name: 'Gerichte' }).click();
  await page.getByTestId('dish-search').fill(name);
  await page.getByRole('button', { name: 'Suchen' }).click();
  await page.getByRole('link', { name: new RegExp(name) }).click();

  await page.getByTestId('edit-dish').click();
  await page.getByTestId('dish-recipe-source').fill('Von Oma');
  await page.getByTestId('save-dish').click();

  await expect(page.getByTestId('dish-recipe-source-value')).toContainText('Von Oma');
  await expect(page.getByTestId('resolved-recipe-source')).toContainText('Von Oma · vom Grundgericht');
  await expect(page.getByText('Gericht gespeichert.')).toBeVisible();

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});
