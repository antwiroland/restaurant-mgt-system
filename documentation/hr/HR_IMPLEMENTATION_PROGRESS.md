# HR Implementation Progress

## Project Information

| Field | Value |
|---|---|
| Project | Restaurant Manager |
| Module | Human Resource & Workforce Management |
| Integration branch | `feature/restaurant-hr-management` (from `feature/operations-expansion` @ `35ef1ed`) |
| Implementation start | 2026-11-09 |
| Current status | Phases 0–3 complete and merged into the integration branch; Phase 4 (attendance & POS clock-in) not started |
| Last updated | 2026-11-09 |

## Overall Progress

- [x] Repository analysis
- [x] Architecture
- [~] Database design — Phase 1–3 entities done (V27–V29); later-phase entities not designed in SQL yet
- [x] Employee management (registry, departments, positions, contracts, documents, user linking)
- [x] Multi-branch management (assignment history, transfers, secondary/temporary assignments, covering-branch visibility, extra branch scope, branch workforce summary)
- [x] Shift scheduling (templates, validation, drafts/publishing, coverage, availability, swaps, notifications; legacy rota kept compatible)
- [ ] Attendance (pre-existing basic time clock in `workforce` is unchanged)
- [ ] POS integration
- [ ] Leave
- [ ] Timesheets
- [ ] Overtime
- [ ] Payroll
- [ ] Tips
- [ ] Service charges
- [ ] Recruitment
- [ ] Performance
- [ ] Training
- [~] Self-service — read-only profile, own schedule, availability, swap requests
- [ ] Reporting / HR dashboard
- [~] Security — HR permission model, branch isolation, private documents, audit done for Phase 1 scope
- [ ] Performance optimization / benchmarking
- [~] Regression testing — full backend suite and web suite run for Phase 1 (see Development Log)
- [ ] Final documentation

## Feature Completion Matrix

| Feature | Database | Backend | Frontend | Permissions | Tests | Status |
|---|---|---|---|---|---|---|
| HR permission model & grants | [x] | [x] | [x] | [x] | [x] | Complete |
| Departments & positions | [x] | [x] | [x] | [x] | [x] | Complete |
| Employee registry & profiles | [x] | [x] | [x] | [x] | [x] | Complete |
| Employee ↔ user account link / import | [x] | [x] | [x] | [x] | [x] | Complete |
| Employment contracts | [x] | [x] | [x] | [x] | [x] | Complete (expiry reminders = due list; notifications pending Phase 3 notification work) |
| Employee documents | [x] | [x] | [x] | [x] | [x] | Complete |
| HR audit trail | [x] (shared `audit_logs`) | [x] | [x] | [x] | [x] | Complete |
| Self-service profile | — | [x] | [x] | [x] | [x] | Partial (read-only profile only) |
| Branch assignment history & transfers | [x] | [x] | [x] | [x] | [x] | Complete |
| Secondary / temporary assignments | [x] | [x] | [x] | [x] | [x] | Complete |
| Extra HR branch scope per user | [x] | [x] | [x] | [x] | [x] | Complete |
| Branch workforce summary | — | [x] | [x] | [x] | [x] | Complete (headcount; cost/attendance metrics come with later phases) |
| Shift templates & scheduling policy | [x] | [x] | [x] | [x] | [x] | Complete |
| Schedule board, validation, publishing | [x] | [x] | [x] | [x] | [x] | Complete (leave conflicts added in Phase 5) |
| Availability | [x] | [x] | [x] | [x] | [x] | Complete |
| Shift swaps | [x] | [x] | [x] | [x] | [x] | Complete |
| Schedule notifications | — | [x] (push) | — | — | [~] | Sent through existing push; delivery itself not asserted in tests |
| Scheduling … analytics (Phases 3–11) | [ ] | [ ] | [ ] | [ ] | [ ] | Not started |

## Git History

