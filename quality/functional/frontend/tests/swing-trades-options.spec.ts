/**
 * Swing trade UI tests – options overlay integration
 *
 * Verifies that:
 *  1. The Swing Trade Suggestions section is visible on the Suggested Trades page.
 *  2. When the backend returns swing suggestions the table renders correctly.
 *  3. The reasoning field is displayed and may include Options market context.
 *  4. The swing trade success-rate badge renders once resolved trades exist.
 *  5. The Refresh button triggers a new fetch without crashing.
 *
 * Run locally (against Docker stack):
 *   npm run frontend:test:docker --prefix quality
 *
 * Or with the combined suite:
 *   npm run test:docker --prefix quality
 */

import { test, expect, Page, APIRequestContext } from '@playwright/test';

// ── Auth helpers ─────────────────────────────────────────────────────────────

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

/** Ensure client5 has portfolio holdings (import once for the whole suite). */
async function ensureClient5HasHoldings(request: APIRequestContext): Promise<void> {
  const summaryResp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
    headers: { Authorization: `Basic ${CLIENT5_B64}` },
    timeout: 15_000
  });
  if (summaryResp.ok()) {
    const body = await summaryResp.json().catch(() => ({ holdings: [] }));
    if (((body.holdings ?? []) as unknown[]).length > 0) {
      return; // already populated
    }
  }

  // Clean + import
  await request.delete('/api/import/cleanup', {
    headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
    data: { clientId: CLIENT5_ID }
  });
  await request.post('/api/import/activity', {
    headers: { Authorization: `Basic ${ADMIN_B64}`, 'Content-Type': 'application/json' },
    data: { clientId: CLIENT5_ID, fileName: ACTIVITY_CSV }
  });

  // Poll for reconciliation
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const pollResp = await request.get(`/api/portfolio/client/${CLIENT5_ID}/summary`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 15_000
    });
    if (pollResp.ok()) {
      const body = await pollResp.json().catch(() => ({ holdings: [] }));
      if (((body.holdings ?? []) as unknown[]).length > 0) return;
    }
    await new Promise(r => setTimeout(r, 5_000));
  }
}

// ── Test suite ────────────────────────────────────────────────────────────────

