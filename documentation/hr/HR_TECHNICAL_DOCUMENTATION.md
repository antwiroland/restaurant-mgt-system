# HR Technical Documentation

Describes only what is implemented. Sections for later phases are added as each phase is delivered.

## 1. Architecture and module boundaries

- Backend package: `com.restaurantmanager.core.hr`
  - `hr.access` — `HrPermission`, `HrAccessService` (permission + branch scope), per-user grants, `/hr/access/**`
  - `hr.org` — departments and positions
  - `hr.employee` — employee registry, emergency contacts, user-account link, self-profile
  - `hr.contract` — employment contracts and renewals
  - `hr.document` — private employee document store
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
- Otherwise → the user's own `branch_id` only; no branch → no branch-specific data.

An employee is in scope when their primary branch is in scope. Employees with no primary branch are visible to all-branch
users only. Out-of-scope lookups return **404** (no existence leak).

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
| `GET /hr/employees/{id}/audit` | `hr.employee.view` |

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

## 6. Audit

HR events use the shared `audit_logs` table with `AuditAction.HR_*` values. Metadata never contains salary figures, PINs or
document contents; salary changes are recorded as "changed" without amounts.

## 7. Web application (Phase 1)

| Route | Purpose | Shown when |
|---|---|---|
| `/hr/employees` | Directory, filters, paging, create, import staff accounts | `hr.employee.view` |
| `/hr/employees/[id]` | Profile: overview, contracts, documents, history; edit, status, login link | `hr.employee.view` (tabs/actions per permission) |
| `/hr/organisation` | Departments and positions | `hr.employee.view` or department/position manage |
| `/hr/access` | Per-user HR permission grants | ADMIN role |
| `/hr/me` | Self-service read-only profile | account linked to an employee |

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
| `HrPostgresFlywayIntegrationTest` | Same flows on the Flyway schema (tag `postgres`, run with `-Ppostgres-it`) |
| `web/src/features/hr/hr.test.tsx` (9) | Permission-filtered navigation, employee form validation and payload |

## 9. Deployment considerations

- `hr.documents.dir` (env `HR_DOCUMENTS_DIR`, default `hr-documents`) must be a persistent, **non-public** volume. In a
  multi-instance deployment it must be shared storage. It must not be served by any static file server.
- Run Flyway (`V27+`) before starting the new build.
- `docker-compose.yml` mounts the named volume `hr_documents` at `/app/hr-documents` (`HR_DOCUMENTS_DIR`).
- `HR_CONTRACT_EXPIRY_CRON` (default `0 15 1 * * *`, in `app.timezone`) schedules the contract expiry job.
- Running `-Ppostgres-it` against Docker Engine 29+ needs `DOCKER_API_VERSION=1.44` (older docker-java default is rejected).
