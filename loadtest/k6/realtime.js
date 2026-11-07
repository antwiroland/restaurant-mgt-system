// WebSocket fan-out: many STOMP subscribers on /topic/orders.new while a producer creates orders.
// Measures how many order events each subscriber receives.
// Run: k6 run -e BASE_URL=http://localhost:8080 -e STAFF_PHONE=... -e STAFF_PASSWORD=... loadtest/k6/realtime.js
import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, availableMenuItems, jsonHeaders, pick, staffLogin } from './lib.js';

const eventsReceived = new Counter('stomp_events_received');
const NUL = '\u0000';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws';
const DURATION_S = Number(__ENV.DURATION_S || 60);

export const options = {
  scenarios: {
    subscribers: {
      executor: 'constant-vus',
      exec: 'subscriber',
      vus: Number(__ENV.SUBSCRIBERS || 100),
      duration: `${DURATION_S + 10}s`,
    },
    producer: {
      executor: 'constant-arrival-rate',
      exec: 'producer',
      rate: Number(__ENV.ORDERS_PER_SECOND || 2),
      timeUnit: '1s',
      duration: `${DURATION_S}s`,
      startTime: '5s',
      preAllocatedVUs: 5,
    },
  },
  thresholds: {
    ws_connecting: ['p(95)<1000'],
    stomp_events_received: ['count>0'],
  },
};

export function setup() {
  return { token: staffLogin(), menuItemIds: availableMenuItems() };
}

export function subscriber() {
  const res = ws.connect(WS_URL, {}, (socket) => {
    socket.on('open', () => {
      socket.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nhost:localhost\n\n${NUL}`);
    });
    socket.on('message', (frame) => {
      if (frame.startsWith('CONNECTED')) {
        socket.send(`SUBSCRIBE\nid:sub-${__VU}\ndestination:/topic/orders.new\n\n${NUL}`);
      } else if (frame.startsWith('MESSAGE')) {
        eventsReceived.add(1);
      }
    });
    socket.setTimeout(() => socket.close(), (DURATION_S + 5) * 1000);
  });
  check(res, { 'ws upgraded': (r) => r && r.status === 101 });
}

export function producer(data) {
  const items = [{ menuItemId: pick(data.menuItemIds), quantity: 1 }];
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({ type: 'PICKUP', items }), jsonHeaders(data.token));
  check(res, { 'order created': (r) => r.status === 201 });
  sleep(0.1);
}
