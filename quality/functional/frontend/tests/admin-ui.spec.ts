/**
 * Admin user – end-to-end UI tests
 *
 * Exercises admin-only functionality in the following ordered sequence:
 *   1.  Real login via the login form
 *   2.  Admin navigation – admin-only links are visible; client routes are blocked
 *   3.  Client Management page – table data validation
 *   4.  Import Data page – Clean Up client5 data (UI flow)
 *   5.  Validate cleanup via API (portfolio must be empty)
 *   6.  Import Data page – Import Activity for client5 (UI flow)
 *   7.  Validate portfolio via API poll (must be populated after import)
 *   8.  Admin Client Holdings page – newly-imported client5 holdings are present
 *       and the table data is correct
 *
 * Run against the live Docker stack:
 *   npm run frontend:test:docker --prefix quality
 *
 * Or via the combined docker suite:
 *   npm run test:docker --prefix quality
 *
 * IMPORTANT: Tests within each describe block run sequentially (workers: 1,
 * fullyParallel: false).  Sections 4–8 build on each other intentionally –
 * cleanup runs first, import second, then the holdings page verifies the result.
 */

import { test, expect, Page } from '@playwright/test';

// ── Constants ─────────────────────────────────────────────────────────────────

const CLIENT5_ID  = 5;
const CLIENT5_B64 = Buffer.from('client5:pass1234').toString('base64');
const ADMIN_B64   = Buffer.from('admin1:pass1234').toString('base64');
const ACTIVITY_CSV = 'GeneratedActivity-IRA94178.csv';

// ── Auth helpers ──────────────────────────────────────────────────────────────

/**
 * Bootstrap an admin session into localStorage before Angular boots.
 * This mirrors the approach used in screens.spec.ts – no real HTTP login
 * required for tests that only need the session injected.
 */
async function bootstrapAdmin(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('currentUser', JSON.stringify({
      username: 'admin1',
      password: 'pass1234',
      role: 'ADMIN',
      clientId: null
    }));
    localStorage.setItem('role', 'ADMIN');
    localStorage.setItem('clientId', '');
  });
}

// ── Suite ─────────────────────────────────────────────────────────────────────

