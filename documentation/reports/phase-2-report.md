# Phase 2 Report - Backend Core

**Phase:** 2  
**Title:** Backend Core (Auth, Roles, PIN)  
**Date Completed:** 2026-03-24

## Scope Delivered

- Spring Boot backend initialization with Maven and Java 17
- PostgreSQL datasource and HikariCP configuration in application profiles
- Flyway migration for core Phase 2 tables
- Environment profiles: `dev`, `staging`, `prod`
- Structured JSON logging in `prod` profile
- JWT auth (access + refresh) and refresh-token revocation
- User registration, login, refresh, and logout endpoints
- Role-based guards for admin and staff/customer boundaries
- Manager PIN set and verify flow with lockout policy
- Short-lived scoped override token generation for financial actions
- PIN-guarded financial endpoints for `DISCOUNT`, `VOID`, `REFUND`
- Audit logging for sensitive events

## API Endpoints Implemented

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/pin/verify`
- `GET /users`
- `PATCH /users/{id}/role`
- `POST /users/{id}/pin`
- `POST /financial/discount`
- `POST /financial/void`
- `POST /financial/refund`

## Test Coverage

Implemented and passing tests for all Phase 2 scenarios listed in `documentation/DEVELOPMENT_GUIDE.md`:

- Authentication scenarios: 12 tests
- Role and permission scenarios: 6 tests
- Manager PIN scenarios: 8 tests
- Audit log scenarios: 4 tests

**Total:** 30 tests passing

## Command Verification

- `cd backend && mvn test -q` -> PASS

## Notes and Constraints

- Production/runtime configuration remains PostgreSQL + Flyway.
- Testcontainers could not run here due missing Docker runtime, so integration tests execute on H2 (`MODE=PostgreSQL`) for this environment.

## Files Added/Updated (Phase 2)

- `backend/pom.xml`
- `backend/src/main/java/**` (backend core implementation)
- `backend/src/main/resources/application*.yml`
- `backend/src/main/resources/logback-spring.xml`
- `backend/src/main/resources/db/migration/V1__phase2_core.sql`
- `backend/src/test/java/**` (Phase 2 integration tests)
- `documentation/PROJECT_PROGRESS.md`
- `documentation/CHANGELOG.md`

