/**
 * End-to-end test: Buy 10 SMH at market value and verify it appears in portfolio.
 *
 * Flow:
 *  1. Fetch live SMH market price.
 *  2. Check client account balance.
 *  3. If available balance < (price * 10), top-up funds first.
 *  4. Submit a MARKET BUY order for 10 SMH.
 *  5. Assert the order is EXECUTED (or at minimum accepted with HTTP 201).
 *  6. Wait 60 s to allow the portfolio to reflect the trade.
 *  7. Fetch the portfolio and assert SMH is present.
 *  8. If SMH is missing, emit diagnostic information (recent trades, trade detail).
 */

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import encoding from 'k6/encoding';

const BASE_URL        = __ENV.BASE_URL        || 'http://localhost:8080';
const CLIENT_USER     = __ENV.CLIENT_USER     || 'client1';
const CLIENT_PASSWORD = __ENV.CLIENT_PASSWORD || 'pass1234';
const CLIENT_ID       = parseInt(__ENV.CLIENT_ID || '1', 10);

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.05']
  }
};

function authHeaders() {
  return {
    headers: {
      'Authorization': `Basic ${encoding.b64encode(`${CLIENT_USER}:${CLIENT_PASSWORD}`)}`,
      'Content-Type': 'application/json'
    }
  };
}

// ── Helper: add funds to the client account ──────────────────────────────────
function addFunds(amount) {
  console.log(`[SETUP] Adding $${amount.toFixed(2)} to client ${CLIENT_ID} account…`);
  const res = http.post(
    `${BASE_URL}/api/account/client/${CLIENT_ID}/add-funds`,
    JSON.stringify({ amount }),
    authHeaders()
  );
  check(res, {
    'add-funds returns 200': (r) => r.status === 200
  });
  if (res.status !== 200) {
    fail(`Failed to add funds: HTTP ${res.status} – ${res.body}`);
  }
  console.log(`[SETUP] Funds added successfully.`);
}