test.describe('Admin user – functional UI tests', () => {

  // ── 1. Real admin login ─────────────────────────────────────────────────────

  test.describe('1. Real admin login via the login form', () => {

    test('login page renders expected controls', async ({ page }) => {
      await page.goto('/login');
      await expect(page.getByRole('heading', { name: 'Stock Brokerage Login' })).toBeVisible();
      await expect(page.getByLabel('Username')).toBeVisible();
      await expect(page.getByLabel('Password')).toBeVisible();
      await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
    });

    test('admin logs in with valid credentials and lands on Client Management', async ({ page }) => {
      await page.goto('/login');
      await page.getByLabel('Username').fill('admin1');
      await page.getByLabel('Password').fill('pass1234');
      await page.getByRole('button', { name: 'Login' }).click();

      // Admin should be redirected to /admin/clients after a successful login
      await expect(page).toHaveURL(/\/admin\/clients/, { timeout: 15_000 });
      await expect(page.getByRole('heading', { name: 'Client Management' })).toBeVisible();
    });

    test('invalid credentials keep the user on the login page with an error', async ({ page }) => {
      await page.goto('/login');
      await page.getByLabel('Username').fill('admin1');
      await page.getByLabel('Password').fill('wrongpassword');
      await page.getByRole('button', { name: 'Login' }).click();

      await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
      // An error/alert element must be visible after a failed login attempt
      const errorEl = page.locator('[class*="error"], [class*="alert"], .error-message, .login-error');
      await expect(errorEl.first()).toBeVisible({ timeout: 8_000 });
    });

  });

  // ── 2. Admin navigation ─────────────────────────────────────────────────────

  test.describe('2. Admin navigation and screen availability', () => {

    test('admin nav bar shows Manage Clients, Manage Rules, and Client Holdings links', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.getByRole('link', { name: /Manage Clients/i })).toBeVisible();
      await expect(page.getByRole('link', { name: /Manage Rules/i })).toBeVisible();
      await expect(page.getByRole('link', { name: /Client Holdings/i })).toBeVisible();
    });

    test('admin can navigate to /admin/clients – Client Management heading visible', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.getByRole('heading', { name: 'Client Management' })).toBeVisible();
    });

    test('admin can navigate to /admin/rules – Rule Management heading visible', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/rules');
      await expect(page.getByRole('heading', { name: 'Rule Management' })).toBeVisible();
    });

    test('admin can navigate to /admin/holdings – Client Holdings heading visible', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.getByRole('heading', { name: 'Client Holdings' })).toBeVisible();
    });

    test('non-admin (client5) is redirected away from /admin/clients', async ({ page }) => {
      await page.addInitScript(() => {
        localStorage.setItem('currentUser', JSON.stringify({
          username: 'client5',
          password: 'pass1234',
          role: 'CLIENT',
          clientId: 5
        }));
        localStorage.setItem('role', 'CLIENT');
        localStorage.setItem('clientId', '5');
      });
      await page.goto('/admin/clients');
      // adminGuard redirects non-admins to /portfolio (not /login)
      await expect(page).toHaveURL(/\/(portfolio|login)/, { timeout: 10_000 });
    });

  });

  // ── 3. Client Management page – data validation ─────────────────────────────

  test.describe('3. Client Management page – data validation', () => {

    test('clients table loads and contains at least one row', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.getByRole('heading', { name: 'Client Management' })).toBeVisible();
      const rows = page.locator('table tbody tr');
      await expect(rows.first()).toBeVisible({ timeout: 15_000 });
      const rowCount = await rows.count();
      expect(rowCount).toBeGreaterThan(0);
      console.log(`[clients-table] ${rowCount} client rows loaded`);
    });

    test('clients table has the required column headers', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
      const headers = page.locator('table thead th');
      await expect(headers.nth(0)).toHaveText('ID');
      await expect(headers.nth(1)).toHaveText('Code');
      await expect(headers.nth(2)).toHaveText('Name');
      await expect(headers.nth(3)).toHaveText('Email');
      await expect(headers.nth(4)).toHaveText('Balance');
      await expect(headers.nth(5)).toHaveText('Status');
      await expect(headers.nth(6)).toHaveText('Risk Level');
    });

    test('client5 appears in the clients table', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
      // "client5" appears as the client code in the second column
      await expect(page.locator('table tbody').getByText('client5')).toBeVisible();
    });

    test('every client row has a numeric ID, non-blank Code, and non-blank Name', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });
      const rows = page.locator('table tbody tr');
      const rowCount = await rows.count();
      for (let i = 0; i < rowCount; i++) {
        const cells = rows.nth(i).locator('td');
        const id   = ((await cells.nth(0).textContent()) ?? '').trim();
        const code = ((await cells.nth(1).textContent()) ?? '').trim();
        const name = ((await cells.nth(2).textContent()) ?? '').trim();
        expect(id,   `Row ${i + 1}: ID should be numeric`).toMatch(/^\d+$/);
        expect(code, `Row ${i + 1}: Code is blank`).toMatch(/\S+/);
        expect(name, `Row ${i + 1}: Name is blank`).toMatch(/\S+/);
      }
      console.log(`[clients-table] All ${rowCount} rows have valid ID / Code / Name ✓`);
    });

    test('"Add New Client" button toggles the client form open and closed', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/clients');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 15_000 });

      const addBtn = page.getByRole('button', { name: /Add New Client/i });
      await expect(addBtn).toBeVisible();

      // Open the form – the toggle button (btn-primary) switches text to 'Cancel'
      await addBtn.click();
      await expect(page.locator('.client-form')).toBeVisible();
      // The toggle button is the btn-primary Cancel; the form itself also has a Cancel btn
      const toggleCancelBtn = page.locator('button.btn-primary', { hasText: 'Cancel' });
      await expect(toggleCancelBtn).toBeVisible();

      // Close the form using the toggle button
      await toggleCancelBtn.click();
      await expect(page.locator('.client-form')).not.toBeVisible();
    });

  });

  // ── 4. Import Data – Clean Up client5 (UI flow) ─────────────────────────────

  test.describe('4. Import Data – Clean Up client5 data via UI', () => {

    test('Cleanup Client Data section is visible and has a Client ID input for admin', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/import-data');
      await expect(page.getByRole('heading', { name: 'Import Portfolio Data' })).toBeVisible();
      const cleanupSection = page.locator('.cleanup-section');
      await expect(cleanupSection).toBeVisible();
      await expect(cleanupSection.getByRole('heading', { name: /Cleanup Client Data/i })).toBeVisible();
      // Admin must see an editable Client ID input (not visible for non-admin)
      await expect(cleanupSection.locator('input[type="number"]')).toBeVisible();
      await expect(cleanupSection.getByRole('button', { name: /Cleanup Client Data/i })).toBeVisible();
    });

    test('admin performs cleanup of client5 and sees a success result box', async ({ page }) => {
      test.setTimeout(60_000);
      await bootstrapAdmin(page);
      await page.goto('/import-data');
      await expect(page.getByRole('heading', { name: 'Import Portfolio Data' })).toBeVisible();

      const cleanupSection = page.locator('.cleanup-section');

      // Clear existing value and type the client5 ID
      const clientIdInput = cleanupSection.locator('input[type="number"]');
      await clientIdInput.clear();
      await clientIdInput.fill(String(CLIENT5_ID));

      // Auto-accept the browser confirm dialog that warns about data deletion
      page.on('dialog', dialog => {
        console.log(`[cleanup] Confirm dialog: "${dialog.message()}" → accepting`);
        dialog.accept();
      });

      // Trigger the cleanup
      await cleanupSection.getByRole('button', { name: /Cleanup Client Data/i }).click();

      // The result box must appear with a success CSS class
      const resultBox = cleanupSection.locator('.result-box');
      await expect(resultBox).toBeVisible({ timeout: 30_000 });
      await expect(resultBox).toHaveClass(/success/);
      const resultText = (await resultBox.textContent() ?? '').trim();
      console.log(`[cleanup] Result: ${resultText}`);
    });

  });

  // ── 5. Validate cleanup via API ─────────────────────────────────────────────

  test.describe('5. Validate cleanup – client5 portfolio must be empty', () => {

    test('API confirms client5 has zero holdings immediately after cleanup', async ({ request }) => {
      test.setTimeout(30_000);
      const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
        headers: { Authorization: `Basic ${CLIENT5_B64}` },
        timeout: 20_000
      });
      expect(resp.ok(), `Portfolio API returned ${resp.status()} – is the backend running?`).toBeTruthy();
      const body = await resp.json();
      const count = ((body.holdings ?? []) as unknown[]).length;
      console.log(`[cleanup-verify] client5 holdings after cleanup: ${count}`);
      expect(count, 'Portfolio should be empty (0 holdings) after cleanup').toBe(0);
    });

  });

  // ── 6. Import Data – Add Activity for client5 (UI flow) ─────────────────────

  test.describe('6. Import Data – Import Activity for client5 via UI', () => {

    test('admin fills in the Activity import form and import completes successfully', async ({ page }) => {
      test.setTimeout(60_000);
      await bootstrapAdmin(page);
      await page.goto('/import-data');
      await expect(page.getByRole('heading', { name: 'Import Portfolio Data' })).toBeVisible();

      // The Import Activity section is an .import-section that contains the "Import Activity" heading
      const activitySection = page.locator('.import-section').filter({
        has: page.locator('h3', { hasText: 'Import Activity' })
      });
      await expect(activitySection).toBeVisible();

      // Set Client ID to 5 (admin can edit this field)
      const clientIdInput = activitySection.locator('input[type="number"]');
      await clientIdInput.fill(String(CLIENT5_ID));

      // Enter the CSV file name manually in the text input
      const fileNameInput = activitySection.locator('input[type="text"]');
      await fileNameInput.fill(ACTIVITY_CSV);

      // Click the Import Activity button
      await activitySection.getByRole('button', { name: /^Import Activity$/i }).click();

      // Wait for the result box – import can take a few seconds
      const resultBox = activitySection.locator('.result-box');
      await expect(resultBox).toBeVisible({ timeout: 45_000 });
      await expect(resultBox).toHaveClass(/success/);

      const resultText = (await resultBox.textContent() ?? '').trim();
      console.log(`[activity-import] Result: ${resultText}`);
    });

    test('activity import result box shows recordsImported > 0', async ({ page }) => {
      test.setTimeout(60_000);
      await bootstrapAdmin(page);
      await page.goto('/import-data');
      await expect(page.getByRole('heading', { name: 'Import Portfolio Data' })).toBeVisible();

      const activitySection = page.locator('.import-section').filter({
        has: page.locator('h3', { hasText: 'Import Activity' })
      });
      await expect(activitySection).toBeVisible();

      const clientIdInput = activitySection.locator('input[type="number"]');
      await clientIdInput.fill(String(CLIENT5_ID));
      const fileNameInput = activitySection.locator('input[type="text"]');
      await fileNameInput.fill(ACTIVITY_CSV);

      await activitySection.getByRole('button', { name: /^Import Activity$/i }).click();

      const resultBox = activitySection.locator('.result-box');
      await expect(resultBox).toBeVisible({ timeout: 45_000 });

      // "Imported: N" in the result box must show a positive number
      const importedText = (await resultBox.textContent() ?? '').trim();
      console.log(`[activity-import] Full result: ${importedText}`);

      // The text contains "Imported: N" – parse that value
      const importedMatch = importedText.match(/Imported:\s*(\d+)/i);
      if (importedMatch) {
        const importedCount = parseInt(importedMatch[1], 10);
        console.log(`[activity-import] Records imported: ${importedCount}`);
        expect(importedCount, 'recordsImported should be > 0').toBeGreaterThan(0);
      }
    });

  });

  // ── 7. Validate portfolio populated via API poll ─────────────────────────────

  test.describe('7. Validate portfolio – must be populated after activity import', () => {

    test('client5 has holdings in the portfolio after import (polls ReconciliationService)', async ({ request }) => {
      test.setTimeout(120_000);
      let holdingCount = 0;
      const deadline = Date.now() + 100_000;
      while (holdingCount === 0 && Date.now() < deadline) {
        const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
          headers: { Authorization: `Basic ${CLIENT5_B64}` },
          timeout: 20_000
        });
        if (resp.ok()) {
          const body = await resp.json().catch(() => ({ holdings: [] }));
          holdingCount = ((body.holdings ?? []) as unknown[]).length;
        }
        if (holdingCount === 0) {
          console.log('[portfolio-verify] Portfolio still empty – waiting 5 s for reconciliation…');
          await new Promise(r => setTimeout(r, 5_000));
        }
      }
      console.log(`[portfolio-verify] client5 has ${holdingCount} holdings after import`);
      expect(holdingCount, 'Portfolio should be populated (> 0 holdings) after activity import').toBeGreaterThan(0);
    });

    test('portfolio API returns holdings with non-empty symbols and positive quantities', async ({ request }) => {
      const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
        headers: { Authorization: `Basic ${CLIENT5_B64}` },
        timeout: 20_000
      });
      expect(resp.ok()).toBeTruthy();
      const body = await resp.json();
      const holdings = (body.holdings ?? []) as any[];
      expect(holdings.length).toBeGreaterThan(0);
      for (const h of holdings) {
        expect(h.symbol, 'Holdings entry is missing a symbol').toBeTruthy();
        expect(parseInt(h.quantity, 10), `Symbol ${h.symbol} has quantity <= 0`).toBeGreaterThan(0);
      }
      console.log(`[portfolio-verify] ${holdings.length} holdings verified ✓`);
    });

  });

  // ── 8. Admin Client Holdings page – verify client5 rows ─────────────────────

  test.describe('8. Admin Client Holdings page – client5 newly-imported holdings', () => {

    /**
     * Ground truth: holding count fetched from the API before UI tests start.
     * Shared across all tests in this describe block.
     */
    let expectedHoldingCount = 0;

    test.beforeAll(async ({ request }) => {
      test.setTimeout(30_000);
      const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
        headers: { Authorization: `Basic ${CLIENT5_B64}` },
        timeout: 20_000
      });
      expect(resp.ok(), `Portfolio API returned ${resp.status()}`).toBeTruthy();
      const body = await resp.json();
      expectedHoldingCount = ((body.holdings ?? []) as unknown[]).length;
      console.log(`[admin-holdings-setup] client5 API holding count: ${expectedHoldingCount}`);
      expect(expectedHoldingCount, 'Holdings must be > 0 before admin holdings UI tests').toBeGreaterThan(0);
    });

    test('/admin/holdings page loads and the holdings table renders at least one row', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.getByRole('heading', { name: 'Client Holdings' })).toBeVisible();
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });
    });

    test('holdings table has columns: Client ID, Client Name, Symbol, Quantity', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });
      const headers = page.locator('table thead th');
      await expect(headers.nth(0)).toHaveText('Client ID');
      await expect(headers.nth(1)).toHaveText('Client Name');
      await expect(headers.nth(2)).toHaveText('Symbol');
      await expect(headers.nth(3)).toHaveText('Quantity');
    });

    test('filtering by client5 ID shows the newly imported holdings (row count matches API)', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });

      // Use the "Filter by Client ID" numeric input
      const clientFilter = page.locator('input[placeholder*="All clients"]');
      await clientFilter.fill(String(CLIENT5_ID));
      await page.waitForTimeout(500);

      const rows = page.locator('table tbody tr');
      const rowCount = await rows.count();
      console.log(`[admin-holdings] Filtered rows for client ${CLIENT5_ID}: ${rowCount}, expected: ${expectedHoldingCount}`);
      expect(rowCount, 'Filtered row count should match the portfolio API holding count').toBe(expectedHoldingCount);
    });

    test('every client5 row has the correct Client ID, non-empty symbol, and positive quantity', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });

      // Filter to client5 only
      const clientFilter = page.locator('input[placeholder*="All clients"]');
      await clientFilter.fill(String(CLIENT5_ID));
      await page.waitForTimeout(500);

      const rows = page.locator('table tbody tr');
      const rowCount = await rows.count();
      expect(rowCount, `Expected ${expectedHoldingCount} rows for client5`).toBeGreaterThan(0);

      for (let i = 0; i < rowCount; i++) {
        const cells = rows.nth(i).locator('td');

        // Column 0: Client ID must equal CLIENT5_ID
        const clientIdText = ((await cells.nth(0).textContent()) ?? '').trim();
        expect(clientIdText, `Row ${i + 1}: Client ID should be ${CLIENT5_ID}`).toBe(String(CLIENT5_ID));

        // Column 1: Client Name must be non-empty
        const clientName = ((await cells.nth(1).textContent()) ?? '').trim();
        expect(clientName, `Row ${i + 1}: Client Name is blank`).toMatch(/\S+/);

        // Column 2: Symbol must be non-empty
        const symbol = ((await cells.nth(2).textContent()) ?? '').trim();
        expect(symbol, `Row ${i + 1}: Symbol is blank`).toMatch(/\S+/);

        // Column 3: Quantity must be a positive integer
        const qtyText = ((await cells.nth(3).textContent()) ?? '').trim();
        expect(parseInt(qtyText, 10), `Row ${i + 1} [${symbol}]: Quantity must be > 0`).toBeGreaterThan(0);
      }
      console.log(`[admin-holdings] All ${rowCount} client5 rows verified ✓`);
    });

    test('summary bar reports correct row count and exactly 1 client when filtered to client5', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });

      const clientFilter = page.locator('input[placeholder*="All clients"]');
      await clientFilter.fill(String(CLIENT5_ID));
      await page.waitForTimeout(500);

      const summaryText = page.locator('.summary-text');
      await expect(summaryText).toBeVisible();
      const text = (await summaryText.textContent() ?? '').trim();
      console.log(`[admin-holdings] Summary bar: "${text}"`);

      expect(text, 'Summary should report exactly 1 client').toContain('1 client(s)');
      expect(text, `Summary should report ${expectedHoldingCount} row(s)`).toContain(`${expectedHoldingCount} row(s)`);
    });

    test('symbol filter narrows client5 rows to matching symbols only', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });

      // Apply client5 filter first
      const clientFilter = page.locator('input[placeholder*="All clients"]');
      await clientFilter.fill(String(CLIENT5_ID));
      await page.waitForTimeout(300);

      const client5RowCount = await page.locator('table tbody tr').count();
      expect(client5RowCount).toBeGreaterThan(0);

      // Read the first symbol from the filtered results
      const firstSymbol = ((await page.locator('table tbody tr').first().locator('td').nth(2).textContent()) ?? '').trim();
      console.log(`[admin-holdings] Symbol filter test using prefix of: "${firstSymbol}"`);

      // Apply a partial symbol filter (first 2 chars)
      const symbolFilter = page.locator('input[placeholder*="e.g. AAPL"]');
      const prefix = firstSymbol.slice(0, 2);
      await symbolFilter.fill(prefix);
      await page.waitForTimeout(300);

      const filteredRows = page.locator('table tbody tr');
      const filteredCount = await filteredRows.count();
      expect(filteredCount, 'Symbol filter should reduce or maintain row count').toBeLessThanOrEqual(client5RowCount);
      expect(filteredCount, 'Symbol filter should match at least the first symbol').toBeGreaterThan(0);

      // Every visible row's symbol must contain the prefix (case-insensitive)
      const prefixLower = prefix.toLowerCase();
      for (let i = 0; i < filteredCount; i++) {
        const sym = ((await filteredRows.nth(i).locator('td').nth(2).textContent()) ?? '').trim().toLowerCase();
        expect(sym, `Row ${i + 1}: symbol "${sym}" does not match prefix "${prefix}"`).toContain(prefixLower);
      }
      console.log(`[admin-holdings] Symbol prefix filter "${prefix}" → ${filteredCount} row(s) ✓`);
    });

    test('"Clear Filters" button restores the full unfiltered holdings table', async ({ page }) => {
      await bootstrapAdmin(page);
      await page.goto('/admin/holdings');
      await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 20_000 });

      const totalRows = await page.locator('table tbody tr').count();
      console.log(`[admin-holdings] Total rows (unfiltered): ${totalRows}`);

      // Apply client5 filter
      const clientFilter = page.locator('input[placeholder*="All clients"]');
      await clientFilter.fill(String(CLIENT5_ID));
      await page.waitForTimeout(300);
      const filteredCount = await page.locator('table tbody tr').count();
      expect(filteredCount).toBeLessThanOrEqual(totalRows);

      // Clear filters and verify all rows are restored
      await page.getByRole('button', { name: /Clear Filters/i }).click();
      await page.waitForTimeout(300);
      const restoredCount = await page.locator('table tbody tr').count();
      expect(restoredCount, 'All rows should be restored after clearing filters').toBe(totalRows);
      console.log(`[admin-holdings] Restored to ${restoredCount} rows after clear ✓`);
    });

  }); // end describe 8

}); // end describe 'Admin user – functional UI tests'
