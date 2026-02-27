/**
 * Portfolio page data-integrity tests – client1
 *
 * Run against the live Docker stack:
 *   npm run frontend:test:docker --prefix quality
 *
 * Or via the combined docker suite:
 *   npm run test:docker --prefix quality
 *
 * These tests verify:
 *  1. The number of rows rendered in the UI matches the count returned by the API
 *     (which in turn reflects what is stored in the database).
 *  2. Every rendered row is fully populated – no blank symbol, quantity or price cells.
 *  3. The filter bar correctly narrows and resets the displayed rows.
 */

import { test, expect, Page, APIRequestContext } from '@playwright/test';

// ── Auth helpers ─────────────────────────────────────────────────────────────

const CLIENT1_ID   = 1;
const CLIENT1_B64  = Buffer.from('client1:pass1234').toString('base64');

/** Inject a client1 session into localStorage before Angular boots. */
async function bootstrapClient1(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('currentUser', JSON.stringify({
      username: 'client1',
      password: 'pass1234',
      role: 'CLIENT',
      clientId: 1
    }));
    localStorage.setItem('role', 'CLIENT');
    localStorage.setItem('clientId', '1');
  });
}

/**
 * Calls the portfolio summary API directly and returns the number of holdings
 * that the backend (and therefore the database) reports for client1.
 */
async function fetchApiHoldingCount(request: APIRequestContext): Promise<number> {
  const resp = await request.get(`/api/portfolio/client/${CLIENT1_ID}/summary`, {
    headers: { Authorization: `Basic ${CLIENT1_B64}` },
    timeout: 20_000
  });
  expect(resp.ok(), `API returned ${resp.status()} – is the backend running?`).toBeTruthy();
  const body = await resp.json();
  return (body.holdings as unknown[]).length;
}

// ── Shared locators ───────────────────────────────────────────────────────────

/** All data rows inside the portfolio table body (never includes the "no match" notice). */
const dataRows = (page: Page) =>
  page.locator('table.portfolio-table tbody tr:has(td.symbol-cell)');

