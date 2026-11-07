// Public read traffic: guests browsing the menu and checking a table bill/tracking after scanning a QR.
// Run: k6 run -e BASE_URL=http://localhost:8080 [-e TABLE_TOKEN=...] loadtest/k6/browse.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib.js';

export const options = {
  scenarios: {
    browse: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 50),
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
  },
  thresholds: {
    'http_req_duration{endpoint:menu}': ['p(95)<300'],
    'http_req_failed{endpoint:menu}': ['rate<0.01'],
  },
};

export default function () {
  const menu = http.get(`${BASE_URL}/menu/items`, { tags: { endpoint: 'menu' } });
  check(menu, { 'menu 200': (r) => r.status === 200 });

  http.get(`${BASE_URL}/menu/categories`, { tags: { endpoint: 'menu' } });

  const token = __ENV.TABLE_TOKEN;
  if (token) {
    const bill = http.get(`${BASE_URL}/orders/public/dine-in/tables/${token}/bill`, { tags: { endpoint: 'table' } });
    check(bill, { 'bill 200': (r) => r.status === 200 });
    http.get(`${BASE_URL}/orders/public/dine-in/tables/${token}/tracking`, { tags: { endpoint: 'table' } });
  }
  sleep(0.2);
}