| Branch | Commit | Message | Date | Feature |
|---|---|---|---|---|
| `feature/restaurant-hr-management` | `d028ed6` | docs(hr): document existing architecture and HR integration analysis | 2026-11-09 | Phase 0 |
| `feature/hr-employee-management` | `c23471a` | feat(hr): add employee registry, org structure, contracts and documents | 2026-11-09 | Phase 1 backend |
| `feature/hr-employee-management` | `add23e3` | feat(hr): add HR web screens for employees, organisation and access | 2026-11-09 | Phase 1 web |
| `feature/hr-employee-management` | `867d2f8` | docs(hr): record Phase 1 progress and technical documentation | 2026-11-09 | Phase 1 docs |
| `feature/restaurant-hr-management` | `3a23c86` | Merge of `feature/hr-employee-management` (no fast-forward) | 2026-11-09 | Phase 1 integration |
| `feature/restaurant-hr-management` | `a068f60` | docs(hr): record Phase 1 commit hashes | 2026-11-09 | Phase 1 docs |
| `feature/hr-multi-branch` | `5d9e484` | feat(hr): add multi-branch assignments, transfers and branch scope | 2026-11-09 | Phase 2 backend |
| `feature/hr-multi-branch` | `1baa588` | feat(hr): add branch history, transfers and branch workforce screens | 2026-11-09 | Phase 2 web |
| `feature/hr-multi-branch` | `977adb2` | docs(hr): record Phase 2 multi-branch design, tests and progress | 2026-11-09 | Phase 2 docs |
| `feature/restaurant-hr-management` | `6bb1538` | Merge of `feature/hr-multi-branch` (no fast-forward) | 2026-11-09 | Phase 2 integration |
| `feature/restaurant-hr-management` | `c5ea856` | Merge of `feature/hr-dev-seed` (dev seed tooling) | 2026-11-09 | Dev tooling |
| `feature/hr-shift-scheduling` | `a582612` | feat(hr): add shift scheduling with templates, validation, publishing and swaps | 2026-11-09 | Phase 3 backend |
| `feature/hr-shift-scheduling` | `477bc39` | feat(hr): add shift schedule board and staff self-service schedule | 2026-11-09 | Phase 3 web |
| `feature/hr-shift-scheduling` | `90a6b00` | chore(hr): seed shift templates, schedules, availability and a swap request | 2026-11-09 | Dev tooling |
| `feature/hr-shift-scheduling` | `32a7178` | docs(hr): record Phase 3 scheduling design, tests and progress | 2026-11-09 | Phase 3 docs |
| `feature/restaurant-hr-management` | `9d24358` | Merge of `feature/hr-shift-scheduling` (no fast-forward) | 2026-11-09 | Phase 3 integration |

## Development Log

| Date | Task | Changes | Tests | Branch | Commit |
|---|---|---|---|---|---|
| 2026-11-09 | Baseline | none | Backend `mvn test`: 279 run, 0 failures, 0 errors | `feature/operations-expansion` | `35ef1ed` |
| 2026-11-09 | Phase 0 analysis | `HR_IMPLEMENTATION_ANALYSIS.md`, this file | — | `feature/restaurant-hr-management` | `d028ed6` |
| 2026-11-09 | Phase 1 backend | V27 migration, `hr.*` packages, audit actions, test cleanup, compose volume, postgres-it profile fix | Backend `mvn test`: **306 run, 0 failures, 0 errors** (279 existing + 27 HR) | `feature/hr-employee-management` | `c23471a` |
| 2026-11-09 | V27 on PostgreSQL | Booted the packaged app against a temporary `postgres:16-alpine` container: Flyway applied V1–V27, Hibernate `validate` passed; API smoke test (seeded 7 departments/26 positions, create employee, search, duplicate phone 409, contract create/activate, PDF upload/download with nosniff + attachment, fake PDF rejected 400, audit timeline) | Manual, passed | `feature/hr-employee-management` | `c23471a` |
| 2026-11-09 | Phase 1 web | HR pages, nav, access context, data layer, proxy headers | `vitest`: 48 run, 45 passed, **3 failed — pre-existing** in `guest.test.tsx` (identical failure on clean `HEAD`); all 9 new HR tests pass. ESLint clean on changed files. `next build` passes | `feature/hr-employee-management` | `add23e3` |

| 2026-11-09 | Phase 2 backend | V28, assignment recorder/service/controller, visible-vs-manageable scoping, extra branch scope, workforce summary | Backend `mvn test`: **315 run, 0 failures, 0 errors** (306 + 9 new) | `feature/hr-multi-branch` | `5d9e484` |
| 2026-11-09 | V28 on PostgreSQL | Migrated a temporary Postgres to V27, inserted three employees (active, former, head office), then booted the app: V28 applied, backfill produced an open PRIMARY row, a closed row at the exit date, and none for head office. Smoke-tested transfer, secondary assignment, branch filter, workforce counts. **Found and fixed a PostgreSQL-only 500** (untyped null parameter in the overlap query); after the fix both overlap cases return 409 | Manual, passed after fix | `feature/hr-multi-branch` | `5d9e484` |
| 2026-11-09 | Phase 2 web | Branches tab, read-only covering view, Branch Workforce page, extra branch scope in HR Access | `vitest` HR suite 9/9 pass; ESLint clean on changed files; `next build` passes | `feature/hr-multi-branch` | `1baa588` |

