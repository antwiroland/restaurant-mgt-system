# Changelog

All notable changes to this project are documented here.
Format: [Phase N - Title] followed by Added / Changed / Decisions.

---

## [Phase 1 - Planning and Design] - 2026-03-23

### Added
- `docs/ROLES_AND_PERMISSIONS.md` - Permission matrix for all four roles
- `docs/DATABASE_SCHEMA.sql` - Full PostgreSQL V1 schema (17 tables, enums, indexes, triggers)
- `docs/API_SPEC.md` - REST API specification across 12 endpoint groups
- `docs/WEBSOCKET_EVENTS.md` - STOMP WebSocket topics and SSE fallback specification
- `docs/OFFLINE_STRATEGY.md` - Offline scope, cache durations, and sync queue design
- `documentation/DEVELOPMENT_GUIDE.md` - Master guide with 10 phases and checkbox task lists
- `PROJECT_PROGRESS.md` - Phase progress tracker
- `CHANGELOG.md` - This file

### Decisions
- UUID primary keys on all tables
- Price snapshot on `order_items` to freeze prices at order time
- Manager PIN to scoped override-token pattern with 5-minute TTL
- Idempotency key on `payments` to prevent duplicate Paystack charges
- Paystack webhook as authoritative payment status source; polling as fallback
- Mobile offline queue with FIFO processing and 3-retry limit

---

## [Phase 2 - Backend Core] - 2026-03-24

### Added
- Spring Boot backend project with Maven and Java 17 (`pom.xml`)
- Environment profile configuration (`application.yml`, `application-dev.yml`, `application-staging.yml`, `application-prod.yml`)
- Flyway migration for Phase 2 core tables (`users`, `refresh_tokens`, `audit_logs`)
- Production structured logging config (`logback-spring.xml` with JSON encoder in `prod`)
- Domain model and repositories for users, refresh tokens, and audit logs
- JWT token service supporting access, refresh, and scoped override tokens
- Security filter chain for stateless JWT authentication and role-based authorization
- Auth endpoints:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `POST /auth/logout`
  - `POST /auth/pin/verify`
- User endpoints:
  - `GET /users` (admin only)
  - `PATCH /users/{id}/role` (admin only)
  - `POST /users/{id}/pin` (manager/admin constraints)
- PIN-guarded financial endpoints:
  - `POST /financial/discount`
  - `POST /financial/void`
  - `POST /financial/refund`
- Audit event writing for `USER_LOGIN`, `USER_LOGOUT`, `PIN_FAILED`, `PIN_LOCKED`, `PIN_VERIFIED`, and `ROLE_ASSIGNED`
- Phase 2 integration test suite (30 scenario tests)

### Changed
- Test runtime adapted to H2 in PostgreSQL compatibility mode because Docker/Testcontainers is unavailable in this execution environment.

### Verified
- `mvn test -q` passes (30 tests, 0 failures).
