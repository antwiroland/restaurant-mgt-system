// Order + payment throughput: staff create pickup orders and initiate payments concurrently.
// Run: k6 run -e BASE_URL=http://localhost:8080 -e STAFF_PHONE=... -e STAFF_PASSWORD=... loadtest/k6/checkout.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, availableMenuItems, jsonHeaders, pick, staffLogin } from './lib.js';

const orderLatency = new Trend('order_create_ms', true);
const paymentLatency = new Trend('payment_initiate_ms', true);
const ordersCreated = new Counter('orders_created');

export const options = {
  scenarios: {
    checkout: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: __ENV.RAMP || '30s', target: Number(__ENV.VUS || 20) },
        { duration: __ENV.HOLD || '1m', target: Number(__ENV.VUS || 20) },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    order_create_ms: ['p(95)<800'],
    payment_initiate_ms: ['p(95)<800'],
  },
};

export function setup() {
  return { token: staffLogin(), menuItemIds: availableMenuItems() };
}

export default function (data) {
  const items = [{ menuItemId: pick(data.menuItemIds), quantity: 1 + Math.floor(Math.random() * 3) }];
  const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify({ type: 'PICKUP', items }), jsonHeaders(data.token));
  orderLatency.add(orderRes.timings.duration);
  if (!check(orderRes, { 'order created': (r) => r.status === 201 })) {
    sleep(1);
    return;
  }
  ordersCreated.add(1);

  const payment = {
    orderId: orderRes.json('id'),
    method: 'CASH',
    momoPhone: '0240000000',
    idempotencyKey: `k6-${__VU}-${__ITER}-${Date.now()}`,
  };
  const payRes = http.post(`${BASE_URL}/payments/initiate`, JSON.stringify(payment), jsonHeaders(data.token));
  paymentLatency.add(payRes.timings.duration);
  check(payRes, { 'payment initiated': (r) => r.status === 201 });

  sleep(0.5 + Math.random());
}
