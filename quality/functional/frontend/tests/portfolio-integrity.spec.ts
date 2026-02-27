/**
 * Portfolio page data-integrity tests – client5
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

const CLIENT5_ID   = 5;
const CLIENT5_B64  = Buffer.from('client5:pass1234').toString('base64');
const ADMIN_B64    = Buffer.from('admin1:pass1234').toString('base64');
const ACTIVITY_CSV = 'GeneratedActivity-IRA94178.csv';

/** Inject a client5 session into localStorage before Angular boots. */
async function bootstrapClient5(page: Page) {
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
}

/**
 * Calls the portfolio summary API directly and returns the number of holdings
 * that the backend (and therefore the database) reports for client5.
 */
async function fetchApiHoldingCount(request: APIRequestContext): Promise<number> {
  const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
    headers: { Authorization: `Basic ${CLIENT5_B64}` },
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
  // Allow up to 90 s – beforeAll warm-up should ensure the backend is already primed
  await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 90_000 });
  await expect(dataRows(page).first()).toBeVisible({ timeout: 90_000 });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('Portfolio page – data integrity (client5)', () => {

  // Set up client5 data ONCE before any test runs:
  //   1. Clean up any pre-existing holdings/trades for client5.
  //   2. Import activity from the generated CSV to populate the portfolio.
  //   3. Warm up the ATR cache so page tests don't timeout on first load.
  test.beforeAll(async ({ request }) => {
    test.setTimeout(200_000);

    // 1. Clean up – ensures a deterministic starting state
    console.log('[setup] Cleaning up client5 data…');
    const cleanResp = await request.delete('/api/import/cleanup', {
      headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
      data: { clientId: CLIENT5_ID }
    });
    console.log(`[setup] Cleanup responded with ${cleanResp.status()}`);

    // 2. Import activity CSV to populate client5's portfolio
    console.log(`[setup] Importing ${ACTIVITY_CSV} for client5…`);
    const importResp = await request.post('/api/import/activity', {
      headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
      data: { clientId: CLIENT5_ID, fileName: ACTIVITY_CSV }
    });
    const importBody = await importResp.json().catch(() => ({}));
    console.log(`[setup] Import responded with ${importResp.status()}:`, JSON.stringify(importBody));
    expect(importResp.ok(), `Activity import failed: ${importResp.status()}`).toBeTruthy();

    // 3. Poll until ReconciliationService (runs every 60 s) has rebuilt the portfolio.
    //    importActivity only creates Trade records; portfolio rows appear after the next cycle.
    console.log('[warmup] Waiting for ReconciliationService to populate client5 portfolio…');
    let holdingCount = 0;
    const pollDeadline = Date.now() + 90_000;
    while (holdingCount === 0 && Date.now() < pollDeadline) {
      const pollResp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
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
  });
  // ── 1. Row count matches database ─────────────────────────────────────────

  test('UI row count matches API / database holding count', async ({ page, request }) => {
    // Ask the backend first (before the page loads) so we have the ground truth.
    const dbCount = await fetchApiHoldingCount(request);
    console.log(`[API] client5 has ${dbCount} holdings in the database`);
    expect(dbCount, 'Database reports zero holdings – import may not have run').toBeGreaterThan(0);

    await bootstrapClient5(page);
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
    await bootstrapClient5(page);
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
    // This test intentionally outlasts the default 120 s timeout.
    // It waits 70 s for a reconciliation cycle, then reloads the portfolio.
    // If the price cache has expired during the wait, yield up to 180 s extra
    // for Yahoo Finance to re-fetch prices for all holdings.
    test.setTimeout(300_000);

    const dbCount = await fetchApiHoldingCount(request);
    console.log(`[API] ${dbCount} holdings before reconciliation wait`);

    await bootstrapClient5(page);
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
    await bootstrapClient5(page);
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

    // Type a prefix that exists in client5's portfolio ('NV' matches NVDA)
    await filterInput.fill('NV');
    await expect(countBadge).not.toContainText(`${totalRows} / ${totalRows}`, { timeout: 3_000 });

    const filteredRows = await rows.count();
    console.log(`[UI]  Rows after filter "NV": ${filteredRows} of ${totalRows}`);
    expect(filteredRows, 'Filter reduced to 0 rows – check if NVDA is in client5 portfolio').toBeGreaterThan(0);
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
    const noMatchRow = page.locator('td.no-match');
    await expect(noMatchRow).toBeVisible({ timeout: 3_000 });
    await expect(noMatchRow).toContainText('No holdings match');
    await expect(countBadge).toContainText(`0 / ${totalRows}`);

    console.log('[UI]  Filter bar behaviour verified ✓');
  });

});
