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
| `hr.settings.manage` | HR settings | ✓ | |

Further codes (`hr.schedule.*`, `hr.attendance.*`, `hr.payroll.*`, ...) are declared now so grants are stable, and are
enforced as their phases are implemented.

Grants can only be managed by ADMIN (`/hr/access/grants`), and every grant/revoke is audited. CUSTOMER accounts can never
receive HR permissions.

### 2.2 Branch scope

`HrAccessService.scope(principal)`:

- ADMIN, or holder of `hr.branch.all` → all branches.
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

## 6. Audit

HR events use the shared `audit_logs` table with `AuditAction.HR_*` values (Phase 2 adds `HR_EMPLOYEE_TRANSFERRED`,
`HR_BRANCH_ASSIGNMENT_CHANGED`, `HR_BRANCH_ACCESS_CHANGED`; Phase 3 adds `HR_SCHEDULE_CHANGED` and
`HR_SCHEDULE_PUBLISHED` (entity types `Schedule` / `Branch`), `HR_SHIFT_SWAP_REQUESTED`, `HR_SHIFT_SWAP_DECIDED`,
`HR_AVAILABILITY_CHANGED`, `HR_SETTINGS_CHANGED`). Metadata never contains salary figures, PINs or
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
| `/hr/my-schedule` | Own published shifts, swap requests, availability | linked employee |

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
- `HR_CONTRACT_EXPIRY_CRON` (default `0 15 1 * * *`, in `app.timezone`) schedules the contract expiry job.
- Running `-Ppostgres-it` against Docker Engine 29+ needs `DOCKER_API_VERSION=1.44` (older docker-java default is rejected).