export default function () {
  const auth = authHeaders();

  // ── Step 1: Fetch live SMH price ──────────────────────────────────────────
  console.log('[STEP 1] Fetching SMH market price…');
  const priceRes = http.get(`${BASE_URL}/api/stocks/price/SMH`, auth);
  check(priceRes, {
    'SMH price endpoint returns 200': (r) => r.status === 200
  });
  if (priceRes.status !== 200) {
    fail(`Cannot fetch SMH price: HTTP ${priceRes.status} – ${priceRes.body}`);
  }

  let smhPrice;
  try {
    smhPrice = parseFloat(JSON.parse(priceRes.body).price);
  } catch (e) {
    fail(`Could not parse SMH price from response: ${priceRes.body}`);
  }

  check(priceRes, {
    'SMH price is a positive number': () => smhPrice > 0
  });
  console.log(`[STEP 1] SMH market price: $${smhPrice.toFixed(4)}`);

  const requiredFunds = smhPrice * 10;

  // ── Step 2: Check account balance ─────────────────────────────────────────
  console.log('[STEP 2] Checking account balance…');
  const acctRes = http.get(`${BASE_URL}/api/account/client/${CLIENT_ID}`, auth);
  check(acctRes, {
    'account endpoint returns 200': (r) => r.status === 200
  });
  if (acctRes.status !== 200) {
    fail(`Cannot fetch account: HTTP ${acctRes.status} – ${acctRes.body}`);
  }

  let availableBalance;
  try {
    availableBalance = parseFloat(JSON.parse(acctRes.body).availableBalance);
  } catch (e) {
    fail(`Could not parse availableBalance from response: ${acctRes.body}`);
  }
  console.log(`[STEP 2] Available balance: $${availableBalance.toFixed(2)} | Required: $${requiredFunds.toFixed(2)}`);

  // ── Step 3: Top-up funds if needed ────────────────────────────────────────
  if (availableBalance < requiredFunds) {
    const topUp = requiredFunds - availableBalance + 500; // buffer of $500
    console.log(`[STEP 3] Insufficient funds – topping up by $${topUp.toFixed(2)}…`);
    addFunds(topUp);

    // Re-verify the balance after top-up
    const acctRes2 = http.get(`${BASE_URL}/api/account/client/${CLIENT_ID}`, auth);
    let newBalance;
    try {
      newBalance = parseFloat(JSON.parse(acctRes2.body).availableBalance);
    } catch (e) {
      fail(`Could not parse availableBalance after top-up: ${acctRes2.body}`);
    }
    check(acctRes2, {
      'balance is sufficient after top-up': () => newBalance >= requiredFunds
    });
    console.log(`[STEP 3] Balance after top-up: $${newBalance.toFixed(2)}`);
  } else {
    console.log('[STEP 3] Sufficient funds – skipping top-up.');
  }

  // ── Step 4: Submit BUY order for 10 SMH at market price ───────────────────
  console.log(`[STEP 4] Submitting MARKET BUY order: 10 x SMH @ $${smhPrice.toFixed(4)}…`);
  const tradePayload = JSON.stringify({
    clientId: CLIENT_ID,
    symbol: 'SMH',
    quantity: 10,
    price: smhPrice,
    type: 'BUY',
    orderType: 'MARKET'
  });

  const tradeRes = http.post(`${BASE_URL}/api/trades`, tradePayload, auth);
  check(tradeRes, {
    'trade order accepted (HTTP 201)': (r) => r.status === 201
  });
  if (tradeRes.status !== 201) {
    fail(`Trade submission failed: HTTP ${tradeRes.status} – ${tradeRes.body}`);
  }

  let tradeBody;
  try {
    tradeBody = JSON.parse(tradeRes.body);
  } catch (e) {
    fail(`Could not parse trade response: ${tradeRes.body}`);
  }

  console.log(`[STEP 4] Trade response: id=${tradeBody.id} status=${tradeBody.status} fraudCheckPassed=${tradeBody.fraudCheckPassed}`);

  check(tradeRes, {
    'trade status is EXECUTED or PENDING': () =>
      tradeBody.status === 'EXECUTED' || tradeBody.status === 'PENDING',
    'trade is not REJECTED': () => tradeBody.status !== 'REJECTED',
    'trade symbol is SMH':   () => tradeBody.symbol === 'SMH',
    'trade quantity is 10':  () => tradeBody.quantity === 10
  });

  if (tradeBody.status === 'REJECTED') {
    fail(`Trade was REJECTED. Reason: ${tradeBody.fraudCheckReason || '(none)'}`);
  }

  if (tradeBody.status === 'EXECUTED') {
    console.log('[STEP 4] Order EXECUTED immediately.');
  } else {
    console.log(`[STEP 4] Order in status: ${tradeBody.status} – market order should self-execute shortly.`);
  }

  const tradeId = tradeBody.id;

  // ── Step 5: Wait 60 s for portfolio to reflect the trade ──────────────────
  console.log('[STEP 5] Waiting 60 seconds for portfolio to update…');
  sleep(60);

  // ── Step 6: Fetch portfolio and verify SMH is present ────────────────────
  console.log('[STEP 6] Fetching portfolio…');
  const portfolioRes = http.get(`${BASE_URL}/api/portfolio/client/${CLIENT_ID}`, auth);
  check(portfolioRes, {
    'portfolio endpoint returns 200': (r) => r.status === 200
  });
  if (portfolioRes.status !== 200) {
    fail(`Cannot fetch portfolio: HTTP ${portfolioRes.status} – ${portfolioRes.body}`);
  }

  let holdings;
  try {
    holdings = JSON.parse(portfolioRes.body);
  } catch (e) {
    fail(`Could not parse portfolio response: ${portfolioRes.body}`);
  }

  const smhHolding = Array.isArray(holdings)
    ? holdings.find(h => h.symbol === 'SMH')
    : null;

  check(portfolioRes, {
    'SMH appears in portfolio': () => smhHolding !== null && smhHolding !== undefined
  });

  if (smhHolding) {
    console.log(`[STEP 6] SMH found in portfolio: quantity=${smhHolding.quantity} avgCost=${smhHolding.averageCost}`);
    console.log('[PASS] SMH buy order → portfolio verification complete.');
    return;
  }

  // ── Step 7: SMH missing – diagnostics ────────────────────────────────────
  console.error('[DEBUG] SMH not found in portfolio after 60 s. Running diagnostics…');
  console.log(`[DEBUG] Portfolio holdings: ${JSON.stringify(holdings)}`);

  // Check the specific trade status
  if (tradeId) {
    const tradeDetailRes = http.get(`${BASE_URL}/api/trades/${tradeId}`, auth);
    if (tradeDetailRes.status === 200) {
      try {
        const latestTrade = JSON.parse(tradeDetailRes.body);
        console.log(`[DEBUG] Trade ${tradeId} current status: ${latestTrade.status} | fraudCheckPassed: ${latestTrade.fraudCheckPassed} | reason: ${latestTrade.fraudCheckReason || 'n/a'}`);
      } catch (e) {
        console.log(`[DEBUG] Trade detail raw response: ${tradeDetailRes.body}`);
      }
    } else {
      console.log(`[DEBUG] Could not fetch trade detail: HTTP ${tradeDetailRes.status}`);
    }
  }

  // List recent trades for this client to spot any issues
  const recentTradesRes = http.get(`${BASE_URL}/api/trades/client/${CLIENT_ID}`, auth);
  if (recentTradesRes.status === 200) {
    try {
      const allTrades = JSON.parse(recentTradesRes.body);
      const smhTrades = allTrades.filter(t => t.symbol === 'SMH');
      console.log(`[DEBUG] All SMH trades for client ${CLIENT_ID}: ${JSON.stringify(smhTrades)}`);
    } catch (e) {
      console.log(`[DEBUG] Could not parse trades: ${recentTradesRes.body}`);
    }
  }

  // Re-check account to see if funds were debited (trade executed but portfolio not updated)
  const acctFinalRes = http.get(`${BASE_URL}/api/account/client/${CLIENT_ID}`, auth);
  if (acctFinalRes.status === 200) {
    try {
      const acctFinal = JSON.parse(acctFinalRes.body);
      console.log(`[DEBUG] Final account balance: available=${acctFinal.availableBalance} reserved=${acctFinal.reservedBalance} total=${acctFinal.accountBalance}`);
    } catch (e) {
      console.log(`[DEBUG] Could not parse final account: ${acctFinalRes.body}`);
    }
  }

  // Fail the test so CI/CD catches it
  fail('SMH did not appear in portfolio after trade execution – see debug output above');
}
