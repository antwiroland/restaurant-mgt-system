# HR Implementation Progress

## Project Information

| Field | Value |
|---|---|
| Project | Restaurant Manager |
| Module | Human Resource & Workforce Management |
| Integration branch | Phases 0–4: `feature/restaurant-hr-management` (from `feature/operations-expansion` @ `35ef1ed`), merged to `main`. From Phase 5 on: one feature branch per phase, each based on the previous one, reviewed into `main` in order |
| Implementation start | 2026-11-09 |
| Current status | Phases 0–9 merged to `main` (2026-11-12): employees, multi-branch, scheduling, attendance and POS clock, timesheets and overtime, leave, payroll, tips and service charge, recruitment with the public careers page, the HR dashboard and reports, editable self-service and the HR-only role. Not started: performance reviews, training and certifications |
| Last updated | 2026-11-12 |

## Overall Progress

- [x] Repository analysis
- [x] Architecture
- [x] Database design — HR migrations V27–V36 (employees to vacancy publishing); performance and training tables not designed yet
- [x] Employee management (registry, departments, positions, contracts, documents, user linking)
- [x] Multi-branch management (assignment history, transfers, secondary/temporary assignments, covering-branch visibility, extra branch scope, branch workforce summary)
- [x] Shift scheduling (templates, validation, drafts/publishing, coverage, availability, swaps, notifications; legacy rota kept compatible)
- [x] Attendance (idempotent events, breaks, late/early/overtime, corrections with approval, policies; legacy time clock kept compatible)
- [x] POS integration (staff clock panel, employee clock PIN with lockout, no effect on POS session or orders)
- [x] Leave (types, effective-dated entitlements, balances with ledger, approval chains, documents, scheduling/timesheet integration)
- [x] Timesheets
- [x] Overtime
- [x] Payroll (rules, runs, payslips, adjustments, four-eyes approval, finalize, payments, self-service payslips, payslip PDF, payout details, bank and Mobile Money payment files)
- [x] Tips (pools from paid orders, sharing rules, approval, paid through payroll)
- [x] Service charges (same engine, separate permission and pools)
- [x] Recruitment (vacancies, candidates, pipeline, interviews and trial shifts, offers, one-step hire, anonymisation)
- [ ] Performance
- [ ] Training
- [x] Self-service — profile with own contact details and emergency contacts editable, own contracts and documents, own schedule, availability, swap requests, own time clock and PIN, own timesheets, own leave, own payslips, own tip and service-charge shares, interviews they run
- [x] Reporting / HR dashboard: dashboard (headcount, movement and turnover, 6-month trend, today's attendance, work waiting, expiries, recruitment, labour cost vs sales) and reports with CSV export (employee register, joiners and leavers, leave, hours and attendance, payroll summary)
- [x] Security — HR permission model, HR role confined to the HR module, branch isolation, private documents, audit, sign-in/permission checks across HR reads, WebSocket connections signed in with per-topic checks
- [ ] Performance optimization / benchmarking
- [x] Regression testing — full backend and web suites run on every phase; latest on `main` 2026-11-12: backend 431, web 103, 0 failures (see Development Log)
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
| Self-service profile | — | [x] | [x] | [x] | [x] | Complete (own contact details and emergency contacts editable; own contracts and documents) |
| HR role (HR-only accounts) | — | [x] | [x] | [x] | [x] | Complete (Phase 9; refused outside HR at the security filter) |
| HR dashboard | — | [x] | [x] | [x] | [x] | Complete (Phase 9; sections follow the viewer's permissions) |
| HR reports and CSV export | — | [x] | [x] | [x] | [x] | Complete (Phase 9; five reports, exports audited, formula-safe CSV) |
| Public careers page | [x] (V36) | [x] | [x] | [x] | [x] | Complete (only published, open vacancies; no pay or applicant data) |
| Branch assignment history & transfers | [x] | [x] | [x] | [x] | [x] | Complete |
| Secondary / temporary assignments | [x] | [x] | [x] | [x] | [x] | Complete |
| Extra HR branch scope per user | [x] | [x] | [x] | [x] | [x] | Complete |
| Branch workforce summary | — | [x] | [x] | [x] | [x] | Complete (headcount; cost/attendance metrics come with later phases) |
| Shift templates & scheduling policy | [x] | [x] | [x] | [x] | [x] | Complete |
| Schedule board, validation, publishing | [x] | [x] | [x] | [x] | [x] | Complete (leave conflicts added in Phase 5) |
| Availability | [x] | [x] | [x] | [x] | [x] | Complete |
| Shift swaps | [x] | [x] | [x] | [x] | [x] | Complete |
| Schedule notifications | — | [x] (push) | — | — | [~] | Sent through existing push; delivery itself not asserted in tests |
| Attendance events & time entries | [x] | [x] | [x] | [x] | [x] | Complete |
| Breaks & attendance policy | [x] | [x] | [x] | [x] | [x] | Complete |
| Corrections with approval | [x] | [x] | [x] | [x] | [x] | Complete |
| POS staff clock & clock PIN | [x] | [x] | [x] | [x] | [x] | Complete (no offline queue: web POS has no offline mode) |
| Timesheets & overtime | [x] | [x] | [x] | [x] | [x] | Complete (commits `59401ea`, `c3300ea`) |
| Leave types & entitlements | [x] | [x] | [x] | [x] | [x] | Complete (no entitlements seeded by design) |
| Leave balances & ledger | [x] | [x] | [x] | [x] | [x] | Complete (carry-over applied when the next year's balance opens) |
| Leave requests & approval chain | [x] | [x] | [x] | [x] | [x] | Complete |
| Leave ↔ scheduling / timesheets / day board | — | [x] | [x] | — | [x] | Complete |
| Payroll rules (deductions/contributions) | [x] | [x] | [x] | [x] | [x] | Complete (no statutory rates seeded by design) |
| Payroll runs, payslips & adjustments | [x] | [x] | [x] | [x] | [x] | Complete |
| Payroll approval, finalize & payment | — | [x] | [x] | [x] | [x] | Complete (payments are recorded, not sent) |
| Tip & service-charge rules | [x] | [x] | [x] | [x] | [x] | Complete (nothing seeded by design) |
| Tip & service-charge pools and shares | [x] | [x] | [x] | [x] | [x] | Complete |
| Shares paid through payroll | [x] | [x] | [x] | — | [x] | Complete (home-branch payroll, reserved once) |
| Vacancies & pipeline | [x] | [x] | [x] | [x] | [x] | Complete |
| Candidates, documents & anonymisation | [x] | [x] | [x] | [x] | [x] | Complete (manual anonymisation; no automatic retention job yet) |
| Interviews, offers & hire | [x] | [x] | [x] | [x] | [x] | Complete (draft contract only for open-ended types) |
| Missed clock-out / late notifications | — | [ ] | — | — | [ ] | Flags shown on the board; push alerts not built yet |
| Performance reviews, training and certifications | [ ] | [ ] | [ ] | [ ] | [ ] | Not started (dashboard and reports delivered in Phase 9) |

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
| `feature/restaurant-hr-management` | `c998a3a` | docs(hr): record Phase 3 commit hashes | 2026-11-09 | Phase 3 docs |
| `feature/hr-attendance` | `16d0654` | feat(hr): add attendance with idempotent clock events, breaks and corrections | 2026-11-09 | Phase 4 backend |
| `feature/hr-attendance` | `953b890` | feat(hr): add attendance board, corrections, rules and self-service time clock | 2026-11-09 | Phase 4 web |
| `feature/hr-pos-integration` | `b41efea` | feat(hr): add POS staff clock panel | 2026-11-09 | Phase 4 POS |
| `feature/hr-pos-integration` | `8f19d59` | chore(hr): seed clock PINs, attendance and a pending correction | 2026-11-09 | Dev tooling |
| `feature/hr-pos-integration` | `78ccc19` | docs(hr): record Phase 4 attendance and POS design, tests and progress | 2026-11-09 | Phase 4 docs |
| `feature/restaurant-hr-management` | `d0bddf2` | Merge of `feature/hr-attendance` (no fast-forward) | 2026-11-09 | Phase 4 integration |
| `feature/restaurant-hr-management` | `6dde03f` | Merge of `feature/hr-pos-integration` (no fast-forward) | 2026-11-09 | Phase 4 integration |
| `feature/hr-timesheets-overtime` | `59401ea` | feat(hr): add timesheets with approval workflow and effective-dated overtime rules | 2026-11-09 | Phase 5 backend |
| `feature/hr-timesheets-overtime` | `c3300ea` | feat(hr): add timesheet review screens and overtime settings | 2026-11-09 | Phase 5 web |
| `feature/hr-leave-management` | `3c71f5f` | feat: Leave Management (V32 schema and enums only) | 2026-11-09 | Phase 5 leave schema |
| `main` | `7fc0723` | Merge PR #4 (`feature/hr-leave-management`) | 2026-11-09 | Phase 5 integration |
| `feature/hr-leave-management` | `5cc6c91` | feat(hr): add leave management with balances, approval chains and scheduling conflicts | 2026-11-12 | Phase 5 leave backend |
| `feature/hr-leave-management` | `b28f1a0` | feat(hr): add leave screens for managers, staff and employee profiles | 2026-11-12 | Phase 5 leave web |
| `feature/hr-leave-management` | `92319d6` | docs(hr): record Phase 5 timesheets and leave design, tests and progress | 2026-11-12 | Phase 5 docs |
| `feature/hr-leave-management` | `40d2812` | chore(hr): seed leave entitlements, balances and requests | 2026-11-12 | Dev tooling |
| `feature/hr-payroll` | `d288375` | feat(hr): add payroll runs, payslips and configurable deduction rules | 2026-11-12 | Phase 6 backend |
| `feature/hr-payroll` | `a958633` | feat(hr): add payroll screens and self-service payslips | 2026-11-12 | Phase 6 web |
| `feature/hr-payroll` | `feda47f` | docs(hr): record Phase 6 payroll design, tests and progress | 2026-11-12 | Phase 6 docs |
| `feature/hr-tips` | `fe32270` | feat(hr): add tips and service charge pools paid through payroll | 2026-11-12 | Phase 7 backend |
| `feature/hr-tips` | `9b3fec0` | feat(hr): add tips and service charge screens | 2026-11-12 | Phase 7 web |
| `feature/hr-tips` | `64c464f` | docs(hr): record Phase 7 tips and service charge design, tests and progress | 2026-11-12 | Phase 7 docs |
| `feature/hr-recruitment` | `27858d4` | feat(hr): add recruitment with vacancies, hiring pipeline, interviews and one-step hire | 2026-11-12 | Phase 8 backend |
| `feature/hr-recruitment` | `a8e4afd` | feat(hr): add recruitment screens | 2026-11-12 | Phase 8 web |
| `feature/hr-recruitment` | `be1b2e5` | docs(hr): record Phase 8 recruitment design, tests and progress | 2026-11-12 | Phase 8 docs |
| `main` | `92588ce` | Merge PR #5 (`feature/hr-recruitment`) | 2026-11-12 | Phases 5–8 integration |
| `chore/seed-pay-recruitment-postgres` | `b6c2dbb` | chore(dev): seed payroll, tips and recruitment; run and seed on Docker Postgres | 2026-11-12 | Dev tooling |
| `feature/public-careers` | `a2de80f` | feat(hr): public careers page with published vacancies | 2026-11-12 | Careers |
| `feature/public-careers` | `76d4f6c` | chore(dev): seed published, internal and filled vacancies for the careers page | 2026-11-12 | Dev tooling |
| `feature/hr-dashboard-self-service-role` | `43b85d0` | feat(hr): HR dashboard, editable self-service and an HR-only role | 2026-11-12 | Phase 9 |
| `feature/hr-dashboard-self-service-role` | `3471a5a` | fix(hr): return 403 (not 401) when the HR role is refused; count ON_SHIFT on the dashboard | 2026-11-12 | Phase 9 fix |
| `feature/hr-dashboard-self-service-role` | `70f67ed` | feat(hr): HR reports with on-screen tables and CSV export | 2026-11-12 | Phase 9 reports |
| `feature/hr-dashboard-self-service-role` | `7561ca5` | docs(hr): record Phase 9 reports, dashboard, self-service, HR role and careers | 2026-11-12 | Phase 9 docs |
| `main` | `cdd6fc7` | Merge branch `chore/seed-pay-recruitment-postgres` | 2026-11-12 | Dev tooling integration |
| `main` | `95a1e52` | Merge branch `feature/public-careers` | 2026-11-12 | Careers integration |
| `main` | `4d19bc9` | Merge branch `feature/hr-dashboard-self-service-role` | 2026-11-12 | Phase 9 integration |

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

| 2026-11-09 | Phase 4 backend | V30, `hr.attendance` package, legacy time clock adjustments, `hrclock` rate-limit bucket | Backend `mvn test`: **354 run, 0 failures, 0 errors** (335 + 8 unit + 11 attendance). One real bug found by the tests and fixed (self PIN change after a failed attempt hit an optimistic-lock conflict) | `feature/hr-attendance` | `16d0654` |
| 2026-11-09 | V30 on PostgreSQL | Migrated to V29 with legacy time entries (linked closed, unlinked open), applied V30: linked row got its employee, unlinked open row untouched. Smoke-tested PIN set, roster, POS clock-in (unscheduled flag), idempotent replay, meal break, clock-out, wrong PIN 401, day board, entries, correction (originals kept), corrections list, legacy timesheet/status, policy — no errors. Found that unlinked legacy entries appeared as nameless rows on the HR day board; fixed by excluding them from HR views | Manual, passed after fix | `feature/hr-attendance` | `16d0654` |
| 2026-11-12 | Phase 5 leave backend | `hr.leave` package on V32 (no new migration), `LEAVE_SUPPORT` document type, leave rule in `ScheduleRules`, leave lines on timesheets, `ON_LEAVE` on the day board, `HrDocumentService.store` extracted | Backend `mvn test`: **384 run, 0 failures, 0 errors**; leave suites 10 + 5 + 1 new | `feature/hr-leave-management` | `5cc6c91` |
| 2026-11-12 | V32 on PostgreSQL | Booted the packaged app on a temporary `postgres:16-alpine`: Flyway applied V1–V32, Hibernate `validate` passed, seven leave types seeded. API smoke test (entitlement, employee + login link, quote, request, overlap 409, awaiting-me, employee/branch lists, calendar, approve, balance and ledger, shift on approved leave 409, HR adjustment, negative-allowed sick leave, cancel): **28/28 passed** | Manual, passed | `feature/hr-leave-management` | `5cc6c91` |
| 2026-11-12 | Phase 5 leave web | `/hr/leave`, `/hr/my-leave`, profile Leave tab, leave API client, nav entries, `LEAVE` timesheet flag | `vitest` HR suites **25/25** (4 new leave + 1 nav); full web suite 61/64 with the 3 pre-existing `guest.test.tsx` failures; `tsc` and ESLint clean; `next build` passes | `feature/hr-leave-management` | `b28f1a0` |
| 2026-11-12 | Leave seed data | `seed_hr.py`: leave types when missing (H2 without Flyway), test entitlements, an HR adjustment, pending/approved/two-step/self-service sick requests | Seeded a fresh local H2 backend end to end; 4 requests in the expected states, approved leave reports its 2 clashing draft shifts | `feature/hr-leave-management` | `40d2812` |
| 2026-11-12 | Local run | `run-backend-local.sh` on a fresh H2 database, `seed_local.py` (restaurant data + HR up to leave), `run-web-local.sh` with the existing build | Backend started and seeded without errors; web `/login` 200, unauthenticated API call through the web proxy 401 (as expected). Payroll, tips and recruitment start empty (not seeded yet) | `feature/hr-recruitment` | `be1b2e5` |
| 2026-11-12 | Phase 8 recruitment backend | V35, `hr.recruitment` package, `HrDocumentService.storeFile/attach` split, recruitment in MANAGER defaults, `HR_RECRUITMENT_CHANGED` | Backend `mvn test`: **408 run, 0 failures, 0 errors** (402 + 2 stage + 4 recruitment) | `feature/hr-recruitment` | `27858d4` |
| 2026-11-12 | V35 on PostgreSQL | Booted the packaged app on a temporary `postgres:16-alpine`: Flyway V1–V35, Hibernate `validate` passed. Smoke test: vacancy with pay range hidden from the manager, new candidate, duplicate by email in another case refused, search, multipart CV upload, trial shift moving the stage, interviewer recording the result, offer with pay by HR, acceptance, hire creating employee + draft contract + CV copy, vacancy filled, anonymisation with the employee's copy still downloadable: **36/36 passed** | Manual, passed | `feature/hr-recruitment` | `27858d4` |
| 2026-11-12 | Phase 8 recruitment web | `/hr/recruitment` (vacancies, pipeline drawer, application drawer, candidates), interviews on My Schedule, nav entry | `vitest` HR suites **38/38**; `tsc` and ESLint clean; `next build` passes (a first build attempt reported 3 Turbopack errors whose text was lost to an output filter; an immediate re-run without changes compiled cleanly) | `feature/hr-recruitment` | `a8e4afd` |
| 2026-11-12 | Phase 7 tips backend | V34, `hr.tips` package, payroll earning lines for approved shares, `HR_TIPS_CHANGED` | Backend `mvn test`: **402 run, 0 failures, 0 errors** (394 + 5 calculator + 3 tips) | `feature/hr-tips` | `fe32270` |
| 2026-11-12 | V34 on PostgreSQL | Booted the packaged app on a temporary `postgres:16-alpine`: Flyway V1–V34, Hibernate `validate` passed. Smoke test with SQL-inserted orders, payments (one fully refunded) and time entries: tips pool collected 100 from 2 orders, house 10 %, shares 60/30 by hours; service-charge pool on the default rule; calculator cannot approve; payroll gross includes the shares; pool locked while payroll uses it; pool paid after payroll finalized: **37/37 passed** | Manual, passed | `feature/hr-tips` | `fe32270` |
| 2026-11-12 | Phase 7 tips web | `/hr/tips` (pools, pool drawer, sharing rules with position points), tips on My Payslips, nav entry | `vitest` HR suites **34/34**; `tsc` and ESLint clean; `next build` passes | `feature/hr-tips` | `9b3fec0` |
| 2026-11-12 | Phase 6 payroll backend | V33, `hr.payroll` package, `payroll_run_id` on timesheets, `HR_PAYROLL_CHANGED` | Backend `mvn test`: **394 run, 0 failures, 0 errors** (384 + 6 calculator + 4 payroll) | `feature/hr-payroll` | `d288375` |
| 2026-11-12 | V33 on PostgreSQL | Booted the packaged app on a temporary `postgres:16-alpine`: Flyway V1–V33, Hibernate `validate` passed. Smoke test (grant, employee + contract, percent/bracket/employer rules with employment-type limit, run, overlap 409, calculate with expected figures, adjustment + recalculate, calculator cannot approve, approve, finalize, employee payslip and lines, pay): **31/31 passed**. Found and fixed run numbers rendered as `PAY--000001` | Manual, passed after fix | `feature/hr-payroll` | `d288375` |
| 2026-11-12 | Phase 6 payroll web | `/hr/payroll` (runs, run drawer, payslip drawer, rules), `/hr/my-payslips`, nav entries | `vitest` HR suites **30/30**; `tsc` and ESLint clean; `next build` passes | `feature/hr-payroll` | `a958633` |
| 2026-11-12 | Dev seed on PostgreSQL | `run-backend-local.sh DB=postgres` (compose Postgres + Redis, Flyway); `seed_local.py --pg` (direct SQL through `docker exec psql`); seed of attendance history, timesheets, payroll rules and runs, tip pools and recruitment | Full seed on an empty Docker PostgreSQL database: Osu October payroll paid (10 payslips), East Legon run blocked by an unlocked timesheet, pools approved, a hire completed | `chore/seed-pay-recruitment-postgres` | `b6c2dbb` |
| 2026-11-12 | Public careers | `GET /careers`, `/careers/{id}` (no sign-in), V36 `published`/`published_at`, publish/unpublish with auto-unpublish on hold/close/cancel/fill, website Careers tab and job page, staff publish controls | Backend `mvn test`: **409 run, 0 failures**; web **82 passed**; live check: only published open jobs listed, no pay fields | `feature/public-careers` | `a2de80f` |
| 2026-11-12 | Phase 9 dashboard, self-service, HR role | `Role.HR` with `HR_DEFAULTS`, security allow-list for the role, `GET /hr/dashboard`, `PUT /hr/me/profile`, `GET /hr/me/contracts`, `GET /hr/me/documents`, web `/hr`, My HR profile edits | Backend `mvn test`: **416 run, 0 failures** (+7 new); web **87 passed**. Live on Docker PostgreSQL: HR account 403 on POS/tables/KDS/inventory, 401 signed out, dashboard figures match seed | `feature/hr-dashboard-self-service-role` | `43b85d0` |
| 2026-11-12 | Phase 9 fix | Error dispatches allowed through security (a filter-level 403 had become 401 on the real server, not under MockMvc); Today tiles use the board's statuses | Backend 416 / web 87 passed; live re-check 403 | `feature/hr-dashboard-self-service-role` | `3471a5a` |
| 2026-11-12 | Phase 9 reports | `GET /hr/reports`, `/hr/reports/{type}`, `/hr/reports/{type}/csv`, `HR_REPORT_EXPORTED`, web `/hr/reports` | Backend `mvn test`: **418 run, 0 failures** (+1 integration, +1 CSV unit); web **88 passed**; `tsc` and ESLint clean | `feature/hr-dashboard-self-service-role` | `70f67ed` |
| 2026-11-12 | Phase 9 merged to `main` | `chore/seed-pay-recruitment-postgres`, `feature/public-careers`, `feature/hr-dashboard-self-service-role` merged in order (one footer conflict resolved: Careers link kept); V35, V36 then V37 apply in order on PostgreSQL | Backend `mvn test` on the merged `main`: **422 run, 0 failures**; web **94 passed**; later front-of-house work on `main`: backend **431**, web **103**, 0 failures | `main` | `4d19bc9` |
| 2026-11-12 | WebSocket sign-in, payslip PDF, payment files | STOMP CONNECT with the access token and per-topic subscription checks (`WebSocketAuthInterceptor`, `WebSocketTopicAuthorizer`); public pickup board ping; web staff screens and mobile send the token. `GET /hr/payslips/{id}/pdf`; payout details (V39) on the employee profile and in self-service; `GET /hr/payroll-runs/{id}/payment-files[/{method}]` | Backend `mvn test`: **438 run, 0 failures** (+3 WebSocket, +4 payroll documents); web **107**; mobile **48**. Live on Docker PostgreSQL: anonymous refused on staff topics, cashier refused on manager alerts, HR refused on floor topics, bad token refused; payslip PDF rendered; V39 applied | `feature/ws-auth-payslip-pdf-payment-files` | — |
| 2026-11-09 | Phase 4 web | Attendance page, PIN card, My time clock; POS staff clock panel | `vitest` HR suites 20/20; ESLint and `tsc` clean; `next build` passes | `feature/hr-attendance`, `feature/hr-pos-integration` | `953b890`, `b41efea` |

## Outstanding Issues

- ~~**WebSocket subscriptions are not authorised (app-wide, found in Phase 9).**~~ Fixed 2026-11-12 on
  `feature/ws-auth-payslip-pdf-payment-files`: STOMP CONNECT takes the access token, every subscription is checked by role and
  ownership, and HR officers get no floor topics (see `documentation/WEBSOCKET_EVENTS.md`).

- **Not run — Testcontainers suite:** `HrPostgresFlywayIntegrationTest` (and the pre-existing `PostgresFlywayIntegrationTest`) could not start
  containers in this environment (Docker Engine 29.5 rejects docker-java's default API version; with `DOCKER_API_VERSION=1.44` Docker is
  found but containers still fail to start). V27 was instead verified by booting the real app on PostgreSQL (see log). Run
  `DOCKER_API_VERSION=1.44 mvn -Ppostgres-it test` on a CI/dev machine with working Testcontainers.
- **Pre-existing web test failures:** 3 tests in `web/src/features/guest.test.tsx` fail because `window.localStorage` is undefined in that
  test environment. Not caused by HR; left untouched.
- **Pre-existing build cache issue:** stale `.next/dev/types` from an old dev server broke `next build` type checking; cleared locally (generated, gitignored).
- **Not verified in a browser:** HR web screens are type-checked, linted, unit-tested and build, and the app has been started locally with seed
  data, but the screens have not been clicked through end-to-end in a browser session.
- **Stacked branches:** Phases 5 (leave) to 8 each build on the previous branch; review and merge them in order (leave → payroll → tips → recruitment) so each PR shows only its own phase.
- **H2 vs PostgreSQL gap:** H2 accepted a query that PostgreSQL rejects (null parameter typing). Until the Testcontainers suite runs in CI,
  each migration/query change is verified manually on PostgreSQL (see log). Treat `(:param is null or ...)` JPQL as forbidden.
- **Attendance alerts:** late arrivals and missed clock-outs are flagged on the board but no push alert is sent yet.
- **Offline POS clocking:** not supported (the web POS has no offline mode); retries with the same key cover short network drops.
- **Rate limiting behind the proxy:** all POS terminals share one `hrclock` bucket because the web proxy does not forward client IPs.
- **Roster query cost:** the POS roster runs a small query per person (break, today's shift); fine for branch-sized rosters,
  revisit if a branch has hundreds of staff.
- **Leave and employment status:** approved leave does not switch `employment_status` to `ON_LEAVE` automatically; scheduling, timesheets
  and the day board use the approved leave periods instead.
- **Leave across a year end:** must be requested as two requests (balances are per calendar year).
- **Partial return from leave:** approved leave can be cancelled as a whole, not shortened; cancel and re-request the shorter period.
- **Carry-over timing:** carry-over is fixed when next year's balance first opens; later changes to last year's balance are not carried.
- **New entitlements and open balances:** a policy added after a year's balance has opened does not change that balance; use an adjustment.
- **Payroll scope:** staff are paid by their home branch only; hours worked at other branches count when they are on the employee's
  own timesheets. One contract per period (the latest covering it); a mid-period pay change needs an adjustment.
- **Payroll periods:** up to 31 days; monthly pro-rating uses the days of the month the period starts in. Only timesheets lying
  fully inside the period are paid.
- **Payroll payments:** recorded only (method and reference); no money is moved by the system. Bank and Mobile Money payment files
  (generic CSV) are exported for upload to the provider; provider-specific formats can be added once a bank or provider is chosen.
- **Payslip documents:** payslips are shown on screen and downloadable as PDF (Helvetica, Latin-1: other characters are replaced).
- **Seed data:** `seed_hr.py` covers every phase up to recruitment (payroll rules and runs, tip and service-charge pools,
  vacancies, candidates, interviews, offers and a hire). Payroll rules are TEST values, not real statutory rates.
- **Tips basis:** an order counts in the period of its first successful payment; partially refunded orders count in full, fully refunded
  orders are left out. Refunds after a pool is approved do not change it (return the pool to draft and recalculate if needed).
- **Tips participants:** based on clocked time at the branch; staff who did not clock (or whose entries are open) get no share.
- **Tips without a contract:** a share is paid only through payroll, so staff without a contract keep an unpaid approved share.
- **Recruitment intake:** published vacancies appear on the public careers page (`/careers`), but applicants are still entered
  by staff; there is no self-apply form yet.
- **Candidate retention:** personal data is removed on demand (anonymise); there is no automatic retention period yet.
- **Hire contracts:** only open-ended offers (permanent, probation, part-time) become a draft contract automatically; fixed-term,
  casual, seasonal and internship contracts need an end date and are created from the employee profile.
- **Interviewer list:** the web picker uses `/scheduling/staff` (managers and admins); others see an empty list.
- **Small branches:** each approval step needs a different person and nobody approves a request they filed; HR can use
  "record as already approved" for leave agreed outside the system.
- **Time zones:** the web board enters and shows times in the browser's local zone; devices should use the restaurant's zone.
- **Notification delivery:** schedule notifications reuse Expo push (after commit, de-duplicated); there is no in-app inbox yet.
- **Scheduled transfers:** transfers cannot be dated in the future (no job applies them yet); record them on the day.
- **Contract reminders:** due/expiring contracts are listed via `GET /hr/contracts/due`; push/email reminders will be added with HR notifications.
- **Known pre-existing:** `WorkforceService` reads `app.time-zone` while `application.yml` defines `app.timezone` (same default, no impact).
- **Data protection:** employee personal data (DOB, address) is visible to holders of `hr.employee.view` in scope. Field-level masking of
  identification numbers will be needed when such fields are added; none are stored yet.

## Next Actions

1. ~~Authorise WebSocket connections and subscriptions~~ Done 2026-11-12.
2. ~~Payroll follow-ups: payslip PDF, bank/mobile-money payment file export~~ Done 2026-11-12 (generic CSV; provider-specific formats when a provider is chosen).
3. Next phases: performance reviews, training and certifications; online applications from the careers page; an edit screen for vacancy text.
4. Fix the Testcontainers environment or CI job so the `postgres` test group runs on every phase.