/** Wait until the portfolio table has finished loading (cash summary is the sentinel). */
async function waitForPortfolioTable(page: Page) {
  await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 20_000 });
  await expect(dataRows(page).first()).toBeVisible({ timeout: 20_000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('Portfolio page – data integrity (client1)', () => {

  // ── 1. Row count matches database ─────────────────────────────────────────

  test('UI row count matches API / database holding count', async ({ page, request }) => {
    // Ask the backend first (before the page loads) so we have the ground truth.
    const dbCount = await fetchApiHoldingCount(request);
    console.log(`[API] client1 has ${dbCount} holdings in the database`);
    expect(dbCount, 'Database reports zero holdings – import may not have run').toBeGreaterThan(0);

    await bootstrapClient1(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    // Allow the *ngFor to finish rendering (reconciliation can add rows mid-load).
    // We poll until the count stabilises rather than using a fixed sleep.
    let stableCount = 0;
    await expect(async () => {
      stableCount = await dataRows(page).count();
      expect(stableCount).toBe(dbCount);
    }).toPass({ timeout: 15_000, intervals: [1_000, 2_000, 3_000] });

    console.log(`[UI]  ${stableCount} rows rendered – matches database ✓`);
  });

  // ── 2. No empty rows ──────────────────────────────────────────────────────

  test('no row is empty – symbol, quantity, avg price and current price are all populated', async ({ page }) => {
    await bootstrapClient1(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    const rows = dataRows(page);
    const rowCount = await rows.count();
    expect(rowCount, 'No data rows found in portfolio table').toBeGreaterThan(0);
    console.log(`[UI]  Checking ${rowCount} rows for empty cells`);

    for (let i = 0; i < rowCount; i++) {
      const row  = rows.nth(i);
      const cells = row.locator('td');

      // ── Symbol (1st td – contains .symbol-ticker) ──
      const symbolTicker = row.locator('.symbol-ticker');
      const symbol = ((await symbolTicker.textContent()) ?? '').trim();
      expect(symbol, `Row ${i + 1}: symbol ticker is blank`).toMatch(/\S+/);

      // ── Quantity (3rd td) ──
      const qtyText = ((await cells.nth(2).textContent()) ?? '').trim();
      expect(qtyText,  `Row ${i + 1} [${symbol}]: quantity cell is blank`)    .toMatch(/^\d+$/);
      expect(parseInt(qtyText, 10), `Row ${i + 1} [${symbol}]: quantity is 0`).toBeGreaterThan(0);

      // ── Average Price (4th td) – must be a dollar amount ──
      const avgText = ((await cells.nth(3).textContent()) ?? '').trim();
      expect(avgText, `Row ${i + 1} [${symbol}]: avg price is blank or malformed`)
        .toMatch(/^\$[\d,]+\.\d{2}$/);

      // ── Current Price (5th td) – may be $0.00 for illiquid symbols, but must be present ──
      const curText = ((await cells.nth(4).textContent()) ?? '').trim();
      expect(curText, `Row ${i + 1} [${symbol}]: current price cell is blank or malformed`)
        .toMatch(/^\$[\d,]+\.\d{2}$/);

      // ── Total Value (6th td) ──
      const totalText = ((await cells.nth(5).textContent()) ?? '').trim();
      expect(totalText, `Row ${i + 1} [${symbol}]: total value cell is blank or malformed`)
        .toMatch(/^\$[\d,]+\.\d{2}$/);
    }

    console.log(`[UI]  All ${rowCount} rows are fully populated ✓`);
  });

  // ── 3. Row count is stable through a ReconciliationService cycle ──────────

  test('row count remains stable after 70 s reconciliation cycle', async ({ page, request }) => {
    // This test intentionally outlasts the default 60 s timeout.
    test.setTimeout(150_000);

    const dbCount = await fetchApiHoldingCount(request);
    console.log(`[API] ${dbCount} holdings before reconciliation wait`);

    await bootstrapClient1(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    const before = await dataRows(page).count();
    console.log(`[UI]  ${before} rows rendered initially`);
    expect(before).toBe(dbCount);

    // ReconciliationService runs every 60 s – wait 70 s then reload and re-check.
    console.log('[Test] Waiting 70 s for reconciliation cycle…');
    await page.waitForTimeout(70_000);

    await page.reload();
    await waitForPortfolioTable(page);

    const after = await dataRows(page).count();
    console.log(`[UI]  ${after} rows after reconciliation`);
    expect(after, 'Holdings were wiped by ReconciliationService – import trades may be missing')
      .toBe(dbCount);
  });

  // ── 4. Filter bar works correctly ─────────────────────────────────────────

  test('filter bar is visible and reduces / restores displayed rows', async ({ page }) => {
    await bootstrapClient1(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    const rows       = dataRows(page);
    const totalRows  = await rows.count();

    // Filter bar elements must be present
    const filterInput = page.locator('input.filter-input');
    const countBadge  = page.locator('.filter-count');
    const clearBtn    = page.locator('.filter-clear');

    await expect(filterInput).toBeVisible();
    await expect(countBadge).toContainText(`${totalRows} / ${totalRows}`);
    await expect(clearBtn).not.toBeVisible(); // no filter yet → no clear button

    // Type a prefix that exists in client1's portfolio ('NV' matches NVDA at minimum)
    await filterInput.fill('NV');
    await expect(countBadge).not.toContainText(`${totalRows} / ${totalRows}`, { timeout: 3_000 });

    const filteredRows = await rows.count();
    console.log(`[UI]  Rows after filter "NV": ${filteredRows} of ${totalRows}`);
    expect(filteredRows, 'Filter reduced to 0 rows – check if NVDA is in client1 portfolio').toBeGreaterThan(0);
    expect(filteredRows).toBeLessThan(totalRows);
    await expect(countBadge).toContainText(`${filteredRows} / ${totalRows}`);

    // Every visible row must match the filter
    for (let i = 0; i < filteredRows; i++) {
      const symbol = ((await rows.nth(i).locator('.symbol-ticker').textContent()) ?? '').trim();
      expect(symbol.toLowerCase(), `Row ${i + 1} symbol "${symbol}" does not match filter "NV"`)
        .toContain('nv');
    }

    // Clear button resets
    await expect(clearBtn).toBeVisible();
    await clearBtn.click();
    await expect(countBadge).toContainText(`${totalRows} / ${totalRows}`, { timeout: 3_000 });
    expect(await rows.count()).toBe(totalRows);
    await expect(clearBtn).not.toBeVisible();

    // Filter with a value that should not match any symbol
    await filterInput.fill('ZZZNOEXIST');
    await page.waitForTimeout(300);
    const noMatchMsg = page.locator('.no-match');
    await expect(noMatchMsg).toBeVisible({ timeout: 3_000 });
    await expect(noMatchMsg).toContainText('ZZZNOEXIST');
    await expect(countBadge).toContainText(`0 / ${totalRows}`);

    console.log('[UI]  Filter bar behaviour verified ✓');
  });

});
