import { test, expect, Page } from '@playwright/test';

type ScreenCheck = {
  route: string;
  heading: string;
};

const clientScreens: ScreenCheck[] = [
  { route: '/portfolio',        heading: 'My Portfolio' },
  { route: '/trade',            heading: 'Execute Trade' },
  { route: '/order-history',    heading: 'Order History' },
  { route: '/realized-gains',   heading: 'Realized Gains/Losses' },
  { route: '/unrealized-gains', heading: 'Unrealized Gains/Losses' },
  { route: '/fund-account',     heading: 'Fund Account' },
  { route: '/import-data',      heading: 'Import Portfolio Data' },
  { route: '/suggested-trades', heading: 'Suggested Trades' }
];

const adminScreens: ScreenCheck[] = [
  { route: '/admin/clients', heading: 'Client Management' },
  { route: '/admin/rules', heading: 'Rule Management' }
];

async function bootstrapSession(page: Page, role: 'CLIENT' | 'ADMIN') {
  await page.addInitScript((assignedRole) => {
    localStorage.setItem('currentUser', JSON.stringify({
      username: assignedRole === 'ADMIN' ? 'admin1' : 'client5',
      password: 'pass1234',
      role: assignedRole,
      clientId: assignedRole === 'ADMIN' ? null : 5
    }));
    localStorage.setItem('role', assignedRole);
    localStorage.setItem('clientId', assignedRole === 'ADMIN' ? '' : '5');
  }, role);
}

