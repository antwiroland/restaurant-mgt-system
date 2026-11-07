# Load tests (k6)

Real load tests against a running stack. They replace the old `/phase10/load/run` endpoint, which only timed mocked methods.

| Script | What it exercises | Main thresholds |
|---|---|---|
| `k6/checkout.js` | Staff create pickup orders and initiate payments (ramping VUs) | p95 order create / payment initiate < 800 ms, < 1% errors |
| `k6/browse.js` | Public menu and table bill/tracking reads at a fixed arrival rate | p95 menu < 300 ms |
| `k6/realtime.js` | Many STOMP WebSocket subscribers receiving `/topic/orders.new` while orders are produced | WebSocket connect p95 < 1 s, events delivered |

## Prerequisites

- The backend is running (for example with `docker compose up -d`).
- At least one available menu item exists.
- A staff account exists (cashier, manager or admin) for the scripts that create orders.
- Use `PAYSTACK_MODE=stub` (the default) so that no real charges are made. `checkout.js` initiates **CASH** payments.

## Running

With k6 installed:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e STAFF_PHONE=+233200000000 -e STAFF_PASSWORD=secret123 loadtest/k6/checkout.js
k6 run -e BASE_URL=http://localhost:8080 -e RPS=100 loadtest/k6/browse.js
k6 run -e BASE_URL=http://localhost:8080 -e STAFF_PHONE=... -e STAFF_PASSWORD=... -e SUBSCRIBERS=200 loadtest/k6/realtime.js
```

With Docker Compose (k6 is in the `loadtest` profile, so it doesn't start with the normal stack):

```bash
STAFF_PHONE=+233200000000 STAFF_PASSWORD=secret123 \
  docker compose --profile loadtest run --rm k6 run /scripts/checkout.js
```

Tuning variables: `VUS`, `RAMP`, `HOLD` (checkout); `RPS`, `DURATION`, `TABLE_TOKEN` (browse); `SUBSCRIBERS`, `ORDERS_PER_SECOND`, `DURATION_S` (realtime).

Rate limiting: the backend limits `/auth/*` per IP. If the limit trips during setup, set `RATE_LIMIT_ENABLED=false` on the backend for load-test runs.

Run load tests against staging, not production: they create real orders.
