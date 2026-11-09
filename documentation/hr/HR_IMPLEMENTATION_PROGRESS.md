# HR Implementation Progress

## Project Information

| Field | Value |
|---|---|
| Project | Restaurant Manager |
| Module | Human Resource & Workforce Management |
| Integration branch | `feature/restaurant-hr-management` (from `feature/operations-expansion` @ `35ef1ed`) |
| Implementation start | 2026-11-09 |
| Current status | Phase 0 and Phase 1 (employee foundation) complete and merged into the integration branch; Phase 2 not started |
| Last updated | 2026-11-09 |

## Overall Progress

- [x] Repository analysis
- [x] Architecture
- [~] Database design — Phase 1 entities done (V27); later-phase entities not designed in SQL yet
- [x] Employee management (registry, departments, positions, contracts, documents, user linking)
- [~] Multi-branch management — primary branch, branch-scoped access and transfer permission done; secondary/temporary assignments and assignment history not started
- [ ] Shift scheduling (pre-existing basic rota in `workforce` is unchanged)
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
- [~] Self-service — read-only "My HR profile" only
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
| Multi-branch assignments | [ ] | [ ] | [ ] | [~] | [ ] | Not started |
| Scheduling … analytics (Phases 3–11) | [ ] | [ ] | [ ] | [ ] | [ ] | Not started |

## Git History

| Branch | Commit | Message | Date | Feature |
|---|---|---|---|---|
| `feature/restaurant-hr-management` | `d028ed6` | docs(hr): document existing architecture and HR integration analysis | 2026-11-09 | Phase 0 |
| `feature/hr-employee-management` | `c23471a` | feat(hr): add employee registry, org structure, contracts and documents | 2026-11-09 | Phase 1 backend |
| `feature/hr-employee-management` | `add23e3` | feat(hr): add HR web screens for employees, organisation and access | 2026-11-09 | Phase 1 web |
| `feature/hr-employee-management` | see `git log` | docs(hr): record Phase 1 progress and technical documentation | 2026-11-09 | Phase 1 docs |
| `feature/restaurant-hr-management` | see `git log` | Merge of `feature/hr-employee-management` (no fast-forward) | 2026-11-09 | Phase 1 integration |

## Development Log

| Date | Task | Changes | Tests | Branch | Commit |
|---|---|---|---|---|---|
| 2026-11-09 | Baseline | none | Backend `mvn test`: 279 run, 0 failures, 0 errors | `feature/operations-expansion` | `35ef1ed` |
| 2026-11-09 | Phase 0 analysis | `HR_IMPLEMENTATION_ANALYSIS.md`, this file | — | `feature/restaurant-hr-management` | `d028ed6` |
| 2026-11-09 | Phase 1 backend | V27 migration, `hr.*` packages, audit actions, test cleanup, compose volume, postgres-it profile fix | Backend `mvn test`: **306 run, 0 failures, 0 errors** (279 existing + 27 HR) | `feature/hr-employee-management` | `c23471a` |
| 2026-11-09 | V27 on PostgreSQL | Booted the packaged app against a temporary `postgres:16-alpine` container: Flyway applied V1–V27, Hibernate `validate` passed; API smoke test (seeded 7 departments/26 positions, create employee, search, duplicate phone 409, contract create/activate, PDF upload/download with nosniff + attachment, fake PDF rejected 400, audit timeline) | Manual, passed | `feature/hr-employee-management` | `c23471a` |
| 2026-11-09 | Phase 1 web | HR pages, nav, access context, data layer, proxy headers | `vitest`: 48 run, 45 passed, **3 failed — pre-existing** in `guest.test.tsx` (identical failure on clean `HEAD`); all 9 new HR tests pass. ESLint clean on changed files. `next build` passes | `feature/hr-employee-management` | `add23e3` |

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
- **Design limitation:** non-admin branch scope is a single branch (the user's `branch_id`) or all branches via `hr.branch.all`. Managers covering
  exactly two branches need multi-branch user scoping (Phase 2).
- **Contract reminders:** due/expiring contracts are listed via `GET /hr/contracts/due`; push/email reminders will be added with HR notifications.
- **Known pre-existing:** `WorkforceService` reads `app.time-zone` while `application.yml` defines `app.timezone` (same default, no impact).
- **Data protection:** employee personal data (DOB, address) is visible to holders of `hr.employee.view` in scope. Field-level masking of
  identification numbers will be needed when such fields are added; none are stored yet.

## Next Actions

1. Phase 2 (`feature/hr-multi-branch`): `hr_employee_branch_assignments` (secondary/temporary, effective dates, history), transfer
   workflow recording assignment history, scoping by assignment, multi-branch scope for users.
2. Phase 3 (`feature/hr-shift-scheduling`): extend `work_schedules` (publish state, templates, availability, swaps, validation) linking
   shifts to employees, reuse `PushNotificationService` for schedule notifications.
3. Fix the Testcontainers environment or CI job so the `postgres` test group runs on every phase.
