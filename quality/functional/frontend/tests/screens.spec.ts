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
});
