# Deployment Readiness

## Status

The project is suitable for local deployment, demos, and staging-style environments. It still needs configuration hardening before unmanaged internet-facing production use.

## Verified From This Workspace

- Backend runtime integration tests pass
- Backend Phase 8 promo tests pass
- Mobile tests pass
- Mobile typecheck passes

## Runtime Topology

- `backend/`: Spring Boot API with PostgreSQL, Flyway, optional Redis caching
- `web/`: Next.js staff console with proxy route under `/api/rm/**`
- `mobile/`: Expo/React Native customer app calling backend root routes directly
- `docker-compose.yml`: Postgres, Redis, backend, Zipkin, Prometheus, Grafana, pgAdmin

## Required Backend Environment Variables

Defined in [application.yml](/C:/Users/HP/Desktop/restaurant_manager/backend/src/main/resources/application.yml):

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `PAYSTACK_SECRET_KEY`
- `PAYSTACK_BASE_URL`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `CACHE_REDIS_ENABLED`
- `CORS_ALLOWED_ORIGINS`
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`
- `ZIPKIN_ENDPOINT`
- `TRACING_SAMPLING_PROBABILITY`

### Integrations (stub/log by default, so no real money or messages move until configured)

| Variable | Purpose |
|---|---|
| `PAYSTACK_MODE` | `stub` (default) or `live`. Live requires an `sk_test_...`/`sk_live_...` key and fails at startup otherwise |
| `PAYSTACK_CALLBACK_URL` | Optional redirect after Paystack checkout |
| `WHATSAPP_PROVIDER` | `log` (default) or `meta` (WhatsApp Cloud API) |
| `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | Required for `meta` |
| `WHATSAPP_TEMPLATE_NAME`, `WHATSAPP_TEMPLATE_LANGUAGE` | Approved template for business-initiated messages (outside the 24 h window) |
| `SMS_PROVIDER` | `log` (default), `hubtel` or `arkesel`. Used for OTP codes |
| `SMS_SENDER_ID`, `HUBTEL_CLIENT_ID`, `HUBTEL_CLIENT_SECRET`, `ARKESEL_API_KEY` | SMS provider credentials |
| `OTP_REQUIRED_FOR_REGISTRATION` | `true` by default. Customers must verify their phone by SMS before registering |
| `PUBLIC_WEB_URL`, `VENUE_NAME` | Used in printed table QR cards and SMS text |
| `LOYALTY_POINTS_PER_CEDI`, `LOYALTY_POINT_VALUE`, `LOYALTY_MAX_REDEEM_PERCENT`, `LOYALTY_AUTO_ACCRUE` | Loyalty rules |

With `SMS_PROVIDER=log`, OTP codes are only written to the backend log. Set a real provider before you enable customer registration in production.

## Production Hardening Required

Replace immediately:
- default `JWT_SECRET`
- default `PAYSTACK_SECRET_KEY`
- default Grafana admin password
- default pgAdmin password
- any permissive origin lists

Lock down:
- `CORS_ALLOWED_ORIGINS`
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`
- public access to admin/observability services

## Docker Compose Audit

The compose file is good for development, not production.

Strengths:
- healthchecks for Postgres and Redis
- persistent volumes
- backend observability stack included

Production gaps:
- hard-coded default credentials
- no reverse proxy or TLS termination
- pgAdmin exposed by default
- Grafana exposed with default credentials
- backend secrets supplied inline

## Database and Migration Readiness

Good:
- Flyway enabled in runtime
- JPA uses `ddl-auto: validate`
- promo codes are now persisted through `V7__promo_codes.sql`

Gap:
- default integration tests still run on H2 with `create-drop`, not PostgreSQL with Flyway

Recommendation:
- add a staging or CI path that runs migrations and smoke tests against PostgreSQL
- the repository now includes a Docker-backed Maven profile for this path:
  `cd backend && mvn -q -Ppostgres-it -Dtest=PostgresFlywayIntegrationTest test`

## Security Readiness

Implemented:
- stateless JWT auth
- rate limiting
- manager override-token flow
- role-based method security
- configurable CORS and WebSocket origin settings

Remaining concerns:
- default secrets remain in checked-in examples
- `prometheus` actuator endpoint is publicly permitted
- local admin tools are published broadly in compose

## Observability Readiness

Available:
- Prometheus metrics
- Zipkin tracing
- Grafana datasource provisioning
- structured logging support

Missing:
- alert rules
- incident/runbook detail beyond basic setup
- retention and cost-control guidance

## Pre-launch Verification

These features are implemented and covered by automated tests, but have not yet been exercised against real services or devices.

| Item | What is still needed |
|---|---|
| **Migrations V9–V12 on PostgreSQL** | Tests run on H2 with schema generated from entities. Run `mvn -Ppostgres-it -Dtest=PostgresFlywayIntegrationTest test` (needs Docker) before deploying |
| **Paystack live mode** | Tested only against a mock HTTP server. Try it with `sk_test_` keys and a real MoMo test number, including the webhook and refunds |
| **WhatsApp Cloud API** | Tested only against a mock server. Needs a Meta test number and an approved template for messages outside the 24-hour window |
| **Hubtel / Arkesel SMS** | Not called for real yet. Needs a registered sender ID |
| **Web and mobile screens** | The new pages (financial, offers, delivery, rider, QR PDF download) and the mobile flows (OTP sign-up, live updates, delivery tracking) pass typechecks and tests but have not been tried in a browser or on a device |
| **Rider location sharing** | Uses browser geolocation, which needs HTTPS (or localhost) and permission on the rider's phone |
| **k6 scripts** | Written but not yet run against a live stack |

## Release Checklist

1. Replace all default secrets and passwords.
2. Restrict allowed origins to deployed domains only.
3. Put backend/web behind HTTPS and a reverse proxy.
4. Disable or firewall pgAdmin and nonessential admin tools.
5. Run Flyway migrations against PostgreSQL in staging.
6. Verify web proxy and mobile base URLs against deployed hostnames.
7. Decide whether seeded promo codes remain bootstrap data or move to admin-managed creation flows.

## Conclusion

- Backend core: strong
- Web app: good
- Mobile app: good
- Documentation accuracy: acceptable after sync, but still requires maintenance discipline
- Production hardening: incomplete

Overall: ready for local and staging-style deployment, not ready for direct public production without environment and security hardening.
