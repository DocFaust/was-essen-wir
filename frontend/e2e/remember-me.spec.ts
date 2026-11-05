import { expect, test, type Page } from '@playwright/test';

const adminPassword = 'e2e-admin-passwort';

test('bleibt auf diesem Gerät angemeldet, nachdem die Sitzung weg ist', async ({ page }) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 320, height: 720 });
  await login(page, false);
  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();

  const remember = page.getByTestId('login-remember');
  await expect(remember).not.toBeChecked();
  const box = await page.locator('label').filter({ hasText: 'Angemeldet bleiben' }).boundingBox();
  expect(box?.height).toBeGreaterThanOrEqual(44);
  expect(box?.width).toBeGreaterThanOrEqual(44);

  await login(page, true);
  const cookies = await page.context().cookies();
  const rememberCookie = cookies.find((cookie) => cookie.name === 'WAEW_REMEMBER');
  expect(rememberCookie?.httpOnly).toBe(true);
  expect(rememberCookie?.secure).toBe(false);
  expect(rememberCookie?.sameSite).toBe('Lax');
  const stored = await page.evaluate(() => JSON.stringify(localStorage) + JSON.stringify(sessionStorage));
  expect(stored).not.toContain('WAEW_REMEMBER');

  await page.addInitScript(() => {
    const mark = () => {
      if ([...document.querySelectorAll('h1')].some((node) => node.textContent === 'Anmelden')) {
        (window as unknown as { __loginSeen?: boolean }).__loginSeen = true;
      }
    };
    new MutationObserver(mark).observe(document.documentElement, { childList: true, subtree: true });
  });
  const kept = (await page.context().cookies()).filter((cookie) => cookie.name !== 'WAEW_SESSION');
  await page.context().clearCookies();
  await page.context().addCookies(kept);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __loginSeen?: boolean }).__loginSeen === true)).toBe(false);

  await page.getByRole('link', { name: 'Mehr' }).click();
  await page.getByTestId('logout').click();
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible();
  expect((await page.context().cookies()).some((cookie) => cookie.name === 'WAEW_REMEMBER' && cookie.value)).toBe(false);
});

async function login(page: Page, remember: boolean) {
  await page.goto('/');
  await page.getByTestId('login-username').fill('Ada');
  await page.getByTestId('login-password').fill(adminPassword);
  if (remember) {
    await page.getByTestId('login-remember').check();
  }
  await page.getByTestId('login-submit').click();
  await expect(page.getByRole('heading', { name: 'Was essen wir heute?' })).toBeVisible();
}
