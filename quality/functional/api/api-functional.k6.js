import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const clientUser = __ENV.CLIENT_USER || 'client1';
const clientPassword = __ENV.CLIENT_PASSWORD || 'pass1234';
const adminUser = __ENV.ADMIN_USER || 'admin1';
const adminPassword = __ENV.ADMIN_PASSWORD || 'pass1234';
const clientId = __ENV.CLIENT_ID || '1';
const p95Threshold = parseInt(__ENV.P95_THRESHOLD || '1500', 10);

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${p95Threshold}`]
  }
};

function authHeader(user, password) {
  const token = encoding.b64encode(`${user}:${password}`);
  return {
    headers: {
      'Authorization': `Basic ${token}`,
      'Content-Type': 'application/json'
    }
  };
}

export default function () {
  const loginPayload = JSON.stringify({ username: clientUser, password: clientPassword });
  const loginResponse = http.post(`${baseUrl}/api/auth/login`, loginPayload, { headers: { 'Content-Type': 'application/json' } });
  check(loginResponse, {
    'auth login succeeds': (r) => r.status === 200
  });

  const clientAuth = authHeader(clientUser, clientPassword);
  const adminAuth = authHeader(adminUser, adminPassword);

  const clientChecks = [
    { name: 'account endpoint', method: 'GET', path: `/api/account/client/${clientId}`, auth: clientAuth },
    { name: 'portfolio endpoint', method: 'GET', path: `/api/portfolio/client/${clientId}`, auth: clientAuth },
    { name: 'portfolio summary endpoint', method: 'GET', path: `/api/portfolio/client/${clientId}/summary`, auth: clientAuth },
    { name: 'trades by client endpoint', method: 'GET', path: `/api/trades/client/${clientId}`, auth: clientAuth },
    { name: 'stocks price endpoint', method: 'GET', path: '/api/stocks/price/TQQQ', auth: clientAuth },
    { name: 'trend endpoint', method: 'GET', path: '/api/trends/last/TQQQ', auth: clientAuth },
    { name: 'prediction endpoint', method: 'GET', path: '/api/predictions/TQQQ', auth: clientAuth }
  ];

  for (const api of clientChecks) {
    const response = http.request(api.method, `${baseUrl}${api.path}`, null, api.auth);
    check(response, {
      [`${api.name} status is 200`]: (r) => r.status === 200
    });
  }

  // Extra validation for the prediction endpoint
  const predResponse = http.get(`${baseUrl}/api/predictions/TQQQ`, clientAuth);
  check(predResponse, {
    'prediction response is 200':                  (r) => r.status === 200,
    'prediction has hourlyPredictions array':       (r) => { try { return Array.isArray(JSON.parse(r.body).hourlyPredictions); } catch { return false; } },
    'prediction has previousDayPredictions field':  (r) => { try { const b = JSON.parse(r.body); return 'previousDayPredictions' in b; } catch { return false; } },
    'hourlyPredictions entries have predictedPrice':(r) => { try { const h = JSON.parse(r.body).hourlyPredictions; return h.length === 0 || 'predictedPrice' in h[0]; } catch { return false; } },
    'hourlyPredictions entries have actualPrice key':(r) => { try { const h = JSON.parse(r.body).hourlyPredictions; return h.length === 0 || 'actualPrice' in h[0]; } catch { return false; } }
  });

  const adminChecks = [
    { name: 'admin clients endpoint', method: 'GET', path: '/api/admin/clients', auth: adminAuth },
    { name: 'admin rules endpoint', method: 'GET', path: '/api/admin/rules', auth: adminAuth },
    { name: 'admin trades endpoint', method: 'GET', path: '/api/admin/trades', auth: adminAuth }
  ];

  for (const api of adminChecks) {
    const response = http.request(api.method, `${baseUrl}${api.path}`, null, api.auth);
    check(response, {
      [`${api.name} status is 200`]: (r) => r.status === 200
    });
  }

  sleep(1);
}
