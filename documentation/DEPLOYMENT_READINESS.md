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
