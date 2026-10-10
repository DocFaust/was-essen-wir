import { expect, test } from '@playwright/test';

test('Startseite zeigt eine Verbindung zum Backend', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Was essen wir?' })).toBeVisible();
  await expect(page.getByTestId('backend-status')).toContainText('Backend erreichbar (was-essen-wir)', {
    timeout: 15_000,
  });
});