| 2026-11-09 | Dev seed tooling | `seed_hr.py` (+ scheduling data), `seed_local.py` hook, HR docs dir for local backend | Seeded a fresh local backend end to end; scenarios verified via API | `feature/hr-dev-seed` → `feature/hr-shift-scheduling` | `0c34fe9`, `90a6b00` |
| 2026-11-09 | Phase 3 backend | V29, `hr.schedule` package, legacy rota adjustments, login-link attach | Backend `mvn test`: **335 run, 0 failures, 0 errors** (315 + 8 rules + 12 scheduling) | `feature/hr-shift-scheduling` | `a582612` |
| 2026-11-09 | V29 on PostgreSQL | Migrated to V28 with legacy rota rows (linked and unlinked logins), applied V29: rows became PUBLISHED, linked row got its employee. Smoke-tested templates, template-based draft, overlap with migrated shift (409), board/coverage, publish, self shifts, legacy my-shifts/list/timesheet, swap options, swaps list, availability, policy — no errors | Manual, passed | `feature/hr-shift-scheduling` | `a582612` |
| 2026-11-09 | Phase 3 web | Schedule board/day/month, shift dialog, swaps/templates/limits tabs, My Schedule | `vitest` HR suites 14/14; ESLint and `tsc` clean; `next build` passes | `feature/hr-shift-scheduling` | `477bc39` |

## Outstanding Issues

- **Not run — Testcontainers suite:** `HrPostgresFlywayIntegrationTest` (and the pre-existing `PostgresFlywayIntegrationTest`) could not start
  containers in this environment (Docker Engine 29.5 rejects docker-java's default API version; with `DOCKER_API_VERSION=1.44` Docker is
  found but containers still fail to start). V27 was instead verified by booting the real app on PostgreSQL (see log). Run
  `DOCKER_API_VERSION=1.44 mvn -Ppostgres-it test` on a CI/dev machine with working Testcontainers.
- **Pre-existing web test failures:** 3 tests in `web/src/features/guest.test.tsx` fail because `window.localStorage` is undefined in that
  test environment. Not caused by HR; left untouched.
- **Pre-existing build cache issue:** stale `.next/dev/types` from an old dev server broke `next build` type checking; cleared locally (generated, gitignored).
- **Not verified in a browser:** HR web screens are type-checked, linted, unit-tested and build, but were not exercised end-to-end in a running browser session.
- **Pending decision — branch base:** HR is based on `feature/operations-expansion` (11 commits ahead of `main`); that branch must land before HR can be reviewed against `main`.
- **H2 vs PostgreSQL gap:** H2 accepted a query that PostgreSQL rejects (null parameter typing). Until the Testcontainers suite runs in CI,
  each migration/query change is verified manually on PostgreSQL (see log). Treat `(:param is null or ...)` JPQL as forbidden.
- **Leave conflicts:** scheduling warns on `ON_LEAVE` status only; approved leave periods will be checked once Phase 5 adds leave requests.
- **Time zones:** the web board enters and shows times in the browser's local zone; devices should use the restaurant's zone.
- **Notification delivery:** schedule notifications reuse Expo push (after commit, de-duplicated); there is no in-app inbox yet.
- **Scheduled transfers:** transfers cannot be dated in the future (no job applies them yet); record them on the day.
- **Contract reminders:** due/expiring contracts are listed via `GET /hr/contracts/due`; push/email reminders will be added with HR notifications.
- **Known pre-existing:** `WorkforceService` reads `app.time-zone` while `application.yml` defines `app.timezone` (same default, no impact).
- **Data protection:** employee personal data (DOB, address) is visible to holders of `hr.employee.view` in scope. Field-level masking of
  identification numbers will be needed when such fields are added; none are stored yet.

## Next Actions

1. Phase 4 (`feature/hr-attendance`, `feature/hr-pos-integration`): attendance events (idempotent, source, terminal), breaks,
   late/early/overtime calculations against published shifts, corrections with approval, employee clock PIN (hashed,
   lockout) and a POS staff-clock panel that never grants POS privileges or touches order attribution. Extend `time_entries`
   rather than replace it; keep `/timeclock/*` working.
2. Fix the Testcontainers environment or CI job so the `postgres` test group runs on every phase.
