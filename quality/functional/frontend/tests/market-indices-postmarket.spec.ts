/**
 * Market indices bar + post-market price column – UI & API tests
 *
 * Verifies that:
 *  1. The market-status pill renders on the portfolio page with a known status.
 *  2. The index quotes bar renders with exactly 5 tiles, each containing a name.
 *  3. Index tile prices are populated (not all dashes) when the backend can reach
 *     Yahoo Finance — or at minimum the bar/tiles exist when prices are unavailable.
 *  4. The portfolio table includes a "Post-Market" column header.
 *  5. Each row in the portfolio table has a Post-Market cell (even if showing "—").
 *  6. The GET /api/market/status endpoint returns a valid status object.
 *  7. The GET /api/market/indices endpoint returns exactly 5 entries with required fields.
 *  8. The GET /api/portfolio/client/:id/summary response has a postMarketPrice field
 *     on every holding (null or a number, never missing from the JSON object).
 *
 * Run against the live Docker stack:
 *   npm run frontend:test:docker --prefix quality
 */

import { test, expect, Page, APIRequestContext } from '@playwright/test';

// ── Auth helpers ──────────────────────────────────────────────────────────────

const CLIENT5_ID  = 5;
const CLIENT5_B64 = Buffer.from('client5:pass1234').toString('base64');
const ADMIN_B64   = Buffer.from('admin1:pass1234').toString('base64');
const ACTIVITY_CSV = 'GeneratedActivity-IRA94178.csv';

/** Inject client5 session into localStorage before Angular boots. */
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

