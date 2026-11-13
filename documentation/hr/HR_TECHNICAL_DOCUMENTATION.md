# HR Technical Documentation

Describes only what is implemented. Sections for later phases are added as each phase is delivered.

## 1. Architecture and module boundaries

- Backend package: `com.restaurantmanager.core.hr`
  - `hr.access` — `HrPermission`, `HrAccessService` (permission + branch scope), per-user grants, `/hr/access/**`
  - `hr.org` — departments and positions
  - `hr.employee` — employee registry, emergency contacts, user-account link, self-profile
  - `hr.contract` — employment contracts and renewals
  - `hr.document` — private employee document store
  - `hr.schedule` — shift templates, scheduling policies, `ScheduleRules` (pure validation), board/publish
    (`ScheduleService`), availability, shift swaps, notifications (`ScheduleNotifier`)
  - `hr.attendance` — clock events (`AttendanceService`, `ClockGateway`), POS staff clock (`PosClockService`), clock PINs
    (`ClockPinService`), breaks, corrections and policies (`AttendanceManagementService`, `AttendancePolicyService`),
    pure calculations (`AttendanceCalculator`)
  - `hr.timesheet` — timesheets, overtime rules, public holidays, overtime pre-authorisation
  - `hr.leave` — leave types and entitlements (`LeaveSetupService`), balances, requests and approvals (`LeaveService`),
    pure arithmetic (`LeaveCalculator`), read-only lookups for scheduling/timesheets (`LeaveLookup`), push (`LeaveNotifier`)
  - `hr.payroll` — deduction rules (`PayrollRuleService`), runs/payslips/adjustments (`PayrollService`), pure arithmetic
    (`PayrollCalculator`)
  - `hr.tips` — tips and service-charge sharing rules, pools and shares (`TipService`), pure sharing arithmetic (`TipCalculator`)
  - `hr.recruitment` — vacancies, pipeline, interviews, offers and hiring (`RecruitmentService`), candidates, documents and
    anonymisation (`CandidateService`); stage rules in `ApplicationStage`
  - `hr.branch` — branch assignment history (`BranchAssignmentRecorder` keeps it consistent), secondary/temporary
    assignments, transfers, branch workforce summary
  - `hr.common` — `HrSequenceService` (gap-tolerant sequential numbers), shared helpers
- HR never writes to POS, order, payment or financial tables.
- REST prefix: `/hr/**` (web reaches it through `/api/rm/hr/**`).

## 2. Access model

### 2.1 Permissions

Defined in `HrPermission` (code → meaning). Effective permissions = role defaults ∪ per-user grants (`hr_user_permissions`).

| Code | Meaning | ADMIN | MANAGER default |
|---|---|:-:|:-:|
| `hr.employee.view` | View employees in scope | ✓ | ✓ |
| `hr.employee.create` | Register employees | ✓ | ✓ |
| `hr.employee.update` | Edit employee records | ✓ | ✓ |
| `hr.employee.archive` | Change employment status to an exit status | ✓ | |
| `hr.employee.transfer` | Change primary branch | ✓ | |
| `hr.department.manage` | Departments | ✓ | |
| `hr.position.manage` | Positions | ✓ | |
| `hr.contract.view` | View contracts (salary masked without compensation) | ✓ | ✓ |
| `hr.contract.manage` | Create / renew / end contracts | ✓ | |
| `hr.document.view` | View and download employee documents | ✓ | ✓ |
| `hr.document.manage` | Upload / archive documents | ✓ | |
| `hr.compensation.view` | See salary figures | ✓ | |
| `hr.branch.all` | Access every branch (central HR) | ✓ (implicit) | |
| `hr.recruitment.manage` | Vacancies, candidates and hiring (branch-scoped) | ✓ | ✓ |
| `hr.settings.manage` | HR settings | ✓ | |

Further codes (`hr.schedule.*`, `hr.attendance.*`, `hr.payroll.*`, ...) are declared now so grants are stable, and are
enforced as their phases are implemented.

Grants can only be managed by ADMIN (`/hr/access/grants`), and every grant/revoke is audited. CUSTOMER accounts can never
receive HR permissions.

