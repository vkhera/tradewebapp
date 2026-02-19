import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import encoding from 'k6/encoding';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const user = __ENV.CLIENT_USER || 'client1';
const password = __ENV.CLIENT_PASSWORD || 'pass1234';
const clientId = __ENV.CLIENT_ID || '1';

const errors = new Rate('errors');

export const options = {
  stages: [
    { duration: '2m', target: 10 },
    { duration: '5m', target: 25 },
    { duration: '3m', target: 40 },
    { duration: '2m', target: 0 }
  ],
  thresholds: {
    http_req_failed: ['rate<0.03'],
    http_req_duration: ['p(95)<1500', 'p(99)<2500'],
    errors: ['rate<0.03']
  }
};

function authHeaders() {
  const token = encoding.b64encode(`${user}:${password}`);
  return {
    headers: {
      'Authorization': `Basic ${token}`,
      'Content-Type': 'application/json'
    }
  };
}

const targets = [
  (cfg) => http.get(`${baseUrl}/api/account/client/${clientId}`, cfg),
  (cfg) => http.get(`${baseUrl}/api/portfolio/client/${clientId}`, cfg),
  (cfg) => http.get(`${baseUrl}/api/portfolio/client/${clientId}/summary`, cfg),
  (cfg) => http.get(`${baseUrl}/api/trades/client/${clientId}`, cfg),
  (cfg) => http.get(`${baseUrl}/api/stocks/price/TQQQ`, cfg),
  (cfg) => http.get(`${baseUrl}/api/trends/last/TQQQ`, cfg),
  (cfg) => http.get(`${baseUrl}/api/predictions/TQQQ`, cfg)
];

export default function () {
  const cfg = authHeaders();
  const endpoint = targets[Math.floor(Math.random() * targets.length)];
  const response = endpoint(cfg);

  const ok = check(response, {
    'status is 200': (r) => r.status === 200
  });
  errors.add(!ok);

  sleep(0.5);
}