/** Ensure client5 has at least one portfolio holding. */
async function ensureClient5HasHoldings(request: APIRequestContext): Promise<void> {
  const summaryResp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
    headers: { Authorization: `Basic ${CLIENT5_B64}` },
    timeout: 20_000
  });
  if (summaryResp.ok()) {
    const body = await summaryResp.json().catch(() => ({ holdings: [] }));
    if (((body.holdings ?? []) as unknown[]).length > 0) return;
  }

  await request.delete('/api/import/cleanup', {
    headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
    data: { clientId: CLIENT5_ID }
  });
  await request.post('/api/import/activity', {
    headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
    data: { clientId: CLIENT5_ID, fileName: ACTIVITY_CSV }
  });

  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const poll = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` }, timeout: 20_000
    });
    if (poll.ok()) {
      const body = await poll.json().catch(() => ({ holdings: [] }));
      if (((body.holdings ?? []) as unknown[]).length > 0) return;
    }
    await new Promise(r => setTimeout(r, 5_000));
  }
}

/** Wait until the portfolio table has loaded (cash-summary card is the sentinel). */
async function waitForPortfolioTable(page: Page) {
  await expect(page.getByText('Cash Balance')).toBeVisible({ timeout: 90_000 });
}

// ── Test suite ────────────────────────────────────────────────────────────────

test.describe('Market indices bar and post-market price column', () => {

  test.beforeAll(async ({ request }) => {
    test.setTimeout(200_000);
    await ensureClient5HasHoldings(request);
  });

  // ── 1. Market-status pill ────────────────────────────────────────────────

  test('market-status pill is visible on portfolio page', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    // The pill renders inside .market-bar; its text is one of the known labels
    const pill = page.locator('.market-status-pill');
    await expect(pill).toBeVisible({ timeout: 15_000 });

    const pillText = ((await pill.textContent()) ?? '').trim();
    const knownLabels = ['Open', 'Pre-Market', 'Post-Market', 'Closed'];
    expect(knownLabels.some(l => pillText.includes(l)),
      `Pill text "${pillText}" is not a recognised market-status label`
    ).toBeTruthy();
  });

  // ── 2. Index bar renders with 5 tiles ────────────────────────────────────

  test('index quotes bar renders with 5 tiles', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    // Allow extra time for the async indices call to resolve
    await expect(page.locator('.indices-bar')).toBeVisible({ timeout: 20_000 });

    const tiles = page.locator('.index-tile');
    await expect(tiles).toHaveCount(5, { timeout: 15_000 });
  });

  // ── 3. Each tile has a name ──────────────────────────────────────────────

  test('every index tile has a non-empty name', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);
    await expect(page.locator('.indices-bar')).toBeVisible({ timeout: 20_000 });

    const tiles = page.locator('.index-tile');
    const count = await tiles.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
      const nameTxt = ((await tiles.nth(i).locator('.index-name').textContent()) ?? '').trim();
      expect(nameTxt, `Tile ${i + 1} has an empty name`).not.toBe('');
    }
  });

  // ── 4. Index tiles show prices or dashes (not broken markup) ────────────

  test('every index tile shows either a price value or a dash placeholder', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);
    await expect(page.locator('.indices-bar')).toBeVisible({ timeout: 20_000 });

    const tiles = page.locator('.index-tile');
    const count = await tiles.count();

    for (let i = 0; i < count; i++) {
      const tile = tiles.nth(i);
      // Either .index-price (real value) or .index-price.na (dash) should exist
      const priceLocator = tile.locator('.index-price');
      await expect(priceLocator).toBeVisible({ timeout: 5_000 });
    }
  });

  // ── 5. Portfolio table has a Post-Market column ──────────────────────────

  test('portfolio table header contains a Post-Market column', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    const headers = page.locator('table.portfolio-table thead th');
    const headerTexts = await headers.allTextContents();
    const hasPostMarket = headerTexts.some(t => t.toLowerCase().includes('post'));
    expect(hasPostMarket, `Expected a Post-Market header. Found: ${headerTexts.join(' | ')}`).toBeTruthy();
  });

  // ── 6. Every data row has a Post-Market cell ─────────────────────────────

  test('every portfolio row has a post-market cell', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/portfolio');
    await waitForPortfolioTable(page);

    const rows = page.locator('table.portfolio-table tbody tr:has(td.symbol-cell)');
    const rowCount = await rows.count();
    expect(rowCount, 'No data rows found in the portfolio table').toBeGreaterThan(0);

    for (let i = 0; i < rowCount; i++) {
      const postCell = rows.nth(i).locator('td.post-market-cell');
      await expect(postCell).toBeVisible({ timeout: 5_000 });
      // The cell must contain either a dollar-prefixed price or the "—" placeholder
      const cellText = ((await postCell.textContent()) ?? '').trim();
      expect(
        cellText.includes('$') || cellText.includes('—'),
        `Row ${i + 1}: unexpected post-market cell content: "${cellText}"`
      ).toBeTruthy();
    }
  });

  // ── 7. API: /api/market/status returns valid structure ──────────────────

  test('GET /api/market/status returns 200 with valid status fields', async ({ request }) => {
    const resp = await request.get('/api/market/status', {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 10_000
    });
    expect(resp.ok(), `Expected 200 from /api/market/status, got ${resp.status()}`).toBeTruthy();

    const body = await resp.json();
    expect(body).toHaveProperty('status');
    expect(body).toHaveProperty('statusLabel');
    expect(body).toHaveProperty('estTime');
    expect(body).toHaveProperty('isRegularOpen');

    const knownStatuses = ['OPEN', 'PRE_MARKET', 'POST_MARKET', 'CLOSED'];
    expect(knownStatuses).toContain(body.status);
    expect(typeof body.isRegularOpen).toBe('boolean');
    expect(body.estTime).toBeTruthy();
  });

  // ── 8. API: /api/market/indices returns 5 entries with required fields ───

  test('GET /api/market/indices returns 5 entries with symbol and name', async ({ request }) => {
    const resp = await request.get('/api/market/indices', {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 15_000
    });
    expect(resp.ok(), `Expected 200 from /api/market/indices, got ${resp.status()}`).toBeTruthy();

    const body = await resp.json() as unknown[];
    expect(Array.isArray(body), 'Response should be an array').toBeTruthy();
    expect(body.length).toBe(5);

    const expectedSymbols = ['^GSPC', '^DJI', '^IXIC', 'GC=F', '^RUT'];
    for (const entry of body as Record<string, unknown>[]) {
      expect(entry).toHaveProperty('symbol');
      expect(entry).toHaveProperty('name');
      expect(entry['name']).toBeTruthy();
      expect(expectedSymbols).toContain(entry['symbol']);
    }
  });

  // ── 9. API: portfolio summary has postMarketPrice on every holding ───────

  test('portfolio summary holdings each have a postMarketPrice field', async ({ request }) => {
    const resp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 30_000
    });
    expect(resp.ok(), `Expected 200 from portfolio summary, got ${resp.status()}`).toBeTruthy();

    const body = await resp.json();
    const holdings = (body.holdings ?? []) as Record<string, unknown>[];
    expect(holdings.length, 'Portfolio has no holdings').toBeGreaterThan(0);

    for (const holding of holdings) {
      expect(holding, `Holding ${holding['symbol']} is missing postMarketPrice field`)
        .toHaveProperty('postMarketPrice');
      // Value must be null or a finite number – never undefined or a non-numeric string
      const pm = holding['postMarketPrice'];
      expect(
        pm === null || (typeof pm === 'number' && isFinite(pm)),
        `Holding ${holding['symbol']}: postMarketPrice should be null or finite number, got ${JSON.stringify(pm)}`
      ).toBeTruthy();
    }
  });
});