**HR role (Phase 9).** `Role.HR` gets `HrPermission.HR_DEFAULTS`: every HR permission except `hr.branch.all`. Branch reach follows the
account instead: an HR login without a branch is central HR (all branches); with a branch it covers that branch plus any extra
`hr_user_branch_access`. Assigning the HR role with no branch clears the account's branch. Four-eyes rules still apply to HR officers,
and only ADMIN grants HR permissions. `SecurityConfig` confines the role: `/hr/**`, `/auth/**`, `/users/me`,
`/notifications/devices/**`, `GET /branches` and `GET /scheduling/staff` are allowed; every other request falls to
`anyRequest().access(authenticatedOutsideHrRole)` and is refused with 403. On the web, `canAccessPath` refuses every route outside
`/hr` and `/account` for the role, and sign-in lands on `/hr`. Leave requests reaching the HR approval step also notify HR-role
accounts (central, or of the employee's branch).

### 2.2 Branch scope

`HrAccessService.scope(principal)`:

- ADMIN, holder of `hr.branch.all`, or an HR-role account without a branch → all branches.
- Otherwise → the user's own `branch_id` plus any extra branches an admin added (`hr_user_branch_access`); none → no
  branch-specific data.

Two levels of employee access (both return **404** when out of scope, so existence is not leaked):

| Level | Rule | Used for |
|---|---|---|
| Visible (`loadVisible`) | primary branch in scope, **or** an active secondary/temporary assignment at a branch in scope | directory, profile, assignment history |
| Manageable (`loadInScope`) | primary branch in scope | edits, status, login link, contracts, documents, audit history, assignments |

Employees with no primary branch are visible to all-branch users only. `EmployeeDetail.manageable` tells the client which level applies.

### 2.3 Self-service

Any staff user linked to an employee record can read their own profile (`GET /hr/me/profile`) and their own access summary
(`GET /hr/me/access`). Self-service cannot modify employment data.

Phase 9 adds, for the linked employee only (anything else is 404):

| Endpoint | What |
|---|---|
| `PUT /hr/me/profile` | Own phone, email, address and up to 5 emergency contacts. Same uniqueness and format rules as HR edits, optimistic `version`, refused after an exit. Audited as `HR_EMPLOYEE_UPDATED` with `selfService: true` and the changed field names. |
| `GET /hr/me/contracts` | Own contracts except drafts, including their own pay (no `hr.compensation.view` needed for oneself). |
| `GET /hr/me/documents`, `GET /hr/me/documents/{id}/download` | Own current (non-archived) documents; downloads audited as `HR_DOCUMENT_DOWNLOADED` with `selfService: true`. |

## 3. Database entities (Phase 1, migration `V27__hr_foundation.sql`)

| Table | Purpose / key constraints |
|---|---|
| `hr_sequences` | `name` PK, `next_value`; row-locked counter for human-readable numbers |
| `hr_departments` | `code` unique, `category`, optional `branch_id`, optional `head_employee_id`, `active` (archive instead of delete) |
| `hr_positions` | `code` unique, `department_id`, `category`, `description`, `requirements`, `active` |
| `hr_employees` | `employee_number` unique, `user_id` unique nullable → `users`, names, contact, `department_id`, `position_id`, `employment_type`, `employment_status`, `hire_date`, `exit_date`, `reporting_manager_id`, `primary_branch_id`, `version` (optimistic lock) |
| `hr_emergency_contacts` | Child of employee (cascade) |
| `hr_employment_contracts` | `contract_number` unique, `employee_id`, type, dates, `salary_type`, `salary_amount NUMERIC(14,2)`, `currency`, `weekly_hours`, `probation_end_date`, `status`, `previous_contract_id` (renewal chain), `document_id` |
| `hr_employee_documents` | Metadata only; bytes in private storage. `storage_key`, `sha256`, `content_type`, `size_bytes`, `expires_on`, `archived` |
| `hr_user_permissions` | (`user_id`, `permission`) PK, `granted_by`, `granted_at` |

Departments and positions referenced by employees cannot be deleted; they are archived (`active = false`).

Default departments/positions from the restaurant category list are seeded by the migration as editable configuration.

### Phase 2 (`V28__hr_branch_assignments.sql`)

| Table | Purpose / key constraints |
|---|---|
| `hr_employee_branch_assignments` | `employee_id`, `branch_id`, `assignment_type` (PRIMARY/SECONDARY/TEMPORARY), optional branch-specific `position_id`, `start_date`, `end_date` (required for TEMPORARY), reasons, actors. Partial unique indexes: one open PRIMARY per employee; one open row per employee and branch. Rows are closed, never deleted. Backfilled with a PRIMARY row per existing employee (hire date → exit date). |
| `hr_user_branch_access` | (`user_id`, `branch_id`) extra HR branch scope for non-admin staff accounts |

`hr_employees.primary_branch_id` remains the current home branch; the assignment table is its history.

## 4. API endpoints (Phase 1)

| Method & path | Permission |
|---|---|
| `GET /hr/me/access` | any staff |
| `GET /hr/me/profile` | linked employee |
| `GET /hr/access/permissions` | ADMIN |
| `GET /hr/access/grants?userId=` / `PUT /hr/access/grants/{userId}` | ADMIN |
| `GET /hr/departments`, `GET /hr/positions` | `hr.employee.view` or manage permission |
| `POST/PUT /hr/departments[/{id}]`, `POST /hr/departments/{id}/archive|restore` | `hr.department.manage` |
| `POST/PUT /hr/positions[/{id}]`, `POST /hr/positions/{id}/archive|restore` | `hr.position.manage` |
| `GET /hr/employees?q=&branchId=&departmentId=&positionId=&status=&type=&page=&size=&sort=` | `hr.employee.view` (scoped) |
| `GET /hr/employees/{id}` | `hr.employee.view` (scoped) |
| `POST /hr/employees` / `PUT /hr/employees/{id}` | `hr.employee.create` / `hr.employee.update` |
| `POST /hr/employees/{id}/status` | `hr.employee.update`; exit statuses need `hr.employee.archive` |
| `POST /hr/employees/{id}/link-user` / `DELETE .../link-user` | `hr.employee.update` |
| `GET /hr/employees/linkable-users` | `hr.employee.create` |
| `POST /hr/employees/import-users` (body: `employmentType`, `hireDate`) | `hr.employee.create`; only staff accounts in the caller's branches |
| `GET /hr/employees/{id}/contracts`, `GET /hr/contracts/due?days=` (end or probation date within N days, plus overdue) | `hr.contract.view` |
| `POST /hr/employees/{id}/contracts`, `PUT /hr/contracts/{id}`, `POST /hr/contracts/{id}/activate|renew|end` | `hr.contract.manage` |
| `GET /hr/employees/{id}/documents`, `GET /hr/documents/{id}/download` | `hr.document.view` |
| `POST /hr/employees/{id}/documents` (multipart), `POST /hr/documents/{id}/archive` | `hr.document.manage` |
| `GET /hr/employees/{id}/audit` | `hr.employee.view` (manageable) |
| `GET /hr/employees/{id}/assignments` | `hr.employee.view` (visible) |
| `POST /hr/employees/{id}/assignments` (`branchId`, `assignmentType` SECONDARY/TEMPORARY, `positionId`, `startDate`, `endDate`, `reason`) | `hr.branch.assign`; employee manageable and target branch in scope |
| `POST /hr/assignments/{id}/end` (`endDate`, `reason`) | `hr.branch.assign` |
| `POST /hr/employees/{id}/transfer` (`toBranchId`, `effectiveDate`, `reason`) | `hr.employee.update` + `hr.employee.transfer`; target branch in scope |
| `GET /hr/branches/workforce` | `hr.branch.view` or `hr.employee.view` |
| `PUT /hr/access/branches/{userId}` (`branchIds`) | ADMIN |

Paged responses: `{ "items": [...], "page": 0, "size": 25, "total": 123 }`.

## 5. Business rules (Phase 1)

- Employee identity: at most one employee per user account (unique `user_id`); duplicate email or phone among employees
  is rejected with 409; CUSTOMER accounts cannot be linked.
- Employee number auto-generated as `EMP-000001` when not supplied.
- Status changes to RESIGNED/TERMINATED/RETIRED require `hr.employee.archive` and set `exit_date`; the linked user account
  is **not** deactivated automatically (that stays an explicit Staff & Users action).
- Contracts: DRAFT → ACTIVE → (EXPIRED | ENDED | RENEWED). Renewal creates a new DRAFT contract linked to the previous one;
  activating it marks the previous one RENEWED. Only one ACTIVE contract per employee. Contract expiry never changes the
  employee's status.
- Documents: PDF, PNG, JPEG, DOCX only, verified by magic bytes; ≤ 5 MB (matches `spring.servlet.multipart.max-file-size`);
  SHA-256 stored; download streams with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`; every download audited.

### Multi-branch rules (Phase 2)

- New and imported employees get a PRIMARY assignment starting on the hire date.
- Transfer: effective date ≤ today and ≥ hire date. The open PRIMARY row ends the day before; a new one starts on the date.
  A transfer dated on the start day of the open PRIMARY row corrects that row (fixes a wrong branch at hire) instead of
  creating a one-day assignment. Changing the branch on the edit form is a transfer effective today.
- An existing secondary assignment at the new home branch is closed when it becomes the primary branch.
- Secondary/temporary: not at the primary branch, no overlap with another assignment at the same branch, not before hire
  date; temporary needs an end date. Ending can shorten but not extend (create a new assignment to extend).
- Exit closes every open assignment at the exit date; re-hire opens a new PRIMARY from the re-hire date.
- Branch workforce: per branch in scope, counts by status of employees whose home branch it is, plus distinct employees
  covering it via an active secondary/temporary assignment. Central users also see a "No branch" row.

## 5b. Shift scheduling (Phase 3, `V29__hr_shift_scheduling.sql`)

### Data

Scheduling extends the existing rota table `work_schedules` instead of adding a parallel table:

| Column added | Meaning |
|---|---|
| `employee_id` | HR employee (nullable = open shift). `user_id` is now nullable and kept in sync with the employee's login. |
| `status` | `DRAFT` / `PUBLISHED` / `CANCELLED`. Existing rows were migrated as `PUBLISHED`. |
| `template_id`, `department_id`, `position_id`, `break_minutes` | Shift details (break = unpaid minutes) |
| `published_at/by`, `cancelled_at`, `cancel_reason`, `version` | Lifecycle and optimistic locking |

New tables: `hr_shift_templates` (local start/end time — end ≤ start means overnight —, days, break, min/max staff,
optional branch), `hr_scheduling_policies` (organisation default row with `branch_id NULL`, optional branch override),
`hr_employee_availability` (weekly rule or single date, optional time window, AVAILABLE/UNAVAILABLE/PREFERRED),
`hr_shift_swap_requests` (one pending request per shift, partial unique index).

### Legacy compatibility

`/scheduling/*` and `/timeclock/*` keep their contracts: they only see `PUBLISHED` shifts that have a login, their overlap
check also counts HR drafts, and their new entries are created `PUBLISHED`. Linking a login to an employee (create, link,
import) attaches that login's existing rota rows to the employee so HR overlap checks see them.

### Validation (`ScheduleRules`)

| Code | Severity | Rule |
|---|---|---|
| `OVERLAP` | blocking | Another non-cancelled shift of the employee overlaps (back-to-back is fine) |
| `NOT_AT_BRANCH` | blocking | No assignment (primary/secondary/temporary) at the shift's branch on the shift date |
| `EMPLOYEE_LEFT`, `EMPLOYEE_SUSPENDED` | blocking | Employment status |
| `EMPLOYEE_ON_LEAVE` | warning | Status ON_LEAVE (approved-leave conflicts arrive with Phase 5) |
| `SHORT_REST` | warning | Gap to the nearest shift before/after is below the policy minimum |
| `LONG_SHIFT` | warning | Elapsed length above the policy maximum |
| `WEEKLY_HOURS` | warning | Paid minutes (after unpaid breaks) of shifts starting in the Monday–Sunday week above the policy maximum |
| `UNAVAILABLE` | warning | An UNAVAILABLE rule applies on a date the shift touches and (if timed) overlaps it |

Blocking → HTTP 409 with `issues`; unacknowledged warnings → HTTP 422 with `issues`; the client resubmits with
`acknowledgeWarnings: true` and the acknowledged codes are written to the audit log. Policy resolution: branch row →
organisation row → `hr.scheduling.default-*` configuration (480 / 720 / 2880 minutes by default — business settings, not legal advice).

### Lifecycle

- New shifts are `DRAFT` (invisible to staff and the time clock). Drafts can be deleted; published shifts can only be
  cancelled (reason required; employee notified).
- Publish (`hr.schedule.publish`) covers all drafts at a branch in a date range. Every assigned draft is re-validated; if any
  has a blocking issue nothing is published. Each affected employee with a login gets one push notification.
- Editing a published shift notifies the old and new employee. Coverage compares assigned shifts per template and day with
  `min_staff`/`max_staff`; shifts without an employee count as open.
- Swaps: requester's own published future shift → colleague (handover) or exchange with a colleague's published future
  shift. Both directions are validated; blocking issues reject the request. Approvers need `hr.shift.swap.approve` for the
  branch and may not be the requester or the colleague; approval re-validates and requires acknowledgement of warnings.

### Endpoints

| Method & path | Permission |
|---|---|
| `GET /hr/schedules?branchId&from&to` (≤ 62 days) | `hr.schedule.view` + branch |
| `POST /hr/schedules`, `PUT /hr/schedules/{id}` | `hr.schedule.manage` + branch |
| `POST /hr/schedules/{id}/cancel`, `DELETE /hr/schedules/{id}` (drafts) | `hr.schedule.manage` |
| `POST /hr/schedules/publish` | `hr.schedule.publish` |
| `GET/POST/PUT /hr/shift-templates[/{id}]`, `.../archive`, `.../restore` | view / `hr.schedule.manage` (all-branch templates: central only) |
| `GET /hr/scheduling-policy?branchId`, `PUT /hr/scheduling-policy` | view / `hr.settings.manage` (organisation default: central only) |
| `GET/POST /hr/employees/{id}/availability`, `DELETE /hr/availability/{id}` | `hr.schedule.view` / `hr.schedule.manage` |
| `GET /hr/shift-swaps?status`, `POST /hr/shift-swaps/{id}/decision` | `hr.shift.swap.approve` |
| `GET /hr/me/shifts`, `GET/POST/DELETE /hr/me/availability`, `GET /hr/me/swap-options`, `GET/POST /hr/me/swaps`, `POST /hr/me/swaps/{id}/cancel` | linked employee |

## 5c. Attendance and POS clock-in (Phase 4, `V30__hr_attendance.sql`)

### Data

`time_entries` (the existing time clock) is extended rather than replaced: `employee_id` (staff without a login can clock),
`source` / `clock_out_source` (`POS`, `SELF_SERVICE`, `MANAGER`, `DEVICE`, `CORRECTION`), `terminal_id`, `unscheduled`,
`version`; `user_id` became nullable and is kept equal to the employee's login. A partial unique index allows one open entry
per employee. `break_minutes` keeps meaning *unpaid* break minutes, so the legacy timesheet maths is unchanged.

| Table | Purpose |
|---|---|
| `hr_attendance_events` | Append-only log of every clock action as received: unique `idempotency_key`, type, device time, terminal, branch, entry, `ACCEPTED`/`REJECTED` + message, operator |
| `hr_attendance_breaks` | MEAL / REST / OTHER breaks; `paid` is copied from the policy when the break starts; one open break per entry |
| `hr_attendance_corrections` | Original and corrected clock-in/out/break, reason, `PENDING` / `APPLIED` / `REJECTED`, requester, decider |
| `hr_attendance_policies` | Organisation default row (`branch_id NULL`) or branch override: grace periods, early clock-in window, unscheduled `ALLOW`/`BLOCK`, correction approval, paid break types, self-clock, missed clock-out threshold |
| `hr_employees.clock_pin_*` | BCrypt hash, fail count, lock-until, set-at |

### Clock actions (`AttendanceService`)

1. **Idempotency.** Every action carries a client request key. A known key returns the first outcome (`duplicate: true`), or the
   original refusal; a key reused for another employee or action is refused. Concurrent duplicates that both pass the check are
   stopped by the unique constraints and answered by `ClockGateway` with the stored outcome.
2. **Event time.** A queued device time (`occurredAt`) is accepted if it is at most 12 hours old (7 days for `DEVICE` events,
   which a gateway may hold through an internet outage) and at most 2 minutes in the future (clamped to now); actions may
   not be timed before the entry's last action. A locked timesheet still refuses them.
3. **Clock-in** requires an employable status (not exited/suspended), an assignment at the branch on that date, and no open
   entry. It links the published shift whose window (start − early clock-in minutes … end) contains the time; without one it is
   flagged `unscheduled` or refused per policy.
4. **Breaks** need an open entry; ending a break recomputes unpaid minutes. **Clock-out** ends an open break.
5. Rejected actions are written to the event log in their own transaction, so the trail survives the rollback.

### Calculations (`AttendanceCalculator`)

| Measure | Rule |
|---|---|
| Paid minutes | elapsed − unpaid breaks |
| Late | clock-in after shift start + late grace → minutes since shift start |
| Early leave | clock-out before shift end − early grace → minutes before shift end |
| Overtime (provisional) | paid − scheduled paid (shift length − shift break), only once clocked out; approval comes with timesheets |
| Missed clock-out | still open after shift end + policy minutes, or 16 h after clock-in when unscheduled |
| Day board statuses | `ON_SHIFT`, `ON_BREAK`, `COMPLETED`, `LEFT_EARLY`, `MISSED_CLOCK_OUT`, `UNSCHEDULED`, `ABSENT` (published shift started, no entry), `NOT_STARTED` |

### POS integration and permission separation

- The POS terminal is the staff account signed in to the till (ADMIN, MANAGER or CASHIER); its branch is the terminal branch
  (admins without a branch must name one). Riders cannot use it.
- Employees pick their name from the roster (first name + last initial; active staff assigned today plus anyone still clocked
  in) and enter their own clock PIN. One BCrypt check per attempt — PINs are never scanned or looked up by value.
- Wrong PINs are counted in a separate transaction; after `hr.attendance.pin-max-attempts` (5) the PIN locks for
  `pin-lockout-minutes` (15). `/hr/pos/clock` and `/hr/pos/me` have their own rate-limit bucket (`hrclock`, 60/min).
- A successful clock returns only the attendance record and a message: no token, no role, no change to the signed-in account,
  its permissions or order attribution. The panel is a separate component and separate requests, so attendance failures cannot
  block orders or payments.
- The web POS has no offline mode, so attendance is not queued offline; network failures are retried with the same request key
  (safe because of idempotency). The PIN is held in memory only for the attempt and never written to storage.

### Corrections

Managers with `hr.attendance.correct` correct an entry or add a missing one (both times required). With
`require_correction_approval` (default) the correction waits for a different person with the same permission; otherwise it is
applied at once. Nobody may correct or decide on their own attendance, nor approve their own request. Applying copies the
corrected values onto the entry (overlap with other entries refused, open breaks closed) while the correction row keeps the
originals. The legacy `/timeclock/entries/{id}` editor now also records an applied correction.

### Legacy compatibility

`/timeclock/*` keeps its contract. Its clock-in also refuses when the linked employee already has an open HR entry and respects
`allow_self_clock`; its timesheet ignores entries without a login. Linking a login attaches its earlier time entries to the employee.

### Endpoints

| Method & path | Permission |
|---|---|
| `GET /hr/pos/roster`, `POST /hr/pos/clock`, `POST /hr/pos/me` | POS operator role + employee PIN |
| `GET /hr/attendance/day?branchId&date`, `GET /hr/attendance/entries?branchId&from&to[&employeeId]` | `hr.attendance.view` + branch |
| `POST /hr/attendance/clock` | `hr.attendance.manage` + branch |
| `GET/POST /hr/attendance/corrections`, `POST /hr/attendance/corrections/{id}/decision` | `hr.attendance.correct` (list also `view`) |
| `GET/PUT /hr/attendance-policy` | `hr.attendance.view` / `hr.settings.manage` (organisation default: central only) |
| `GET/PUT/DELETE /hr/employees/{id}/clock-pin` | `hr.attendance.manage` (status also `view`), manageable scope |
| `GET /hr/me/attendance/status`, `GET /hr/me/attendance`, `POST /hr/me/clock`, `GET/PUT /hr/me/clock-pin` | linked employee |

## 5c-bis. Biometric attendance devices (`V41__hr_attendance_devices.sql`, `hr.device`)

Hikvision terminals (others later) on the restaurant LAN are read by the **attendance gateway**, a separate Spring Boot
program (`attendance-gateway/`, Java 21, SQLite queue, see its README). It never touches the database: it signs each request
with its own key and the backend stays the source of truth. Accepted scans become ordinary clock actions (`source DEVICE`,
terminal `device:<serial>`) through `ClockGateway`, so every rule in 5c (branch assignment, shift matching, breaks, missed
clock-out, timesheet locks) applies unchanged.

### Data

| Table | Purpose |
|---|---|
| `hr_attendance_gateways` | Gateway id, name, branch, **SHA-256 of the key** (key shown once, `rmgw_<prefix>_<secret>`), prefix, revoked, last heartbeat / sync, pending and failed counts, last error, version |
| `hr_attendance_devices` | Serial (unique), make (`HIKVISION`/`ZKTECO`/`OTHER`), model, IP, port, HTTPS, gateway, branch, `direction_mode`, active, reported status, last event, clock offset. **No device password**: logins live only on the gateway, encrypted |
| `hr_employee_device_ids` | Device user number to employee, for one device or every device |
| `hr_device_events` | Every delivered event as received (raw JSON, device / gateway / backend timestamps, auth method, reported type), unique on (serial, device event id), with status, message, applied action, linked `hr_attendance_events` row, and the reviewer's decision beside it |

### Processing (`DeviceEventProcessor`)

1. **Idempotent.** (serial, device event id) is processed once; a redelivery is answered with the first outcome
   (`duplicate: true`). The clock request key is derived from the same pair, so a race cannot record it twice.
2. **Direction is never guessed.** The device's attendance status (`checkIn`, `breakOut`, `breakIn`, `checkOut`; overtime
   in/out count as in/out) wins; otherwise the terminal's `direction_mode` (`CLOCK_IN`, `CLOCK_OUT`, `BREAK_OUT`,
   `BREAK_IN`); with `DEVICE_REPORTED` and no status the scan goes to review. A second clock-in is refused by the rules and
   shown as `REJECTED`; it never becomes a clock-out.
3. **Device time** is the time of the action. A device clock more than 2 minutes ahead sends the scan to review.
4. **Double scans**: the same action within 5 minutes of an accepted one is `REPEAT_SCAN` (ignored).
5. Outcomes: `ACCEPTED`, `REPEAT_SCAN`, `NEEDS_REVIEW` (no direction, unregistered/inactive device, clock ahead),
   `UNMATCHED` (device user not linked), `REJECTED` (attendance rules refused, with the reason), then `RESOLVED` /
   `DISMISSED` after review. Device breaks are meal breaks (paid or not per policy).
6. Not one transaction: a refused clock action is recorded by the attendance service in its own transaction; the device
   event is stored afterwards with the outcome.

### Review and corrections

Reviewers (`hr.attendance.correct`, branch-scoped by the device's branch; scans from unregistered devices: central HR)
can **record** a scan as a chosen action (at the device time, with the reviewer as `recorded_by`), **try again** (after
linking a person or registering a device; uses a fresh request key), or **not count** it (reason required). The device
event is never edited; decisions are written beside it and audited (`HR_ATTENDANCE_DEVICE_EVENT_RESOLVED`). Corrections of
recorded attendance use the existing correction workflow.

### Monitoring

Gateways heartbeat every minute with pending/failed counts, last error and each device's state and clock offset; the
answer carries the device list, so changes reach the gateway within a minute. The overview flags: a gateway silent for 3
minutes (its devices then show offline), a disconnected device, 50+ events waiting, failed sends, clock drift over 2
minutes, unknown people and scans waiting for review. Configuration changes are audited (`HR_ATTENDANCE_DEVICE_CHANGED`).

### Endpoints

| Endpoint | Access |
|---|---|
| `POST /attendance/gateway/events` (batch up to 200, every event answered), `POST /attendance/gateway/heartbeat`, `GET /attendance/gateway/config`, `GET /attendance/gateway/devices`, `POST /attendance/gateway/sync-status` | Gateway key: `X-Gateway-Key: rmgw_...` or `Authorization: Gateway rmgw_...` (401 when missing, wrong or revoked; 403 if the body names another gateway) |
| `GET /hr/attendance-devices/overview`, `GET .../events?filter=review|all&from&to` | `hr.attendance.view`, branch-scoped |
| `GET/POST .../gateways`, `POST .../gateways/{id}/rotate-key`, `POST .../gateways/{id}/revoke` | `hr.settings.manage` |
| `GET .../devices` (also `hr.attendance.view`), `POST .../devices`, `PUT .../devices/{id}` | `hr.settings.manage` + branch |
| `GET .../device-ids` (also `view`), `POST .../device-ids`, `DELETE .../device-ids/{id}` | `hr.settings.manage`, employee in scope |
| `POST .../events/{id}/apply` `{action, note}`, `POST .../events/{id}/retry`, `POST .../events/{id}/dismiss` `{note}` | `hr.attendance.correct` + branch |

### Web

**HR > Attendance Devices** (`/hr/attendance/devices`, linked from Attendance): Overview (health and problems), Review
(waiting scans with Record as / Try again / Don't count), Devices, People on devices, Gateways (register, key shown once
with setup lines, new key, revoke).

### Tests

`HrAttendanceDeviceIntegrationTest` (full day out of order, redelivery, double scan, second clock-in refused, review and
apply, exit-only reader, unknown person then link and retry, events two days late, clock ahead, keys revoke/rotate,
heartbeat and alerts), `DeviceEventProcessorUnitTest`; gateway: parser, multipart stream, ISAPI client against a fake
Digest-protected device (probe, paged search, alert stream), offline queue and backoff; web `devices.test.tsx`.

## 5d. Timesheets and overtime (Phase 5, `V31__hr_timesheets_overtime.sql`)

Summarised from commits `59401ea` / `c3300ea`. Timesheets are generated per employee and period from time entries and published
shifts as frozen snapshots (`hr_timesheets`, `hr_timesheet_lines`), and move Draft → Submitted → Reviewed → Approved → Locked.
Effective-dated overtime rules (`hr_overtime_rules`), public holidays (`hr_public_holidays`) and overtime pre-authorisation
(`hr_overtime_requests`) feed a pure `TimesheetCalculator`. Days on a non-draft timesheet refuse attendance changes (`TimesheetGuard`).
Endpoints: `/hr/timesheets/**`, `/hr/me/timesheets`, `/hr/overtime-rules`, `/hr/holidays`, `/hr/overtime-requests/**`.

## 5e. Leave (Phase 5, `V32__hr_leave.sql`)

### Data

| Table | Purpose |
|---|---|
| `hr_leave_types` | Organisation-wide: `code`, paid, `deducts_balance`, `requires_document`, `allow_negative`, `count_basis` (`WORKDAYS`/`CALENDAR`), ordered `approval_steps` (`REPORTING_MANAGER`, `BRANCH_MANAGER`, `HR`), optional `max_days_per_request`. Seven types are seeded (annual, sick, maternity, paternity, compassionate, unpaid, study); **no entitlements are seeded**. |
| `hr_leave_policies` | Effective-dated entitlement per type, optionally per branch and/or employment type: `annual_days`, `accrual` (`UPFRONT`/`MONTHLY`), `carry_over_max_days`, `min_service_days`. Never edited; a change is a new row. |
| `hr_leave_balances` | One row per employee, type and year (`entitled`, `carried_over`, `adjustment`, `used`, `pending`, accrual start, policy). Row-locked (`PESSIMISTIC_WRITE`) for every change. |
| `hr_leave_transactions` | Append-only ledger behind each balance: `ENTITLEMENT`, `CARRY_OVER`, `ADJUSTMENT`, `PENDING`, `RELEASE`, `USE`, `RESTORE`. |
| `hr_leave_requests` | Dates (inclusive), half-day flags for the first/last day, counted `days`, status `PENDING`/`APPROVED`/`REJECTED`/`CANCELLED`, the approval chain resolved at request time and `current_step`, optional `document_id`. |
| `hr_leave_approvals` | One decision per step (unique `request_id, step`). |

Supporting documents are stored as employee documents of type `LEAVE_SUPPORT` (private store, same validation as Phase 1).

### Calculations (`LeaveCalculator`, pure)

| Measure | Rule |
|---|---|
| Days | `CALENDAR`: every day. `WORKDAYS`: skips `hr.leave.non-working-days` (default `SAT,SUN`) and public holidays observed at the employee's branch. A half flag makes that end day 0.5; a one-day request with a half flag is 0.5. |
| Entitlement | Policy days pro-rated by months of employment in the year (hire month counts), to the nearest half day. |
| Earned | `UPFRONT`: all of it. `MONTHLY`: equal share at the start of each month from the accrual start month, nearest half day; all of it after the year. |
| Available | earned + carried over + adjustments − used − pending. Checked as of the leave's first day. |
| Carry-over | Unused balance at the end of last year, never negative, capped by the policy. Written when the new year's balance opens. |

Policy resolution (`LeaveSetupService.resolvePolicy`): among policies matching the employee's home branch and employment type, the
most specific (branch + type > branch > type > organisation) in force on 1 January — or on the hire date when hired that year. If
none is in force yet, the first one taking effect later that year applies, so an entitlement configured mid-year covers the current year.

Balances open lazily on first use (request or adjustment) and write their entitlement and carry-over to the ledger; read views
show the projected balance without writing.

### Requests and approval

- Request validation: active type, employee not exited, end ≥ start, both dates in the same year (split cross-year leave), not before
  hire date, at least one counted day, ≤ max days per request, no overlap with the employee's pending/approved leave, minimum service,
  and — for balance types — enough available days unless the type allows negative balances.
- Booking reserves days as **pending**; final approval moves them to **used**; decline or cancelling a pending request **releases** them;
  cancelling approved leave **restores** them. Every move is a ledger line.
- Chain: copied from the type when the request is made; a `REPORTING_MANAGER` step is dropped when the employee has no reporting manager
  with a login (an empty chain falls back to `BRANCH_MANAGER`).
- Who decides a step: reporting manager → that manager's login (or `hr.leave.manage` in scope); branch manager → `hr.leave.approve` for
  the request's branch; HR → `hr.leave.manage` for the branch. Nobody decides their own leave or a request they filed, and each step needs
  a different person. Declining needs a note. A type that requires a document cannot get its final approval without one.
- Managers may record leave for an employee (`hr.leave.approve` or `hr.leave.manage`); HR (`hr.leave.manage`) may record it as already
  approved, which writes every step as approved by them.
- Employees cancel their own pending leave, or approved leave that has not started; managers cancel any pending/approved leave with a reason.
- Notifications (push, after commit): approvers of the waiting step on request and after each approval; the employee on the final decision
  and when a manager cancels.

### Integration

- **Scheduling** (`ScheduleRules.leaveIssue`): approved full-day leave on a day a shift touches → `ON_APPROVED_LEAVE` (blocking, also on
  publish and swaps); approved half-day → `HALF_DAY_LEAVE` (warning); pending leave → `LEAVE_PENDING` (warning).
- **Timesheets**: a missed shift on an approved leave day is a `LEAVE,<TYPE>` line and does not count as an absence.
- **Attendance day board**: such a shift shows `ON_LEAVE` instead of `ABSENT`.
- The request detail lists shifts already scheduled during the leave so the manager can reassign them.

### Endpoints

| Method & path | Permission |
|---|---|
| `GET /hr/leave-types` | any staff (inactive types only with `hr.leave.manage`) |
| `POST /hr/leave-types`, `PUT /hr/leave-types/{id}` | `hr.leave.manage` + all branches |
| `GET /hr/leave-policies?leaveTypeId`, `POST /hr/leave-policies` | approve or manage / `hr.leave.manage` (organisation-wide rows: central only) |
| `GET /hr/employees/{id}/leave-balances?year` | approve or manage, employee visible |
| `POST /hr/employees/{id}/leave-balances/adjust` (`leaveTypeId`, `year`, `days`, `note`) | `hr.leave.manage`, employee manageable |
| `GET /hr/leave-balances/{id}/transactions` | own balance, or approve/manage with the employee visible |
| `GET /hr/leave-requests?status&branchId&from&to&employeeId&awaitingMe` | approve or manage; `awaitingMe=true` works for anyone (e.g. reporting managers) |
| `POST /hr/leave-requests` (`employeeId`, …, `recordApproved`), `POST /hr/leave-requests/quote` | approve or manage; `recordApproved` needs manage |
| `GET /hr/leave-requests/{id}`, `POST .../decision`, `POST .../cancel`, `POST .../document` (multipart) | see rules above |
| `GET /hr/leave-calendar?branchId&from&to` (≤ 93 days) | approve, manage or `hr.schedule.view` + branch |
| `GET /hr/me/leave-balances`, `GET/POST /hr/me/leave-requests`, `POST /hr/me/leave-requests/quote`, `POST /hr/me/leave-requests/{id}/cancel|document` | linked employee |

### Web

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/leave` | To decide, all requests, who's away, types & entitlements | `hr.leave.approve` or `hr.leave.manage` |
| `/hr/my-leave` | Balances with history, request leave (live day count and balance check), own requests, attach document, cancel | linked employee |
| `/hr/employees/[id]` → **Leave** tab | Balances (HR adjustments), the employee's requests, record leave | approve or manage |

## 5f. Payroll (Phase 6, `V33__hr_payroll.sql`)

### Data

| Table | Purpose |
|---|---|
| `hr_payroll_rules` | Deductions and employer contributions: `code`, `kind` (`EMPLOYEE_DEDUCTION`/`EMPLOYER_CONTRIBUTION`), `method` (`PERCENT`/`FIXED`/`BRACKETS`), `base` (`BASIC`/`GROSS`/`TAXABLE`), rate, fixed amount, cap, JSON bands, `pre_tax`, optional employment types, optional branch, `effective_from`. Never edited: a change is a new row; a row with `active = false` ends the code from its date. **No statutory rates are seeded.** |
| `hr_payroll_runs` | One branch and period (≤ 31 days): `run_number` (`PAY-000001`), status, totals, who calculated/approved/finalized, cancel reason, skipped employees (JSON). |
| `hr_payslips` | Snapshot per employee: contract, pay type and rate, hourly rate, paid/overtime minutes, worked days, unpaid leave days, basic, gross, taxable, deductions, net, employer contributions, issue codes, payment method/reference/time. |
| `hr_payslip_lines` | Frozen lines: `EARNING`, `DEDUCTION`, `EMPLOYER` with quantity × rate = amount. |
| `hr_payroll_adjustments` | One-off earnings (taxable or not) and post-tax deductions per employee in a run. |
| `hr_timesheets.payroll_run_id` | The run that pays a locked timesheet. Reserved at calculation, released on recalculation or cancellation, so a timesheet is never paid twice. |

### Calculation (`PayrollCalculator`, pure)

Who is paid: employees whose **home branch** is the run's branch, employed during the period, with a non-draft contract covering it
(the most recent one) and not already on another active run overlapping the period. Others are listed as skipped with a reason.

| Pay type | Basic pay | Hourly rate (for overtime/premiums) |
|---|---|---|
| `MONTHLY` | salary × employed days ÷ days in the month (full month = salary exactly) | salary × 12 ÷ (52 × weekly hours) |
| `WEEKLY` | wage × employed days ÷ 7 | wage ÷ weekly hours |
| `DAILY` | rate × distinct days with paid time on locked timesheets | rate × 5 ÷ weekly hours |
| `HOURLY` | (paid − approved overtime) minutes × rate | rate |
| `CONTRACT_SUM`, `COMMISSION` | 0, flagged `MANUAL_PAY_TYPE` (use an adjustment) | — |

Weekly hours default to 40 when the contract has none. Only timesheets lying **inside** the period and **locked** count.

- Approved overtime: minutes × hourly × overtime multiplier. Unapproved overtime minutes of hourly staff are paid at the normal rate.
- Holiday / weekend / night premiums: minutes × hourly × (multiplier − 1), only when the multiplier is above 1.
- Multipliers come from the first locked timesheet's rule snapshot, else the overtime rule in force at period end.
- Unpaid leave (approved leave of a type with `paid = false`) reduces monthly/weekly pay by days × daily rate
  (monthly: salary × 12 ÷ 260; weekly: wage ÷ 5), counted like the leave type counts days within the period.

Deductions: gross → pre-tax employee rules (base basic or gross) → **taxable** = taxable earnings − pre-tax → other employee rules →
employer contributions (do not reduce net) → one-off deductions → **net**. Percent and bracket amounts never go below zero; a cap limits
the amount. Brackets are progressive (each band's rate applies to the part of the base inside it; only the last band is open-ended).
Rule resolution per code: the latest branch row in force, else the latest organisation row; employment-type limits then apply.
Money is rounded per line to 2 decimals, half up.

Issue codes on a payslip: **errors** (block approval) `UNLOCKED_TIMESHEETS` (hourly/daily staff), `NEGATIVE_NET`; **warnings**
`UNLOCKED_OVERTIME` (salaried staff: overtime left out), `PARTIAL_PERIOD`, `MANUAL_PAY_TYPE`, `TIMESHEET_OUTSIDE_PERIOD`.

### Workflow and access

| Step | Permission | Rules |
|---|---|---|
| Create run | `hr.payroll.process` + branch | No other active run at the branch overlapping the period |
| Calculate / recalculate | `hr.payroll.process` | Draft or calculated; replaces all payslips |
| Add / remove adjustment | `hr.payroll.process` | Draft or calculated; sends the run back to draft |
| Approve | `hr.payroll.approve` | Calculated; not the person who calculated it; not someone paid in the run; no error issues |
| Return to draft | process or approve | Calculated or approved; reason audited |
| Finalize | `hr.payroll.finalize` | Approved; payslips become visible to employees; push "Payslip ready" |
| Record payment | `hr.payroll.pay` | Finalized; all or selected payslips; method and reference; run becomes Paid when every payslip is |
| Cancel | `hr.payroll.process` | Not finalized; releases timesheets |
| View runs and payslips | any `hr.payroll.*` + branch | Employees see their own payslips only once finalized |
| Payslip PDF | same as viewing | `GET /hr/payslips/{id}/pdf`, one page (summary, payment, every line); audited `HR_PAYSLIP_DOWNLOADED` (run and employee number, never amounts) |
| Payout details | edit: `hr.payroll.process` or `hr.payroll.pay` + branch; view: any payroll permission (numbers masked unless process/pay); the employee: own | `hr_employees.payout_method`, bank name/branch/account name/number, Mobile Money network/number (V39). Can also be given when adding the employee (`payout` on `POST /hr/employees`, same rules; a half-filled method refuses the whole request). Changes audited `HR_PAYOUT_DETAILS_CHANGED` without the numbers |
| Payment files | `hr.payroll.pay` + branch | Finalized or paid runs; one CSV per method (bank: bank, branch, account name and number; Mobile Money: network and number; both: amount, currency, reference `PAY-000001-EMP-000012`) for unpaid payslips with net > 0. Refused (409) while anyone due that way lacks details. `GET .../payment-files` lists people and totals per method and who is missing details. Audited `HR_PAYMENT_FILE_EXPORTED` (method, people, total) |
| Rules | `hr.settings.manage` (organisation rows: central only); viewing needs a payroll permission | |

Payroll permissions are **not** in the MANAGER defaults; ADMIN has them, others need a grant. Audit (`HR_PAYROLL_CHANGED`) records
operations, run numbers and counts — never amounts.

### Endpoints

| Method & path | Permission |
|---|---|
| `GET/POST /hr/payroll-rules` | see above |
| `GET /hr/payroll-runs?branchId`, `POST /hr/payroll-runs`, `GET /hr/payroll-runs/{id}` | payroll permissions |
| `POST /hr/payroll-runs/{id}/calculate|approve|reopen|finalize|pay|cancel` | see workflow |
| `POST /hr/payroll-runs/{id}/adjustments`, `DELETE /hr/payroll-runs/{id}/adjustments/{adjustmentId}` | `hr.payroll.process` |
| `GET /hr/payslips/{id}` | payroll permission + branch, or own finalized payslip |
| `GET /hr/me/payslips` | linked employee (finalized/paid only) |

### Web

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/payroll` | Runs per branch, new run, run drawer (payslips, issues, skipped, adjustments, approve/finalize/pay/cancel), deduction rules | any `hr.payroll.*` |
| `/hr/my-payslips` | Own finalized payslips with lines | linked employee |

## 5g. Tips and service charge (Phase 7, `V34__hr_tips_service_charge.sql`)

### Data

| Table | Purpose |
|---|---|
| `hr_tip_rules` | How a source (`TIPS` or `SERVICE_CHARGE`) is shared, organisation-wide or per branch, from a date: `method` (`HOURS`, `POINTS`, `EQUAL`), `house_percent`, `taxable`, `min_minutes`, `position_points` (JSON `{positionId: points}`). Never edited; **nothing is seeded**. |
| `hr_tip_pools` | One branch, source and period (≤ 31 days that have ended): `pool_number` (`TIP-000001` / `SVC-000001`), status, paid orders counted, collected, house and shared amounts, frozen rule snapshot, who calculated/approved. |
| `hr_tip_allocations` | One share per person: minutes worked at the branch, points, amount, and the payroll run that pays it (reserved at payroll calculation, released on recalculation, return to draft or cancel). |

### Collection and sharing (`TipCalculator`, pure)

- **Collected**: `orders.tip_amount` (or `orders.service_charge`) of orders at the branch whose **first** successful payment (status
  `SUCCESS` or `REFUNDED`) falls in the period, excluding cancelled orders and orders refunded in full — the same payment basis as the
  sales report. Each order therefore belongs to exactly one period.
- **House share**: collected × house percent, rounded half up; the rest is shared.
- **Who shares**: everyone with closed time entries **at that branch** in the period (home branch or not); minutes = elapsed − unpaid break.
  Below the minimum time, or a position with 0 / no points when points are listed, gets nothing.
- **Weights**: `HOURS` minutes; `POINTS` minutes × position points; `EQUAL` one each.
- **Rounding**: largest-remainder in cents — shares always add up exactly to the shared amount. If nobody is eligible, the amount is
  reported as kept by the house.
- **Rule resolution**: the branch rule in force at period end, else the organisation rule, else a default (by hours, house 0 %, taxable).

### Workflow, payroll and access

| Step | Who | Rules |
|---|---|---|
| Rules | `hr.tips.manage` / `hr.service_charge.manage` for that source | Organisation-wide rules: central only |
| Create / calculate pool | same source permission + branch | No other active pool of that source at the branch overlapping the period |
| Approve | same permission | Calculated; not the person who calculated it; not someone with a share |
| Return to draft / cancel | same permission, reason | Refused while a calculated (or later) payroll run pays from the pool; shares held by a draft run are released |
| Payroll | — | Approved shares in pools ending inside a payroll period become `TIPS` / `SERVICE_CHARGE` earning lines (taxable per rule) on the employee's **home-branch** payroll; the pool becomes `PAID` once every share's run is finalized |
| View | source permission, `hr.payroll.view` or `hr.payroll.process` + branch | |
| Self-service | linked employee | `GET /hr/me/tips`: approved and paid shares (shown on My Payslips) |

None of these permissions are MANAGER defaults. Audit `HR_TIPS_CHANGED` records operations, pool numbers and counts — no amounts.

### Endpoints

`GET/POST /hr/tip-rules`, `GET/POST /hr/tip-pools`, `GET /hr/tip-pools/{id}`, `POST /hr/tip-pools/{id}/calculate|approve|reopen|cancel`,
`GET /hr/me/tips`.

### Web

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/tips` | Pools per branch (new pool, drawer with collected/house/shared, shares, approve/return/cancel) and sharing rules with position points | tips, service-charge or payroll permissions |
| `/hr/my-payslips` | Adds "My tips & service charge" (approved, in payroll, paid) | linked employee |

## 5h. Recruitment (Phase 8, `V35__hr_recruitment.sql`)

### Data

| Table | Purpose |
|---|---|
| `hr_vacancies` | `vacancy_number` (`VAC-000001`), branch, optional position, title, description, requirements, employment type, openings, hired count, optional pay range, status (`DRAFT`, `OPEN`, `ON_HOLD`, `FILLED`, `CLOSED`, `CANCELLED`), closing date, hiring manager, opened/closed time and reason. |
| `hr_candidates` | One row per person: name, phone, email (stored lower case), source (walk-in, referral with the referring employee, job board, social media, agency, other), notes, `anonymised_at`. |
| `hr_candidate_documents` | CVs and certificates; bytes in the private HR document store (same validation as employee documents). |
| `hr_applications` | Candidate × vacancy (unique): stage (`APPLIED` → `SCREENING` → `INTERVIEW` → `TRIAL_SHIFT` → `OFFER` → `HIRED`, or `REJECTED` / `WITHDRAWN`), reject reason, offer terms (contract type, pay basis and amount, weekly hours, start date) and offer status (`SENT`, `ACCEPTED`, `DECLINED`), employee created on hire. |
| `hr_application_events` | Append-only stage history with note and actor. |
| `hr_interviews` | Phone / in person / video / trial shift, time, length, interviewer (any staff account), location, outcome (`SCHEDULED`, `PASSED`, `FAILED`, `NO_SHOW`, `CANCELLED`), score 1–5, feedback. |

### Rules

- **Vacancy status**: Draft → Open; Open ⇄ On hold; Open/On hold/Filled → Closed; Draft/Open/On hold → Cancelled; Closed (or Filled with
  openings left) → Open. Closing or cancelling needs a reason and rejects every open application with it; refused while an accepted offer
  is outstanding. Applications can only be added to open vacancies.
- **Pipeline** (`ApplicationStage.refusal`): forward moves only (stages may be skipped); reject (reason required) or withdraw from any
  open stage; a rejected/withdrawn application can be reconsidered back to screening; `HIRED` only through Hire. Closing an application
  cancels its scheduled interviews. Scheduling an interview or trial shift moves an earlier application to that stage.
- **Candidates**: matched by phone or email (case-insensitive) — a second registration of the same person is refused (409) so one person
  has one history. Visible to central HR, to whoever registered them, and to anyone who can see a branch they applied to.
- **Interviews**: the interviewer is notified by push and sees their interviews at `GET /hr/me/interviews` (on My Schedule) without any
  recruitment permission; they or a recruitment manager record the outcome. Passed/failed needs a score.
- **Pay**: setting or seeing a vacancy's pay range or an offer's pay needs `hr.compensation.view`; others see "hidden".
- **Hire** (needs `hr.recruitment.manage` + `hr.employee.create`, offer accepted, vacancy open or on hold): creates the employee through
  the normal employee service (duplicate email/phone checks, branch rules, primary branch assignment, audit) with the vacancy's branch,
  position and employment type and the offer's start date (or a given date). If the hirer manages contracts and the offered type is
  open-ended (permanent, probation, part-time), a **draft** contract with the offered terms is created; otherwise the result says why not.
  Candidate documents are **copied** (new storage files) to the employee as CV documents. When every opening is filled the vacancy becomes
  Filled and other open applications are rejected with "Position filled".
- **Data protection**: `POST /hr/candidates/{id}/anonymise` (no open applications) deletes the name, contact details, notes and
  documents (files removed from storage); the row and history stay for statistics. Employee records are unaffected.
- Recruitment is in the MANAGER defaults (branch-scoped), since managers already register employees.
- Audit `HR_RECRUITMENT_CHANGED` records operations and identifiers; candidate personal data and pay are never written to it.

### Endpoints

| Method & path | Permission |
|---|---|
| `GET/POST /hr/vacancies`, `GET/PUT /hr/vacancies/{id}`, `POST /hr/vacancies/{id}/status` | `hr.recruitment.manage` + branch |
| `POST /hr/vacancies/{id}/applications` (existing `candidateId` or a new `candidate`) | same |
| `GET /hr/applications/{id}`, `POST .../stage`, `POST .../interviews`, `PUT .../offer`, `POST .../offer-response` | same |
| `POST /hr/applications/{id}/hire` | + `hr.employee.create` (contract only with `hr.contract.manage`) |
| `PUT /hr/interviews/{id}` | the interviewer, or recruitment manager for the branch |
| `GET/POST /hr/candidates`, `GET/PUT /hr/candidates/{id}`, `POST .../documents` (multipart), `GET /hr/candidate-documents/{id}/download`, `POST .../anonymise` | `hr.recruitment.manage` + candidate visibility |
| `GET /hr/me/interviews` | any staff account |

### Web

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/recruitment` | Vacancies per branch (new vacancy, drawer with pipeline columns, add applicant — new or existing, status actions) and Candidates (search, history, remove personal data). The application drawer covers documents, moves, interviews and results, offer, offer response and hire. | `hr.recruitment.manage` |
| `/hr/my-schedule` | "Interviews I run" section with result recording | interviewer with a linked employee record |

## 5i. HR dashboard and HR role (Phase 9)

### Dashboard (`HrDashboardService`, `GET /hr/dashboard?branchId=`)

Needs `hr.reports.view` (ADMIN, HR role, MANAGER default). Scope = the viewer's branches, or one of them with `branchId` (403 outside
scope). Employees without a home branch count only for all-branch viewers looking at every branch. Each section appears only with
the permission behind it, so the dashboard never shows more than the viewer could open elsewhere:

| Section | Content | Needs |
|---|---|---|
| `headcount` | Employed (hired by today; active, on leave or suspended), starting soon, by status / department / employment type / branch | reports |
| `movement` | Joiners this month (and starting in the next 30 days), leavers since the 1st of last month, leavers and turnover % over 12 months (leavers / average of headcount a year ago and today) | reports |
| `trend` | Headcount at each of the last 6 month ends (this month: today), with joiners and leavers | reports |
| `today` | Attendance-board statuses summed over the branches in view, late arrivals | `hr.attendance.view` |
| `actions` | Counts of pending leave, attendance corrections, timesheets to review/approve (last ~3 months), shift swaps, payroll runs to approve / finalize / pay, pools to approve; each item only with its permission | per item |
| `expiring` | Contracts ending (incl. overdue), probations ending, documents expiring in the next 30 days, for employed people | `hr.contract.view` / `hr.document.view` |
| `recruitment` | Open and published vacancies, openings left, applicants by open stage, interviews in the next 7 days | `hr.recruitment.manage` |
| `labour` | Latest finalized or paid payroll per branch: gross, employer cost, total, sales collected for that branch in the same period (net of refunds, cancelled orders excluded), labour %, payable overtime | `hr.payroll.view` or `hr.compensation.view` |

Web: `/hr` (HR Dashboard), first item in the Human Resources menu, with a branch filter for multi-branch viewers.

### Reports (`HrReportService`, `/hr/reports`)

| Endpoint | What |
|---|---|
| `GET /hr/reports` | The reports this viewer may run (type, title, description, whether it takes a period) |
| `GET /hr/reports/{type}?from&to&branchId&includeExited` | `{columns: [{key, label}], rows: [...]}` for on-screen tables |
| `GET /hr/reports/{type}/csv?...` | The same as a CSV attachment (`text/csv`, UTF-8 with BOM, `no-store`) |

| Type | Rows | Needs (with `hr.reports.view`) |
|---|---|---|
| `EMPLOYEES` | Current employees (optionally leavers too): number, name, department, position, branch, type, status, hire and exit dates, phone, email. No pay. | `hr.employee.view` |
| `MOVEMENT` | Joined / Left events in the period, with exit status and reason | `hr.employee.view` |
| `LEAVE` | Requests overlapping the period: employee, branch, type, dates, days, status. Reasons are not exported (they can be medical). | `hr.leave.approve` or `hr.leave.manage` |
| `HOURS` | Timesheets overlapping the period: scheduled, paid, overtime and payable overtime hours, late count, absences | `hr.timesheet.review` or payroll view/process |
| `PAYROLL` | Runs overlapping the period: number, branch, dates, status, employees, gross, deductions, net, employer cost | payroll view, process or approve |

Rules: the period defaults to the 1st of this month to today and spans 1 to 366 days (400 otherwise); `branchId` outside the viewer's scope
is 403; employees without a home branch appear only for all-branch viewers on the all-branches view. CSV cells are quoted when needed,
and text starting with `=`, `+`, `-`, `@`, tab or carriage return is prefixed with an apostrophe so spreadsheets do not run it as a
formula (numbers are left as numbers). Every CSV download is audited as `HR_REPORT_EXPORTED` with the report, period, branch and row
count; viewing on screen is not. Web: `/hr/reports` ("HR Reports" in the menu): pick a report, period and branch, view, download.

### Self-service web

`/hr/me` adds "My contact details" (view and edit), "My contracts" and "My documents" (download).

### Tests

`HrDashboardIntegrationTest` (scope, starting soon, pay hidden from managers, actions per permission, expiring contracts, branch filter
and refusal), `HrReportIntegrationTest` (reports offered per permission, payroll refused to managers, branch scope and refusal, movement
in a period, period limits, CSV format and export audit), `HrReportCsvTest` (quoting and formula defusing), `HrSelfServiceIntegrationTest` (contact edit, uniqueness, format and stale version; own contracts without drafts and with
pay; own documents only), `HrRoleSecurityIntegrationTest` (HR defaults and branch reach, refusal outside HR while other roles keep
access, central HR on role assignment, sign-in and permission required on HR reads); web `hrDashboard.test.tsx`.

### Known gap (not HR-specific)

The STOMP endpoint `/ws` is open and subscriptions are not authorised, so any client that knows a topic (for example `/topic/orders.*`)
can listen without signing in. Fixing it needs the web and mobile clients to send their token on connect; tracked separately.

## 6. Audit

HR events use the shared `audit_logs` table with `AuditAction.HR_*` values (Phase 2 adds `HR_EMPLOYEE_TRANSFERRED`,
`HR_BRANCH_ASSIGNMENT_CHANGED`, `HR_BRANCH_ACCESS_CHANGED`; Phase 3 adds `HR_SCHEDULE_CHANGED` and
`HR_SCHEDULE_PUBLISHED` (entity types `Schedule` / `Branch`), `HR_SHIFT_SWAP_REQUESTED`, `HR_SHIFT_SWAP_DECIDED`,
`HR_AVAILABILITY_CHANGED`, `HR_SETTINGS_CHANGED`; Phase 4 adds `HR_ATTENDANCE_RECORDED` (manager on behalf),
`HR_ATTENDANCE_CORRECTION`, `HR_CLOCK_PIN_CHANGED` — PIN values never appear; Phase 5 adds `HR_TIMESHEET_CHANGED`,
`HR_OVERTIME_DECIDED`, `HR_LEAVE_REQUESTED`, `HR_LEAVE_DECIDED`, `HR_LEAVE_CANCELLED`, `HR_LEAVE_BALANCE_ADJUSTED`; leave types and
entitlements use `HR_SETTINGS_CHANGED`; Phase 6 adds `HR_PAYROLL_CHANGED` (operation, run number, counts; never amounts) and payroll
rules use `HR_SETTINGS_CHANGED`; Phase 7 adds `HR_TIPS_CHANGED`; Phase 8 adds `HR_RECRUITMENT_CHANGED`; Phase 9 adds `HR_REPORT_EXPORTED` (report, period, branch, row count; never the data itself)
and records self-service profile edits and document downloads with `selfService: true`). Individual clock actions are audited in
`hr_attendance_events` rather than the shared log. Metadata never contains salary figures, PINs or
document contents; salary changes are recorded as "changed" without amounts.

## 7. Web application (Phase 1)

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/employees` | Directory, filters, paging, create, import staff accounts | `hr.employee.view` |
| `/hr/employees/[id]` | Profile: overview, contracts, documents, history; edit, status, login link | `hr.employee.view` (tabs/actions per permission) |
| `/hr/organisation` | Departments and positions | `hr.employee.view` or department/position manage |
| `/hr/access` | Per-user HR permission grants | ADMIN role |
| `/hr/me` | Self-service read-only profile | account linked to an employee |
| `/hr/branches` | Branch workforce headcount | `hr.branch.view` or `hr.employee.view` |
| `/hr/schedule` | Week board (drag to move, coverage, hours, publish), day list, month overview; swap approvals, templates, limits tabs | `hr.schedule.view` |
| `/hr/my-schedule` | Own time clock and PIN, recent attendance, published shifts, swap requests, availability | linked employee |
| `/hr/attendance` | Day board (status, shift vs actual, late/early/overtime, breaks, source), clock on behalf, corrections, rules | `hr.attendance.view` |
| `/pos` → **Staff clock** | Roster, PIN pad, clock in/out and breaks, own shifts | POS operator |

The employee profile shows a **POS clock PIN** card (set/reset/remove) for `hr.attendance.manage`.

The profile has a **Branches** tab (history, transfer, add/end assignment). Employees visible only through an assignment
open read-only with only the Overview and Branches tabs. The HR Access drawer also sets extra branches per account.

- `features/hr/HrAccessContext.tsx` loads `/hr/me/access` once per signed-in user; `lib/navigation.ts` items carry an `hr`
  requirement (`"self"` = linked employee). Hiding is cosmetic; the API enforces everything.
- Data layer: `lib/hrApi.ts` (reuses `apiRequest`, `downloadFile`, `rawError` from `lib/apiClient.ts`).
- The `/api/rm` proxy forwards `X-Content-Type-Options` and `Cache-Control` so document downloads keep their protections.

## 8. Tests (Phase 1)

| Suite | Scope |
|---|---|
| `HrUnitTest` | Role defaults, branch scope rules, permission codes, file-signature detection |
| `HrEmployeeIntegrationTest` (16) | Registry CRUD, numbering, duplicates, branch isolation, grants, exits, optimistic locking, reporting loops, org validation, user linking, import, audit scoping |
| `HrContractDocumentIntegrationTest` (7) | Pay masking, lifecycle, renewal, expiry job, document validation/download/IDOR, cross-employee document refs |
| `HrMultiBranchIntegrationTest` (9) | Primary history on hire, transfers (dates, permissions, visibility, audit), same-day correction, covering-branch visibility vs management, overlap rules, temporary expiry, exit/rehire, extra branch scope, workforce counts, scoped vs central assigners |
| `ScheduleRulesTest` (8) | Every validation rule incl. overnight shifts, breaks in weekly hours, availability windows |
| `HrSchedulingIntegrationTest` (12) | Draft/publish visibility incl. legacy APIs, blocking vs acknowledged warnings, templates and coverage, branch scope, cancel/delete, all-or-nothing publish, availability, swaps (exchange, refusal, party cannot approve), policy override, legacy rota compatibility, login linking |
| `web/src/features/hr/schedule/schedule.test.tsx` (5) | Overnight time maths, weekly totals, issue grouping |
| `AttendanceUnitTest` (8) | Paid time, late/early grace, overtime, missed clock-out, break pay, shift window, PIN rules, event-time limits |
| `HrAttendanceIntegrationTest` (11) | POS full shift and account separation, idempotency, PIN lockout persistence, branch rules, unscheduled policy, day board, corrections (originals kept, second person, own record), missing record without approval, self/legacy/POS sharing one open record, manager clock and PIN management, legacy edit history |
| `web/src/features/hr/attendance/attendance.test.tsx` (6) | Staff clock flow, state-based actions, retry with the same key on network failure, no retry on refusal |
| `LeaveCalculatorTest` (5) | Workday/calendar counting, holidays, half days, pro-rating, monthly accrual, carry-over cap |
| `ScheduleRulesTest` (+1) | Approved leave blocks, half-day and pending leave warn, overnight shift into leave |
| `HrLeaveIntegrationTest` (10) | Self-service request → approve → cancel with ledger, insufficient vs negative-allowed balance, two-step chain with distinct approvers and decline note, required document, reporting-manager step and fallback, scheduling block/warn and conflicts, holidays and half days, cross-year refusal, carry-over cap and HR-only adjustments, HR-recorded leave on timesheet and day board, branch isolation and central configuration |
| `web/src/features/hr/leave/leave.test.tsx` (4) | Date/day formatting, live quote and submit, quote problems block submit, decline needs note and approve sends decision |
| `PayrollCalculatorTest` (6) | Monthly full/partial/unpaid leave, hourly normal vs approved overtime and holiday premium, daily and manual pay types, progressive brackets and validation, pre-tax → tax → employer → one-off order with caps, non-negative amounts |
| `HrPayrollIntegrationTest` (4) | Full cycle with rules (calculate, four-eyes approval, finalize, self-service visibility, partial then full payment, no amounts in audit), blocking unlocked timesheets, adjustments sending the run back to draft, one run per branch/period, cancel releasing timesheets, new-joiner pro-rating, unpaid leave, skipped employees, rule validation, central rules and ending a rule |
| `web/src/features/hr/payroll/payroll.test.tsx` (4) | Approve disabled for the calculator and with blocking payslips, finalize action, payslip sections |
| `TipCalculatorTest` (5) | House share, hours split with exact cents, position points and zero points, minimum time and equal split, nothing eligible |
| `HrTipsIntegrationTest` (3) | Collection rules (refunds, other branch, outside period), hours split incl. a visiting employee, four-eyes approval, home-branch payroll for each share, pool locked while payroll uses it, pool paid after both payrolls; branch rule with points overriding the organisation rule, one pool per source and period, cancel; per-source permissions and branch scope |
| `web/src/features/hr/tips/tips.test.tsx` (4) | Shares and unshared note, approve disabled for the calculator, no actions without the source permission, approve action |
| `ApplicationStageTest` (2) | Forward moves and skips, no backwards or direct hire, close from any open stage, reconsider only to screening |
| `HrRecruitmentIntegrationTest` (4) | Manager pipeline without pay rights, duplicate candidate, CV upload, interviewer without recruitment rights records the result, offer without pay, hire creating the employee and copying the CV, vacancy filled and others rejected, anonymising after hire keeps the employee's copy; HR hire with pay creating a draft contract (pay hidden from the manager); stage rules, closing with/without accepted offers, reopening, branch isolation; anonymisation needs closed applications |
| `web/src/features/hr/recruitment/recruitment.test.tsx` (4) | Next-stage rules, reject needs a note and moves send the stage, hidden pay and hire result, pipeline grouping and closed toggle |
| `HrPostgresFlywayIntegrationTest` | Same flows on the Flyway schema (tag `postgres`, run with `-Ppostgres-it`) |
| `web/src/features/hr/hr.test.tsx` (9) | Permission-filtered navigation, employee form validation and payload |

## 9. Deployment considerations

- `hr.documents.dir` (env `HR_DOCUMENTS_DIR`, default `hr-documents`) must be a persistent, **non-public** volume. In a
  multi-instance deployment it must be shared storage. It must not be served by any static file server.
- Run Flyway (`V27+`) before starting the new build.
- `docker-compose.yml` mounts the named volume `hr_documents` at `/app/hr-documents` (`HR_DOCUMENTS_DIR`).
- `HR_DEFAULT_MIN_REST_MINUTES`, `HR_DEFAULT_MAX_SHIFT_MINUTES`, `HR_DEFAULT_MAX_WEEKLY_MINUTES` set scheduling fallbacks
  until an organisation policy is saved.
- Shift times are entered in the browser's local time; staff devices should use the restaurant's time zone (`app.timezone`).
- `HR_DEFAULT_*` attendance fallbacks (grace, early clock-in, unscheduled, approval, paid breaks, self clock, missed
  clock-out), `HR_CLOCK_PIN_MAX_ATTEMPTS`, `HR_CLOCK_PIN_LOCKOUT_MINUTES`, `RATE_LIMIT_HR_CLOCK_REQUESTS/WINDOW_SECONDS`.
- Behind the web proxy every POS request reaches the backend from the proxy's address, so the `hrclock` rate-limit bucket is
  shared by all terminals of one web server; size it for shift changes.
- `HR_LEAVE_NON_WORKING_DAYS` (default `SAT,SUN`): weekdays not counted for `WORKDAYS` leave. Restaurants that trade every day may
  want this empty. Changing it does not recount existing requests.
- Tip and service-charge sharing rules are not seeded; without one, pools are shared by hours worked and the house keeps nothing.
- Payroll deduction rules are not seeded: configure income tax, pension and other deductions in HR > Payroll > Deductions before the
  first run. The run currency is `GHS` (the column exists for later multi-currency support).
- Leave entitlements are not seeded: configure them in HR > Leave > Types & entitlements before staff request balance-tracked leave.
- `HR_CONTRACT_EXPIRY_CRON` (default `0 15 1 * * *`, in `app.timezone`) schedules the contract expiry job.
- Running `-Ppostgres-it` against Docker Engine 29+ needs `DOCKER_API_VERSION=1.44` (older docker-java default is rejected).
