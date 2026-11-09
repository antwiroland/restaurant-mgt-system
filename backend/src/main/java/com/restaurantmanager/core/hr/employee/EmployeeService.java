package com.restaurantmanager.core.hr.employee;

import com.restaurantmanager.core.audit.AuditLogEntity;
import com.restaurantmanager.core.audit.AuditLogRepository;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.branch.AssignmentType;
import com.restaurantmanager.core.hr.branch.BranchAssignmentEntity;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRecorder;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.common.HrSequenceService;
import com.restaurantmanager.core.hr.common.PageResponse;
import com.restaurantmanager.core.hr.org.DepartmentEntity;
import com.restaurantmanager.core.hr.org.DepartmentRepository;
import com.restaurantmanager.core.hr.org.PositionEntity;
import com.restaurantmanager.core.hr.org.PositionRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Central employee registry. Every read and write is checked against HR permissions and branch scope. */
@Service
public class EmployeeService {
    static final String SEQUENCE = "EMPLOYEE";
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.MANAGER, Role.CASHIER, Role.RIDER);
    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final HrAccessService accessService;
    private final HrSequenceService sequenceService;
    private final HrAuditService audit;
    private final BranchAssignmentRecorder assignmentRecorder;
    private final BranchAssignmentRepository assignmentRepository;
    private final com.restaurantmanager.core.workforce.WorkScheduleRepository scheduleRepository;
    private final com.restaurantmanager.core.workforce.TimeEntryRepository timeEntryRepository;
    private final ZoneId zone;

    public EmployeeService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                           PositionRepository positionRepository, BranchRepository branchRepository, UserRepository userRepository,
                           AuditLogRepository auditLogRepository, HrAccessService accessService, HrSequenceService sequenceService,
                           HrAuditService audit, BranchAssignmentRecorder assignmentRecorder,
                           BranchAssignmentRepository assignmentRepository,
                           com.restaurantmanager.core.workforce.WorkScheduleRepository scheduleRepository,
                           com.restaurantmanager.core.workforce.TimeEntryRepository timeEntryRepository,
                           @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessService = accessService;
        this.sequenceService = sequenceService;
        this.audit = audit;
        this.assignmentRecorder = assignmentRecorder;
        this.assignmentRepository = assignmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.zone = ZoneId.of(timeZone);
    }

    // ------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummary> list(UserPrincipal principal, EmployeeFilter filter, int page, int size, String sort) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_VIEW);
        if (filter.branchId() != null && !access.canSeeBranch(filter.branchId())) {
            throw new ApiException(403, "You do not have access to this branch");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort));
        return PageResponse.of(employeeRepository.findAll(spec(filter, access, LocalDate.now(zone)), pageable), EmployeeService::toSummary);
    }

    @Transactional(readOnly = true)
    public EmployeeDetail get(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_VIEW);
        EmployeeEntity employee = loadVisible(id, access);
        return toDetail(employee, access.canSeeBranch(employee.primaryBranchId()));
    }

    @Transactional(readOnly = true)
    public EmployeeDetail myProfile(UserPrincipal principal) {
        EmployeeEntity employee = employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
        return toDetail(employeeRepository.findDetailedById(employee.getId()).orElseThrow(), false);
    }

    /**
     * Read access: the employee's primary branch is in scope, or they hold an active secondary/temporary assignment at a
     * branch in scope (so a covering branch can see who works there). Out-of-scope records are reported as missing.
     */
    @Transactional(readOnly = true)
    public EmployeeEntity loadVisible(UUID id, HrAccess access) {
        EmployeeEntity employee = employeeRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(404, "Employee not found"));
        if (access.canSeeBranch(employee.primaryBranchId())) {
            return employee;
        }
        boolean assignedHere = assignmentRepository.findActiveSecondary(id, LocalDate.now(zone)).stream()
                .anyMatch(a -> access.canSeeBranch(a.getBranchId()));
        if (!assignedHere) {
            throw new ApiException(404, "Employee not found");
        }
        return employee;
    }

    /**
     * Management access: the employee's primary branch must be in scope. Used for edits, contracts, documents and history,
     * which stay with the home branch even when the employee also works elsewhere.
     */
    @Transactional(readOnly = true)
    public EmployeeEntity loadInScope(UUID id, HrAccess access) {
        EmployeeEntity employee = employeeRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(404, "Employee not found"));
        if (!access.canSeeBranch(employee.primaryBranchId())) {
            throw new ApiException(404, "Employee not found");
        }
        return employee;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> auditTrail(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_VIEW);
        loadInScope(id, access);
        return auditLogRepository.findTimeline("Employee", id, PageRequest.of(0, 200)).stream()
                .map(EmployeeService::toAuditEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkableUser> linkableUsers(UserPrincipal principal) {
        HrAccess access = accessService.requireAny(principal, HrPermission.EMPLOYEE_CREATE, HrPermission.EMPLOYEE_UPDATE);
        List<UserEntity> staff = userRepository.findByRoleInAndActiveTrueOrderByNameAsc(STAFF_ROLES).stream()
                .filter(u -> access.canSeeBranch(branchId(u)))
                .toList();
        Set<UUID> linked = staff.isEmpty() ? Set.of() : employeeRepository.findLinkedUserIds(staff.stream().map(UserEntity::getId).toList());
        return staff.stream()
                .filter(u -> !linked.contains(u.getId()))
                .map(u -> new LinkableUser(u.getId(), u.getName(), u.getPhone(), u.getEmail(), u.getRole().name(), branchId(u)))
                .toList();
    }

    // ------------------------------------------------------------ commands

    @Transactional
    public EmployeeDetail create(UserPrincipal principal, EmployeeRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_CREATE);
        EmployeeEntity employee = new EmployeeEntity();
        UUID branchId = request.primaryBranchId();
        if (branchId == null && !access.allBranches()) {
            branchId = access.branchIds().stream().findFirst()
                    .orElseThrow(() -> new ApiException(403, "Your account is not assigned to a branch"));
        }
        applyBranch(employee, branchId, access);
        String number = blankToNull(request.employeeNumber());
        if (number != null) {
            number = number.toUpperCase(Locale.ROOT);
            if (employeeRepository.existsByEmployeeNumberIgnoreCase(number)) {
                throw new ApiException(409, "Employee number " + number + " is already in use");
            }
        } else {
            number = sequenceService.next(SEQUENCE, "EMP");
        }
        employee.setEmployeeNumber(number);
        applyFields(employee, request, access);
        if (request.userId() != null) {
            employee.setUserId(validateLinkableUser(request.userId(), null, access).getId());
        }
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setCreatedBy(access.userId());
        EmployeeEntity saved = employeeRepository.saveAndFlush(employee);
        if (saved.getUserId() != null) {
            attachLoginHistory(saved.getUserId(), saved.getId());
        }
        assignmentRecorder.openPrimary(saved.getId(), saved.primaryBranchId(), positionId(saved), saved.getHireDate(), "Hired", access.userId());
        audit.log(access.userId(), AuditAction.HR_EMPLOYEE_CREATED, "Employee", saved.getId(),
                Map.of("employeeNumber", saved.getEmployeeNumber(), "branchId", String.valueOf(saved.primaryBranchId())));
        return toDetail(saved, true);
    }

    @Transactional
    public EmployeeDetail update(UserPrincipal principal, UUID id, EmployeeRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_UPDATE);
        EmployeeEntity employee = loadInScope(id, access);
        if (request.version() != null && request.version() != employee.getVersion()) {
            throw new ApiException(409, "This employee was changed by someone else. Reload and try again.");
        }
        Map<String, Object> before = snapshot(employee);
        if (!Objects.equals(request.primaryBranchId(), employee.primaryBranchId())) {
            // a branch change from the edit form is a transfer effective today, recorded in the assignment history
            transferInternal(employee, request.primaryBranchId(), LocalDate.now(zone), "Changed on employee record", access);
        }
        String number = blankToNull(request.employeeNumber());
        if (number != null && !number.equalsIgnoreCase(employee.getEmployeeNumber())) {
            throw new ApiException(400, "Employee numbers cannot be changed");
        }
        applyFields(employee, request, access);
        Map<String, Object> after = snapshot(employee);
        List<String> changed = before.keySet().stream().filter(k -> !Objects.equals(before.get(k), after.get(k))).toList();
        if (!changed.isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("fields", changed);
            if (changed.contains("primaryBranchId")) {
                meta.put("fromBranchId", String.valueOf(before.get("primaryBranchId")));
                meta.put("toBranchId", String.valueOf(after.get("primaryBranchId")));
            }
            audit.log(access.userId(), AuditAction.HR_EMPLOYEE_UPDATED, "Employee", id, meta);
        }
        return toDetail(employeeRepository.saveAndFlush(employee), true);
    }

    /** Moves the employee's home branch from a given date, closing the old primary assignment and opening a new one. */
    @Transactional
    public EmployeeDetail transfer(UserPrincipal principal, UUID id, TransferRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_UPDATE);
        EmployeeEntity employee = loadInScope(id, access);
        if (employee.getEmploymentStatus().isExit()) {
            throw new ApiException(400, "Former employees cannot be transferred");
        }
        LocalDate effective = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(zone);
        transferInternal(employee, request.toBranchId(), effective, blankToNull(request.reason()), access);
        return toDetail(employeeRepository.saveAndFlush(employee), access.canSeeBranch(employee.primaryBranchId()));
    }

    private void transferInternal(EmployeeEntity employee, UUID toBranchId, LocalDate effective, String reason, HrAccess access) {
        if (!access.has(HrPermission.EMPLOYEE_TRANSFER)) {
            throw new ApiException(403, "You do not have permission to move employees between branches");
        }
        if (effective.isAfter(LocalDate.now(zone))) {
            throw new ApiException(400, "Transfers cannot be dated in the future");
        }
        if (effective.isBefore(employee.getHireDate())) {
            throw new ApiException(400, "The transfer date cannot be before the hire date");
        }
        UUID from = employee.primaryBranchId();
        applyBranch(employee, toBranchId, access);
        assignmentRecorder.switchPrimary(employee.getId(), toBranchId, positionId(employee), effective, reason, access.userId());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fromBranchId", String.valueOf(from));
        meta.put("toBranchId", String.valueOf(toBranchId));
        meta.put("effectiveDate", effective.toString());
        if (reason != null) {
            meta.put("reason", reason);
        }
        audit.log(access.userId(), AuditAction.HR_EMPLOYEE_TRANSFERRED, "Employee", employee.getId(), meta);
    }

    @Transactional
    public EmployeeDetail changeStatus(UserPrincipal principal, UUID id, StatusRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_UPDATE);
        EmployeeEntity employee = loadInScope(id, access);
        EmploymentStatus from = employee.getEmploymentStatus();
        EmploymentStatus to = request.status();
        if (from == to) {
            return toDetail(employee, true);
        }
        if ((to.isExit() || from.isExit()) && !access.has(HrPermission.EMPLOYEE_ARCHIVE)) {
            throw new ApiException(403, "You do not have permission to record exits or rehires");
        }
        if (to.isExit()) {
            if (blankToNull(request.reason()) == null) {
                throw new ApiException(400, "A reason is required when employment ends");
            }
            LocalDate exitDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(zone);
            if (exitDate.isBefore(employee.getHireDate())) {
                throw new ApiException(400, "Exit date cannot be before the hire date");
            }
            employee.setExitDate(exitDate);
            employee.setExitReason(request.reason().trim());
            assignmentRecorder.closeAll(id, exitDate, "Employment ended: " + to.name().toLowerCase(Locale.ROOT), access.userId());
        } else if (from.isExit()) {
            employee.setExitDate(null);
            employee.setExitReason(null);
            assignmentRecorder.openPrimary(id, employee.primaryBranchId(), positionId(employee),
                    request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(zone), "Re-hired", access.userId());
        }
        employee.setEmploymentStatus(to);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("from", from.name());
        meta.put("to", to.name());
        if (blankToNull(request.reason()) != null) {
            meta.put("reason", request.reason().trim());
        }
        audit.log(access.userId(), AuditAction.HR_EMPLOYEE_STATUS_CHANGED, "Employee", id, meta);
        return toDetail(employeeRepository.saveAndFlush(employee), true);
    }

    @Transactional
    public EmployeeDetail linkUser(UserPrincipal principal, UUID id, UUID userId) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_UPDATE);
        EmployeeEntity employee = loadInScope(id, access);
        if (employee.getUserId() != null) {
            throw new ApiException(409, "This employee is already linked to a user account");
        }
        UserEntity user = validateLinkableUser(userId, id, access);
        employee.setUserId(user.getId());
        attachLoginHistory(user.getId(), employee.getId());
        audit.log(access.userId(), AuditAction.HR_EMPLOYEE_USER_LINKED, "Employee", id, Map.of("userId", user.getId().toString()));
        return toDetail(employeeRepository.saveAndFlush(employee), true);
    }

    @Transactional
    public EmployeeDetail unlinkUser(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_UPDATE);
        EmployeeEntity employee = loadInScope(id, access);
        if (employee.getUserId() == null) {
            return toDetail(employee, true);
        }
        UUID previous = employee.getUserId();
        employee.setUserId(null);
        audit.log(access.userId(), AuditAction.HR_EMPLOYEE_USER_UNLINKED, "Employee", id, Map.of("userId", previous.toString()));
        return toDetail(employeeRepository.saveAndFlush(employee), true);
    }

    /**
     * Creates employee records for staff accounts that do not have one yet, so existing staff join the registry without
     * re-typing. Accounts whose phone or email already belongs to an employee are skipped and reported, never merged.
     */
    @Transactional
    public ImportResult importUsers(UserPrincipal principal, ImportRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_CREATE);
        List<LinkableUser> candidates = linkableUsers(principal);
        List<UUID> created = new ArrayList<>();
        List<ImportSkip> skipped = new ArrayList<>();
        for (LinkableUser candidate : candidates) {
            UserEntity user = userRepository.findById(candidate.id()).orElse(null);
            if (user == null) {
                continue;
            }
            String email = blankToNull(user.getEmail());
            String phone = blankToNull(user.getPhone());
            if (email != null && employeeRepository.existsByEmailIgnoreCase(email)) {
                skipped.add(new ImportSkip(user.getId(), user.getName(), "Another employee already uses this email"));
                continue;
            }
            if (phone != null && employeeRepository.existsByPhone(phone)) {
                skipped.add(new ImportSkip(user.getId(), user.getName(), "Another employee already uses this phone number"));
                continue;
            }
            String[] names = splitName(user.getName());
            EmployeeEntity employee = new EmployeeEntity();
            employee.setEmployeeNumber(sequenceService.next(SEQUENCE, "EMP"));
            employee.setUserId(user.getId());
            employee.setFirstName(names[0]);
            employee.setMiddleName(names[1]);
            employee.setLastName(names[2]);
            employee.setEmail(email);
            employee.setPhone(phone);
            employee.setPrimaryBranch(user.getBranch());
            employee.setEmploymentType(request.employmentType());
            employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
            employee.setHireDate(request.hireDate());
            employee.setCreatedBy(access.userId());
            EmployeeEntity saved = employeeRepository.saveAndFlush(employee);
            attachLoginHistory(user.getId(), saved.getId());
            assignmentRecorder.openPrimary(saved.getId(), saved.primaryBranchId(), null, saved.getHireDate(), "Imported from staff account", access.userId());
            audit.log(access.userId(), AuditAction.HR_EMPLOYEE_CREATED, "Employee", saved.getId(),
                    Map.of("employeeNumber", saved.getEmployeeNumber(), "source", "user-import", "userId", user.getId().toString()));
            created.add(saved.getId());
        }
        return new ImportResult(created.size(), created, skipped);
    }

    // ------------------------------------------------------------ helpers

    private void applyFields(EmployeeEntity employee, EmployeeRequest request, HrAccess access) {
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());
        UUID id = employee.getId();
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
            boolean taken = id == null ? employeeRepository.existsByEmailIgnoreCase(email) : employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id);
            if (taken) {
                throw new ApiException(409, "Another employee already uses this email");
            }
        }
        if (phone != null) {
            boolean taken = id == null ? employeeRepository.existsByPhone(phone) : employeeRepository.existsByPhoneAndIdNot(phone, id);
            if (taken) {
                throw new ApiException(409, "Another employee already uses this phone number");
            }
        }
        if (request.hireDate().isAfter(LocalDate.now(zone).plusYears(1))) {
            throw new ApiException(400, "Hire date is too far in the future");
        }
        if (employee.getExitDate() != null && employee.getExitDate().isBefore(request.hireDate())) {
            throw new ApiException(400, "Hire date cannot be after the exit date");
        }
        PositionEntity position = null;
        if (request.positionId() != null) {
            position = positionRepository.findById(request.positionId()).orElseThrow(() -> new ApiException(404, "Position not found"));
            if (!position.isActive() && (employee.getPosition() == null || !position.getId().equals(employee.getPosition().getId()))) {
                throw new ApiException(400, "Position " + position.getTitle() + " is archived");
            }
        }
        DepartmentEntity department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId()).orElseThrow(() -> new ApiException(404, "Department not found"));
            if (department.getBranchId() != null && !access.canSeeBranch(department.getBranchId())) {
                throw new ApiException(404, "Department not found");
            }
            if (!department.isActive() && (employee.getDepartment() == null || !department.getId().equals(employee.getDepartment().getId()))) {
                throw new ApiException(400, "Department " + department.getName() + " is archived");
            }
        } else if (position != null) {
            department = position.getDepartment();
        }
        if (position != null && position.getDepartment() != null && department != null
                && !position.getDepartment().getId().equals(department.getId())) {
            throw new ApiException(400, "Position " + position.getTitle() + " belongs to another department");
        }
        EmployeeEntity manager = null;
        if (request.reportingManagerId() != null) {
            if (request.reportingManagerId().equals(id)) {
                throw new ApiException(400, "An employee cannot report to themselves");
            }
            manager = employeeRepository.findById(request.reportingManagerId())
                    .orElseThrow(() -> new ApiException(404, "Reporting manager not found"));
            assertNoReportingCycle(id, manager);
        }
        employee.setFirstName(request.firstName().trim());
        employee.setMiddleName(blankToNull(request.middleName()));
        employee.setLastName(blankToNull(request.lastName()));
        employee.setGender(request.gender());
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setEmail(email);
        employee.setPhone(phone);
        employee.setAddress(blankToNull(request.address()));
        employee.setDepartment(department);
        employee.setPosition(position);
        employee.setEmploymentType(request.employmentType());
        employee.setHireDate(request.hireDate());
        employee.setReportingManager(manager);
        employee.setQualifications(blankToNull(request.qualifications()));
        employee.setExperience(blankToNull(request.experience()));
        employee.getEmergencyContacts().clear();
        List<EmergencyContactRequest> contacts = request.emergencyContacts() == null ? List.of() : request.emergencyContacts();
        for (int i = 0; i < contacts.size(); i++) {
            EmergencyContactRequest c = contacts.get(i);
            EmergencyContactEntity contact = new EmergencyContactEntity();
            contact.setEmployee(employee);
            contact.setName(c.name().trim());
            contact.setRelationship(blankToNull(c.relationship()));
            contact.setPhone(c.phone().trim());
            contact.setEmail(blankToNull(c.email()));
            contact.setSortOrder(i);
            employee.getEmergencyContacts().add(contact);
        }
    }

    private void applyBranch(EmployeeEntity employee, UUID branchId, HrAccess access) {
        access.assertBranch(branchId);
        if (branchId == null) {
            employee.setPrimaryBranch(null);
            return;
        }
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new ApiException(404, "Branch not found"));
        if (!branch.isActive()) {
            throw new ApiException(400, "Branch " + branch.getName() + " is inactive");
        }
        employee.setPrimaryBranch(branch);
    }

    private void assertNoReportingCycle(UUID employeeId, EmployeeEntity manager) {
        if (employeeId == null) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        EmployeeEntity current = manager;
        while (current != null && seen.add(current.getId())) {
            if (current.getId().equals(employeeId)) {
                throw new ApiException(400, "This reporting line would create a loop");
            }
            current = current.getReportingManager();
        }
    }

    private UserEntity validateLinkableUser(UUID userId, UUID employeeId, HrAccess access) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ApiException(404, "User account not found"));
        if (user.getRole() == Role.CUSTOMER) {
            throw new ApiException(400, "Customer accounts cannot be linked to employees");
        }
        if (!user.isActive()) {
            throw new ApiException(400, "This user account is deactivated");
        }
        if (!access.canSeeBranch(branchId(user))) {
            throw new ApiException(404, "User account not found");
        }
        employeeRepository.findByUserId(userId).ifPresent(existing -> {
            if (!existing.getId().equals(employeeId)) {
                throw new ApiException(409, "This user account is already linked to employee " + existing.getEmployeeNumber());
            }
        });
        return user;
    }

    /** EXISTS an active secondary/temporary assignment of the employee at one of {@code branches} on {@code day}. */
    private static Predicate assignedAt(jakarta.persistence.criteria.Root<EmployeeEntity> root,
                                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                                        jakarta.persistence.criteria.CriteriaBuilder cb, Set<UUID> branches, LocalDate day) {
        jakarta.persistence.criteria.Subquery<UUID> sub = query.subquery(UUID.class);
        jakarta.persistence.criteria.Root<BranchAssignmentEntity> a = sub.from(BranchAssignmentEntity.class);
        sub.select(a.get("id")).where(
                cb.equal(a.get("employeeId"), root.get("id")),
                cb.notEqual(a.get("assignmentType"), AssignmentType.PRIMARY),
                a.get("branchId").in(branches),
                cb.lessThanOrEqualTo(a.<LocalDate>get("startDate"), day),
                cb.or(cb.isNull(a.get("endDate")), cb.greaterThanOrEqualTo(a.<LocalDate>get("endDate"), day)));
        return cb.exists(sub);
    }

    private static Specification<EmployeeEntity> spec(EmployeeFilter filter, HrAccess access, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Set<UUID> branches = access.branchFilter();
            if (branches != null) {
                if (branches.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(cb.or(root.get("primaryBranch").get("id").in(branches), assignedAt(root, query, cb, branches, today)));
            }
            if (filter.branchId() != null) {
                predicates.add(cb.or(cb.equal(root.get("primaryBranch").get("id"), filter.branchId()),
                        assignedAt(root, query, cb, Set.of(filter.branchId()), today)));
            }
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), filter.departmentId()));
            }
            if (filter.positionId() != null) {
                predicates.add(cb.equal(root.get("position").get("id"), filter.positionId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("employmentStatus"), filter.status()));
            } else if (!filter.includeExited()) {
                predicates.add(root.get("employmentStatus").in(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE, EmploymentStatus.SUSPENDED));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("employmentType"), filter.type()));
            }
            String q = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("firstName")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("lastName"), "")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("employeeNumber")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("email"), "")), like, '\\'),
                        cb.like(cb.coalesce(root.<String>get("phone"), ""), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort sortOf(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "name,asc" : sort).split(",");
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return switch (parts[0]) {
            case "number" -> Sort.by(direction, "employeeNumber");
            case "hireDate" -> Sort.by(direction, "hireDate").and(Sort.by("employeeNumber"));
            case "status" -> Sort.by(direction, "employmentStatus").and(Sort.by("lastName", "firstName"));
            default -> Sort.by(direction, "lastName", "firstName").and(Sort.by("employeeNumber"));
        };
    }

    private static Map<String, Object> snapshot(EmployeeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("firstName", e.getFirstName());
        m.put("middleName", e.getMiddleName());
        m.put("lastName", e.getLastName());
        m.put("gender", e.getGender());
        m.put("dateOfBirth", e.getDateOfBirth());
        m.put("email", e.getEmail());
        m.put("phone", e.getPhone());
        m.put("address", e.getAddress());
        m.put("departmentId", e.getDepartment() == null ? null : e.getDepartment().getId());
        m.put("positionId", e.getPosition() == null ? null : e.getPosition().getId());
        m.put("employmentType", e.getEmploymentType());
        m.put("hireDate", e.getHireDate());
        m.put("reportingManagerId", e.getReportingManager() == null ? null : e.getReportingManager().getId());
        m.put("primaryBranchId", e.primaryBranchId());
        m.put("qualifications", e.getQualifications());
        m.put("experience", e.getExperience());
        m.put("emergencyContacts", e.getEmergencyContacts().stream().map(c -> c.getName() + "|" + c.getPhone() + "|" + c.getRelationship()).toList());
        return m;
    }

    static String[] splitName(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return new String[]{"Unnamed", null, null};
        }
        if (parts.length == 1) {
            return new String[]{parts[0], null, null};
        }
        String middle = parts.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1)) : null;
        return new String[]{parts[0], middle, parts[parts.length - 1]};
    }

    private static UUID branchId(UserEntity user) {
        return user.getBranch() == null ? null : user.getBranch().getId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static EmployeeSummary toSummary(EmployeeEntity e) {
        return new EmployeeSummary(e.getId(), e.getEmployeeNumber(), e.fullName(), e.getEmail(), e.getPhone(),
                e.getDepartment() == null ? null : e.getDepartment().getId(), e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getPosition() == null ? null : e.getPosition().getId(), e.getPosition() == null ? null : e.getPosition().getTitle(),
                e.getEmploymentType(), e.getEmploymentStatus(), e.getHireDate(), e.primaryBranchId(),
                e.getPrimaryBranch() == null ? null : e.getPrimaryBranch().getName(), e.getUserId() != null);
    }

    /** Rota entries and time-clock records made under a login before it was linked now belong to the employee too. */
    private void attachLoginHistory(UUID userId, UUID employeeId) {
        scheduleRepository.attachEmployee(userId, employeeId);
        timeEntryRepository.attachEmployee(userId, employeeId);
    }

    private static UUID positionId(EmployeeEntity e) {
        return e.getPosition() == null ? null : e.getPosition().getId();
    }

    private EmployeeDetail toDetail(EmployeeEntity e, boolean manageable) {
        LinkedUser linkedUser = e.getUserId() == null ? null : userRepository.findById(e.getUserId())
                .map(u -> new LinkedUser(u.getId(), u.getName(), u.getRole().name(), u.isActive()))
                .orElse(null);
        EmployeeEntity manager = e.getReportingManager();
        return new EmployeeDetail(toSummary(e), e.getFirstName(), e.getMiddleName(), e.getLastName(), e.getGender(), e.getDateOfBirth(),
                e.getAddress(), e.getExitDate(), e.getExitReason(),
                manager == null ? null : manager.getId(), manager == null ? null : manager.fullName(),
                e.getQualifications(), e.getExperience(),
                e.getEmergencyContacts().stream().map(c -> new EmergencyContactView(c.getId(), c.getName(), c.getRelationship(), c.getPhone(), c.getEmail())).toList(),
                linkedUser, manageable, e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static AuditEntry toAuditEntry(AuditLogEntity a) {
        return new AuditEntry(a.getId(), a.getAction().name(), a.getActor() == null ? null : a.getActor().getName(), a.getMetadata(), a.getCreatedAt());
    }

    // ------------------------------------------------------------ DTOs

    public record EmployeeFilter(String q, UUID branchId, UUID departmentId, UUID positionId, EmploymentStatus status,
                                 EmploymentType type, boolean includeExited) {}

    public record EmergencyContactRequest(@NotBlank @Size(max = 120) String name,
                                          @Size(max = 60) String relationship,
                                          @NotBlank @Size(max = 20) @Pattern(regexp = "[+0-9 ()-]{6,20}", message = "Enter a valid phone number") String phone,
                                          @Email @Size(max = 255) String email) {}

    public record EmployeeRequest(
            @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9-]*", message = "Use letters, numbers and dashes") String employeeNumber,
            UUID userId,
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String middleName,
            @Size(max = 80) String lastName,
            Gender gender,
            @Past LocalDate dateOfBirth,
            @Email @Size(max = 255) String email,
            @Size(max = 20) @Pattern(regexp = "^$|[+0-9 ()-]{6,20}", message = "Enter a valid phone number") String phone,
            @Size(max = 500) String address,
            UUID departmentId,
            UUID positionId,
            @NotNull EmploymentType employmentType,
            @NotNull LocalDate hireDate,
            UUID reportingManagerId,
            UUID primaryBranchId,
            @Size(max = 2000) String qualifications,
            @Size(max = 2000) String experience,
            @Size(max = 5) List<@Valid EmergencyContactRequest> emergencyContacts,
            Long version) {}

    public record StatusRequest(@NotNull EmploymentStatus status, @PastOrPresent LocalDate effectiveDate, @Size(max = 500) String reason) {}

    public record LinkUserRequest(@NotNull UUID userId) {}

    public record TransferRequest(UUID toBranchId, @PastOrPresent LocalDate effectiveDate, @Size(max = 500) String reason) {}

    /** Defaults applied to every imported record; HR reviews and corrects each record afterwards. */
    public record ImportRequest(@NotNull EmploymentType employmentType, @NotNull @PastOrPresent LocalDate hireDate) {}

    public record ImportSkip(UUID userId, String name, String reason) {}

    public record ImportResult(int created, List<UUID> employeeIds, List<ImportSkip> skipped) {}

    public record EmployeeSummary(UUID id, String employeeNumber, String fullName, String email, String phone,
                                  UUID departmentId, String departmentName, UUID positionId, String positionTitle,
                                  EmploymentType employmentType, EmploymentStatus employmentStatus, LocalDate hireDate,
                                  UUID primaryBranchId, String primaryBranchName, boolean hasUserAccount) {}

    public record EmergencyContactView(UUID id, String name, String relationship, String phone, String email) {}

    public record LinkedUser(UUID id, String name, String role, boolean active) {}

    public record LinkableUser(UUID id, String name, String phone, String email, String role, UUID branchId) {}

    public record EmployeeDetail(EmployeeSummary summary, String firstName, String middleName, String lastName, Gender gender,
                                 LocalDate dateOfBirth, String address, LocalDate exitDate, String exitReason,
                                 UUID reportingManagerId, String reportingManagerName, String qualifications, String experience,
                                 List<EmergencyContactView> emergencyContacts, LinkedUser linkedUser,
                                 boolean manageable, long version,
                                 Instant createdAt, Instant updatedAt) {}

    public record AuditEntry(UUID id, String action, String actorName, String metadata, Instant createdAt) {}
}
