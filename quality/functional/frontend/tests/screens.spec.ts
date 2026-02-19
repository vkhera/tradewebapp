import { test, expect, Page } from '@playwright/test';

type ScreenCheck = {
  route: string;
  heading: string;
};

const clientScreens: ScreenCheck[] = [
  { route: '/portfolio', heading: 'My Portfolio' },
  { route: '/trade', heading: 'Execute Trade' },
  { route: '/order-history', heading: 'Order History' },
  { route: '/realized-gains', heading: 'Realized Gains/Losses' },
  { route: '/unrealized-gains', heading: 'Unrealized Gains/Losses' },
  { route: '/fund-account', heading: 'Fund Account' },
  { route: '/import-data', heading: 'Import Portfolio Data' }
];

const adminScreens: ScreenCheck[] = [
  { route: '/admin/clients', heading: 'Client Management' },
  { route: '/admin/rules', heading: 'Rule Management' }
];

async function bootstrapSession(page: Page, role: 'CLIENT' | 'ADMIN') {
  await page.addInitScript((assignedRole) => {
    localStorage.setItem('currentUser', JSON.stringify({
      username: assignedRole === 'ADMIN' ? 'admin1' : 'client1',
      password: 'pass1234',
      role: assignedRole,
      clientId: assignedRole === 'ADMIN' ? null : 1
    }));
    localStorage.setItem('role', assignedRole);
    localStorage.setItem('clientId', assignedRole === 'ADMIN' ? '' : '1');
  }, role);
}

test.describe('Frontend screen coverage', () => {
  test('login page renders and key controls are available', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Stock Brokerage Login' })).toBeVisible();
    await expect(page.getByLabel('Username')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
  });

  test('unauthenticated access redirects to login', async ({ page }) => {
    await page.goto('/portfolio');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'Stock Brokerage Login' })).toBeVisible();
  });

  test('unknown route redirects to login', async ({ page }) => {
    await page.goto('/some-nonexistent-page');
    await expect(page).toHaveURL(/\/login/);
  });

  for (const screen of clientScreens) {
    test(`client screen renders: ${screen.route}`, async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto(screen.route);
      await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
      await expect(page.getByText('Stock Brokerage')).toBeVisible();
    });
  }

  for (const screen of adminScreens) {
    test(`admin screen renders: ${screen.route}`, async ({ page }) => {
      await bootstrapSession(page, 'ADMIN');
      await page.goto(screen.route);
      await expect(page.getByRole('heading', { name: screen.heading })).toBeVisible();
      await expect(page.getByText('Stock Brokerage')).toBeVisible();
    });
  }

  test('client navigation exposes major flows', async ({ page }) => {
    await bootstrapSession(page, 'CLIENT');
    await page.goto('/portfolio');

    await expect(page.getByRole('link', { name: /Portfolio/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Trade/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Order History/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Fund Account/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Import Data/i })).toBeVisible();
  });

  test('admin navigation exposes major flows', async ({ page }) => {
    await bootstrapSession(page, 'ADMIN');
    await page.goto('/admin/clients');

    await expect(page.getByRole('link', { name: /Manage Clients/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Manage Rules/i })).toBeVisible();
  });

  test.describe('Portfolio Predictions button', () => {
    test('Predictions button is visible for each holding row', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      // Wait for portfolio table to load (cash summary is the first indicator)
      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 15000 });

      // At least one Predictions button must be present
      const predButtons = page.getByRole('button', { name: /Predictions/i });
      await expect(predButtons.first()).toBeVisible({ timeout: 10000 });

      const count = await predButtons.count();
      console.log(`Found ${count} Predictions button(s)`);
      expect(count).toBeGreaterThan(0);
    });

    test('Predictions button click opens popup with data', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      // Wait for portfolio table
      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 15000 });

      const firstBtn = page.getByRole('button', { name: /Predictions/i }).first();
      await expect(firstBtn).toBeVisible({ timeout: 10000 });

      // Capture what symbol we clicked
      const symbolCell = page.locator('td.symbol-cell').first();
      const symbol = await symbolCell.locator('.symbol-ticker').textContent();
      console.log(`Clicking Predictions for: ${symbol}`);

      // Button should start in default state (not green)
      await expect(firstBtn).not.toHaveClass(/pred-btn--open/);

      // Click the button
      await firstBtn.click();

      // Button turns amber (loading) then green (loaded)
      // Wait for popup to appear
      const popup = page.locator('.pred-popup-overlay');
      await expect(popup).toBeVisible({ timeout: 20000 });

      // Popup header should contain the symbol and "Price Forecasts"
      await expect(popup.locator('.tooltip-title')).toContainText('Price Forecasts');

      // Wait for table or no-data message (API response)
      const predTable = popup.locator('.pred-table');
      const noData    = popup.locator('.tooltip-no-data');
      await expect(predTable.or(noData)).toBeVisible({ timeout: 20000 });

      if (await predTable.isVisible()) {
        const rows = predTable.locator('tbody tr');
        const rowCount = await rows.count();
        console.log(`Prediction table has ${rowCount} rows`);
        expect(rowCount).toBeGreaterThan(0);

        // First row should have a non-empty price cell (text contains $ and digits)
        const firstPriceCell = rows.first().locator('.pred-price');
        await expect(firstPriceCell).toBeVisible();
        const priceText = (await firstPriceCell.textContent() || '').trim();
        console.log(`First predicted price cell text: "${priceText}"`);
        expect(priceText).toMatch(/^\$[\d,]+\.\d{2}$/);  // e.g. "$262.38"
      }

      // Button should now be green (open state)
      await expect(firstBtn).toHaveClass(/pred-btn--open/);

      // Close with X button
      await popup.locator('.popup-close').click();
      await expect(popup).not.toBeVisible({ timeout: 3000 });

      // Button reverts to default (not green)
      await expect(firstBtn).not.toHaveClass(/pred-btn--open/);
    });

    test('Predictions popup closes on outside click', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 15000 });
      const firstBtn = page.getByRole('button', { name: /Predictions/i }).first();
      await firstBtn.click();

      const popup = page.locator('.pred-popup-overlay');
      await expect(popup).toBeVisible({ timeout: 20000 });

      // Click outside the popup (on the heading)
      await page.getByRole('heading', { name: 'My Portfolio' }).click();
      await expect(popup).not.toBeVisible({ timeout: 3000 });
    });
  });
});