test.describe('Frontend screen coverage', () => {
  test('admin can log in from login page and land on admin clients', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Username').fill('admin1');
    await page.getByLabel('Password').fill('pass1234');
    await page.getByRole('button', { name: 'Login' }).click();

    await expect(page).toHaveURL(/\/admin\/clients/);
    await expect(page.getByRole('heading', { name: 'Client Management' })).toBeVisible();
  });

  test('activity file upload flow sets filename and enables import action', async ({ page }) => {
    await bootstrapSession(page, 'CLIENT');
    await page.goto('/import-data');

    const activityUploadInput = page.locator('input[type="file"]').nth(1);
    await activityUploadInput.setInputFiles({
      name: 'activity-upload-test.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('Date,Action,Symbol,Quantity,Price\n2026-02-27,BUY,TQQQ,1,10.00\n')
    });

    await expect(page.locator('.drop-zone-filename').nth(1)).toContainText('.csv', { timeout: 15000 });
    await expect(page.getByRole('button', { name: 'Import Activity' })).toBeEnabled();
  });

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
    await expect(page.getByRole('link', { name: /^Trade$/i })).toBeVisible();
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

    // Warm up the backend ONCE before any portfolio page tests run.
    // On a cold start, ATR computation for the portfolio symbols can take several minutes.
    // Also ensures client5's portfolio is populated via the activity CSV import.
    test.beforeAll(async ({ request }) => {
      test.setTimeout(300_000);
      const CLIENT5_B64 = Buffer.from('client5:pass1234').toString('base64');
      const ADMIN_B64   = Buffer.from('admin1:pass1234').toString('base64');
      const ACTIVITY_CSV = 'GeneratedActivity-IRA94178.csv';

      // Clean + import to ensure client5 has portfolio data
      console.log('[setup] Cleaning up client5 data…');
      await request.delete('/api/import/cleanup', {
        headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
        data: { clientId: 5 }
      });
      console.log(`[setup] Importing ${ACTIVITY_CSV} for client5…`);
      const importResp = await request.post('/api/import/activity', {
        headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
        data: { clientId: 5, fileName: ACTIVITY_CSV }
      });
      console.log(`[setup] Import responded with ${importResp.status()}`);

      // Poll until ReconciliationService (runs every 60 s) has rebuilt the portfolio.
      // importActivity only creates Trade records; portfolio rows appear after the next cycle.
      console.log('[warmup] Waiting for ReconciliationService to populate client5 portfolio…');
      let holdingCount = 0;
      const pollDeadline = Date.now() + 90_000;
      while (holdingCount === 0 && Date.now() < pollDeadline) {
        const pollResp = await request.get('/api/portfolio/client/5/summary', {
          headers: { Authorization: `Basic ${CLIENT5_B64}` },
          timeout: 30_000
        });
        if (pollResp.ok()) {
          const body = await pollResp.json().catch(() => ({ holdings: [] }));
          holdingCount = ((body.holdings ?? []) as unknown[]).length;
        }
        if (holdingCount === 0) {
          console.log('[warmup] Portfolio still empty – waiting 5 s for reconciliation…');
          await new Promise(r => setTimeout(r, 5_000));
        }
      }
      console.log(`[warmup] Portfolio ready: ${holdingCount} holdings found`);

      // Prime the predictions API for the first symbol so the popup opens fast
      try {
        const predResp = await request.get('/api/predictions/TNA', {
          headers: { Authorization: `Basic ${CLIENT5_B64}` },
          timeout: 60_000
        });
        console.log(`[warmup] Predictions API for TNA responded with ${predResp.status()}`);
      } catch (e) {
        console.warn('[warmup] Predictions warmup skipped:', e);
      }
    });

    test('Predictions button is visible for each holding row', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      // Wait for portfolio table to load – allow extra time on a cold backend
      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

      // At least one Predictions button must be present
      const predButtons = page.getByRole('button', { name: /Predictions/i });
      await expect(predButtons.first()).toBeVisible({ timeout: 10000 });

      const count = await predButtons.count();
      console.log(`Found ${count} Predictions button(s)`);
      expect(count).toBeGreaterThan(0);
    });

    test('Predictions button click opens popup with data', async ({ page }) => {
      // Predictions computation can be slow on a cold backend – extend timeout
      test.setTimeout(150_000);
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      // Wait for portfolio table – allow extra time on a cold backend
      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

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

      // Wait for popup to appear – cold prediction API can take up to 90 s
      const popup = page.locator('.pred-popup-overlay');
      await expect(popup).toBeVisible({ timeout: 90_000 });

      // Popup header should contain the symbol and "Price Forecasts"
      await expect(popup.locator('.tooltip-title')).toContainText('Price Forecasts');

      // Wait for table or no-data message (API response)
      const predTable = popup.locator('.pred-table').first();
      const noData    = popup.locator('.tooltip-no-data');
      await expect(predTable.or(noData)).toBeVisible({ timeout: 20000 });

      if (await predTable.isVisible()) {
        const rows = predTable.locator('tbody tr');
        const rowCount = await rows.count();
        console.log(`Prediction table has ${rowCount} rows`);
        expect(rowCount).toBeGreaterThan(0);

        // Table must have Hour, Predicted, Actual columns
        const headers = predTable.locator('thead th');
        await expect(headers.nth(0)).toHaveText('Hour');
        await expect(headers.nth(1)).toContainText('Predicted');
        await expect(headers.nth(2)).toContainText('Actual');

        // First row predicted price cell must contain a dollar amount
        const firstPriceCell = rows.first().locator('.pred-price');
        await expect(firstPriceCell).toBeVisible();
        const priceText = (await firstPriceCell.textContent() || '').trim();
        console.log(`First predicted price cell text: "${priceText}"`);
        expect(priceText).toMatch(/^\$[\d,]+\.\d{2}$/);  // e.g. "$262.38"

        // Actual column cell must exist (price or dash placeholder)
        const firstActualCell = rows.first().locator('.pred-actual');
        await expect(firstActualCell).toBeVisible();
        console.log(`First actual cell: "${(await firstActualCell.textContent() || '').trim()}"`);

        // Previous business day section (may be absent early in operations)
        const prevSection = popup.locator('.prev-day-section');
        const prevVisible = await prevSection.isVisible();
        console.log(`Previous business day section visible: ${prevVisible}`);
        if (prevVisible) {
          await expect(popup.locator('.prev-day-title')).toContainText('Previous Business Day');
          const prevTable = prevSection.locator('.prev-day-table');
          await expect(prevTable).toBeVisible();
          const prevHeaders = prevTable.locator('thead th');
          await expect(prevHeaders.nth(0)).toHaveText('Hour');
          await expect(prevHeaders.nth(1)).toContainText('Predicted');
          await expect(prevHeaders.nth(2)).toContainText('Actual');
        }
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
      test.setTimeout(150_000);
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });
      const firstBtn = page.getByRole('button', { name: /Predictions/i }).first();
      await firstBtn.click();

      const popup = page.locator('.pred-popup-overlay');
      await expect(popup).toBeVisible({ timeout: 90_000 });
      await page.getByRole('heading', { name: 'My Portfolio' }).click();
      await expect(popup).not.toBeVisible({ timeout: 3000 });
    });
  });

  // ── ATR(14) column tests ─────────────────────────────────────────────────────────────────────────
  test.describe('Portfolio ATR(14) column', () => {

    // Warm up the backend before ATR tests – the 5-min price cache may have
    // expired since the Predictions warm-up if earlier tests took >5 minutes.
    test.beforeAll(async ({ request }) => {
      test.setTimeout(200_000);
      const CLIENT5_B64 = Buffer.from('client5:pass1234').toString('base64');
      console.log('[warmup-atr] Re-priming portfolio API before ATR tests …');
      const resp = await request.get('/api/portfolio/client/5/summary', {
        headers: { Authorization: `Basic ${CLIENT5_B64}` },
        timeout: 190_000
      });
      console.log(`[warmup-atr] Portfolio API responded with ${resp.status()}`);
    });

    test('ATR(14) column header is present in portfolio table', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

      // Header with title attribute (set in the template)
      const atrHeader = page.locator('th[title*="Average True Range"]');
      await expect(atrHeader).toBeVisible({ timeout: 10000 });
      await expect(atrHeader).toContainText('ATR(14)');
    });

    test('ATR(14) cell is rendered for each holding row', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

      // Wait for rows to appear – allow 30 s in case reconciliation service briefly clears holdings
      const rows = page.locator('tbody tr');
      await expect(rows.first()).toBeVisible({ timeout: 30_000 });

      const rowCount = await rows.count();
      expect(rowCount).toBeGreaterThan(0);

      // Every holding row must have at least one .atr-cell td (first ATR14 column)
      for (let i = 0; i < rowCount; i++) {
        const atrCell = rows.nth(i).locator('td.atr-cell').first();
        await expect(atrCell).toBeVisible();
      }
    });

    test('ATR(14) cell shows dollar value or dash placeholder', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

      const rows = page.locator('tbody tr');
      await expect(rows.first()).toBeVisible({ timeout: 30_000 });

      // Use the FIRST atr-cell (ATR14 column) of the first row
      const firstAtrCell = rows.first().locator('td.atr-cell').first();
      const cellText = (await firstAtrCell.textContent() || '').trim();

      console.log(`First ATR cell text: "${cellText}"`);

      // Either a dollar amount like "$1.23 (0.5%)" or the placeholder "–"
      const hasValue = /^\$[\d,]+\.\d{2}/.test(cellText);
      const hasDash  = cellText === '–';
      expect(hasValue || hasDash).toBe(true);
    });

    test('ATR(14) value cell carries a colour class when value is present', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });

      const rows = page.locator('tbody tr');
      await expect(rows.first()).toBeVisible({ timeout: 30_000 });
      const rowCount = await rows.count();
      for (let i = 0; i < rowCount; i++) {
        const atrValue = rows.nth(i).locator('td.atr-cell').first().locator('.atr-value');
        if (await atrValue.isVisible()) {
          // Must carry exactly one of the three colour classes
          const cls = await atrValue.getAttribute('class') || '';
          const hasColorClass = cls.includes('atr-high') || cls.includes('atr-mid') || cls.includes('atr-low');
          console.log(`ATR cell class="${cls}"`);
          expect(hasColorClass).toBe(true);

          // The percentage sub-label must also be visible in the ATR14 cell
          await expect(rows.nth(i).locator('td.atr-cell').first().locator('.atr-pct')).toBeVisible();
          break;
        }
      }
    });

    test('ATR(14) CSV download includes ATR columns', async ({ page }) => {
      await bootstrapSession(page, 'CLIENT');
      await page.goto('/portfolio');

      await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 60000 });
      await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 30_000 });

      // Intercept the download
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        page.getByRole('button', { name: /Download CSV/i }).click()
      ]);

      // Read via Node.js fs after saving to a temp path
      const tmpPath = await download.path();
      const fs = await import('fs');
      const csvText = fs.readFileSync(tmpPath!, 'utf-8');

      console.log(`CSV first line: ${csvText.split('\n')[0]}`);
      expect(csvText).toContain('ATR(14)');
      expect(csvText).toContain('ATR%');
    });
  });
});