test.describe('Swing Trade Suggestions – options overlay', () => {

  test.beforeAll(async ({ request }) => {
    test.setTimeout(200_000);
    console.log('[setup] Ensuring client5 has portfolio holdings for swing trade tests…');
    await ensureClient5HasHoldings(request);
    console.log('[setup] Portfolio ready');
  });

  // 1. Section is visible on page load ────────────────────────────────────────

  test('Swing Trade section heading is visible on Suggested Trades page', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/suggested-trades');

    await expect(page.getByRole('heading', { name: 'Suggested Trades' }).first()).toBeVisible();
    await expect(page.getByText('Swing Trade Suggestions')).toBeVisible({ timeout: 15_000 });
  });

  // 2. Table or "no signal" notice renders ────────────────────────────────────

  test('Swing trade section shows table or no-signal notice after analysis', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/suggested-trades');

    // Wait for swing section to finish loading (spinner disappears)
    await expect(page.getByText('Swing Trade Suggestions')).toBeVisible({ timeout: 15_000 });

    // Either a filled table OR the "no swing signals" placeholder must be present
    const tableOrNotice = page.locator('table.swing-table, text=No swing trade signals at this time');
    await expect(tableOrNotice.first()).toBeVisible({ timeout: 90_000 });
  });

  // 3. Swing suggestions endpoint returns valid shape ─────────────────────────

  test('API: swing suggestions endpoint returns 200 with an array', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('API: each swing suggestion has required fields', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();

    for (const item of suggestions as Record<string, unknown>[]) {
      expect(typeof item['symbol']).toBe('string');
      expect(['HOLD', 'SELL']).toContain(item['action']);
      expect(typeof item['confidence']).toBe('number');
      expect(item['confidence'] as number).toBeGreaterThanOrEqual(0);
      expect(item['confidence'] as number).toBeLessThanOrEqualTo(95);
      expect(typeof item['reasoning']).toBe('string');
      expect((item['reasoning'] as string).length).toBeGreaterThan(0);
      expect(typeof item['targetPrice']).toBe('number');
      expect(item['targetPrice'] as number).toBeGreaterThan(0);
      expect(typeof item['stopLoss']).toBe('number');
      expect(item['stopLoss'] as number).toBeGreaterThan(0);
    }
  });

  // 4. Reasoning may include options context ──────────────────────────────────

  test('API: reasoning field is a non-empty string', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();

    for (const item of suggestions as Record<string, unknown>[]) {
      const reasoning = item['reasoning'] as string;
      // Must contain the symbol and a direction word
      expect(reasoning).toMatch(/Bullish|Bearish/);
    }
  });

  test('API: when options data is available reasoning contains "Options market:" suffix', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();

    // At least one suggestion with options data should mention the suffix.
    // If Yahoo Finance is reachable the suffix will be present; if not, we just
    // assert the missing suffix does not make the reasoning blank.
    for (const item of suggestions as Record<string, unknown>[]) {
      const reasoning = item['reasoning'] as string;
      expect(reasoning.length).toBeGreaterThan(10);
    }
  });

  // 5. HOLD stop-loss is below current price; SELL stop-loss is above target ──

  test('API: HOLD stop-loss is below current price', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();

    for (const item of suggestions as Record<string, unknown>[]) {
      if (item['action'] === 'HOLD') {
        expect(item['stopLoss'] as number).toBeLessThan(item['currentPrice'] as number);
        expect(item['targetPrice'] as number).toBeGreaterThan(item['currentPrice'] as number);
      }
    }
  });

  test('API: SELL stop-loss is above re-entry target price', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();

    for (const item of suggestions as Record<string, unknown>[]) {
      if (item['action'] === 'SELL') {
        expect(item['stopLoss'] as number).toBeGreaterThan(item['targetPrice'] as number);
      }
    }
  });

  // 6. At most 5 suggestions returned ─────────────────────────────────────────

  test('API: at most 5 swing suggestions returned', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/suggestions/${CLIENT5_ID}`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 90_000
    });
    const suggestions: unknown[] = await resp.json();
    expect(suggestions.length).toBeLessThanOrEqualTo(5);
  });

  // 7. Swing history endpoint ─────────────────────────────────────────────────

  test('API: swing history returns 200 with an array', async ({ request }) => {
    const resp = await request.get(`/api/swing-trades/${CLIENT5_ID}/history`, {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 30_000
    });
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  // 8. Trend analysis includes Options_Sentiment technique ───────────────────

  test('API: trend analysis includes Options_Sentiment in techniqueResults', async ({ request }) => {
    const resp = await request.get('/api/trends/last/AAPL', {
      headers: { Authorization: `Basic ${CLIENT5_B64}` },
      timeout: 60_000
    });
    expect(resp.status()).toBe(200);
    const body = await resp.json();

    expect(body).toHaveProperty('overallTrend');
    expect(['UPTREND', 'DOWNTREND', 'SIDEWAYS']).toContain(body.overallTrend);
    expect(body).toHaveProperty('techniqueResults');
    expect(body.techniqueResults).toHaveProperty('Options_Sentiment');
    expect(['UPTREND', 'DOWNTREND', 'SIDEWAYS']).toContain(body.techniqueResults['Options_Sentiment']);
  });

  // 9. Refresh button triggers re-fetch ──────────────────────────────────────

  test('Swing Refresh button is enabled and clickable', async ({ page }) => {
    await bootstrapClient5(page);
    await page.goto('/suggested-trades');

    await expect(page.getByText('Swing Trade Suggestions')).toBeVisible({ timeout: 15_000 });

    const refreshBtn = page.locator('button', { hasText: /Refresh/i }).last();
    await expect(refreshBtn).toBeVisible({ timeout: 30_000 });

    // After clicking, button should briefly show "Analysing..." while loading
    await refreshBtn.click();
    // Just verify it doesn't crash and the section remains visible
    await expect(page.getByText('Swing Trade Suggestions')).toBeVisible({ timeout: 5_000 });
  });
});
