package com.restaurantmanager.core.hr.branch;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.hr.employee.EmploymentStatus;
import com.restaurantmanager.core.hr.org.PositionEntity;
import com.restaurantmanager.core.hr.org.PositionRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secondary and temporary branch assignments, assignment history and the branch workforce summary. */
@Service
public class BranchAssignmentService {
    private final BranchAssignmentRepository repository;
    private final BranchAssignmentRecorder recorder;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final PositionRepository positionRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ZoneId zone;

    public BranchAssignmentService(BranchAssignmentRepository repository, BranchAssignmentRecorder recorder, EmployeeService employeeService,
                                   EmployeeRepository employeeRepository, BranchRepository branchRepository,
                                   PositionRepository positionRepository, HrAccessService accessService, HrAuditService audit,
                                   @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.repository = repository;
        this.recorder = recorder;
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.positionRepository = positionRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> history(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.require(principal, HrPermission.EMPLOYEE_VIEW);
        employeeService.loadVisible(employeeId, access);
        List<BranchAssignmentEntity> rows = repository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId);
        return toViews(rows);
    }

    @Transactional
    public AssignmentView assign(UserPrincipal principal, UUID employeeId, AssignmentRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.BRANCH_ASSIGN);
        EmployeeEntity employee = employeeService.loadInScope(employeeId, access);
        access.assertBranch(request.branchId());
        if (request.assignmentType() == AssignmentType.PRIMARY) {
            throw new ApiException(400, "Change the primary branch with a transfer");
        }
        if (employee.getEmploymentStatus().isExit()) {
            throw new ApiException(400, "Former employees cannot be assigned to branches");
        }
        BranchEntity branch = branchRepository.findById(request.branchId()).orElseThrow(() -> new ApiException(404, "Branch not found"));
        if (!branch.isActive()) {
            throw new ApiException(400, "Branch " + branch.getName() + " is inactive");
        }
        LocalDate start = request.startDate() != null ? request.startDate() : LocalDate.now(zone);
        if (start.isBefore(employee.getHireDate())) {
            throw new ApiException(400, "An assignment cannot start before the hire date");
        }
        if (request.endDate() != null && request.endDate().isBefore(start)) {
            throw new ApiException(400, "End date cannot be before the start date");
        }
        if (request.assignmentType() == AssignmentType.TEMPORARY && request.endDate() == null) {
            throw new ApiException(400, "A temporary assignment needs an end date");
        }
        if (branch.getId().equals(employee.primaryBranchId())) {
            throw new ApiException(400, "This is already the employee's primary branch");
        }
        if (!repository.findOverlapping(employeeId, branch.getId(), start, request.endDate()).isEmpty()) {
            throw new ApiException(409, "The employee already has an assignment at " + branch.getName() + " in this period");
        }
        if (request.positionId() != null) {
            PositionEntity position = positionRepository.findById(request.positionId())
                    .orElseThrow(() -> new ApiException(404, "Position not found"));
            if (!position.isActive()) {
                throw new ApiException(400, "Position " + position.getTitle() + " is archived");
            }
        }
        BranchAssignmentEntity assignment = new BranchAssignmentEntity();
        assignment.setEmployeeId(employeeId);
        assignment.setBranchId(branch.getId());
        assignment.setAssignmentType(request.assignmentType());
        assignment.setPositionId(request.positionId());
        assignment.setStartDate(start);
        assignment.setEndDate(request.endDate());
        assignment.setReason(blankToNull(request.reason()));
        assignment.setCreatedBy(access.userId());
        BranchAssignmentEntity saved = repository.save(assignment);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "assigned");
        meta.put("assignmentId", saved.getId().toString());
        meta.put("branch", branch.getName());
        meta.put("type", saved.getAssignmentType().name());
        meta.put("from", start.toString());
        meta.put("until", String.valueOf(saved.getEndDate()));
        audit.log(access.userId(), AuditAction.HR_BRANCH_ASSIGNMENT_CHANGED, "Employee", employeeId, meta);
        return toViews(List.of(saved)).get(0);
    }

    @Transactional
    public AssignmentView end(UserPrincipal principal, UUID assignmentId, EndRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.BRANCH_ASSIGN);
        BranchAssignmentEntity assignment = repository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(404, "Assignment not found"));
        try {
            employeeService.loadInScope(assignment.getEmployeeId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Assignment not found");
        }
        if (assignment.getAssignmentType() == AssignmentType.PRIMARY) {
            throw new ApiException(400, "The primary branch is changed with a transfer");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate endDate = request.endDate() != null ? request.endDate() : today;
        if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(today)) {
            throw new ApiException(409, "This assignment has already ended");
        }
        if (endDate.isBefore(assignment.getStartDate())) {
            throw new ApiException(400, "End date cannot be before the start date");
        }
        if (assignment.getEndDate() != null && endDate.isAfter(assignment.getEndDate())) {
            throw new ApiException(400, "Use a new assignment to extend a temporary assignment");
        }
        recorder.close(assignment, endDate, blankToNull(request.reason()), access.userId());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "ended");
        meta.put("assignmentId", assignmentId.toString());
        meta.put("until", endDate.toString());
        if (blankToNull(request.reason()) != null) {
            meta.put("reason", request.reason().trim());
        }
        audit.log(access.userId(), AuditAction.HR_BRANCH_ASSIGNMENT_CHANGED, "Employee", assignment.getEmployeeId(), meta);
        return toViews(List.of(assignment)).get(0);
    }

    /** Headcount per branch the caller can see. Counts come from grouped queries, not by loading employees. */
    @Transactional(readOnly = true)
    public List<BranchWorkforce> workforce(UserPrincipal principal) {
        HrAccess access = accessService.requireAny(principal, HrPermission.BRANCH_VIEW, HrPermission.EMPLOYEE_VIEW);
        Map<UUID, Map<EmploymentStatus, Long>> byBranch = new HashMap<>();
        for (Object[] row : employeeRepository.countByPrimaryBranchAndStatus()) {
            byBranch.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(EmploymentStatus.class))
                    .merge((EmploymentStatus) row[1], (Long) row[2], Long::sum);
        }
        Map<UUID, Long> covering = new HashMap<>();
        for (Object[] row : repository.countActiveSecondaryByBranch(LocalDate.now(zone))) {
            covering.put((UUID) row[0], (Long) row[1]);
        }
        List<BranchWorkforce> result = branchRepository.findAll().stream()
                .filter(b -> access.canSeeBranch(b.getId()))
                .sorted(Comparator.comparing(BranchEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(b -> toWorkforce(b.getId(), b.getCode(), b.getName(), b.isActive(), byBranch.get(b.getId()), covering.getOrDefault(b.getId(), 0L)))
                .collect(Collectors.toList());
        if (access.allBranches() && byBranch.containsKey(null)) {
            result.add(toWorkforce(null, null, "No branch (head office)", true, byBranch.get(null), 0));
        }
        return result;
    }

    private static BranchWorkforce toWorkforce(UUID id, String code, String name, boolean active, Map<EmploymentStatus, Long> counts, long covering) {
        Map<EmploymentStatus, Long> c = counts == null ? Map.of() : counts;
        long current = c.getOrDefault(EmploymentStatus.ACTIVE, 0L) + c.getOrDefault(EmploymentStatus.ON_LEAVE, 0L)
                + c.getOrDefault(EmploymentStatus.SUSPENDED, 0L);
        return new BranchWorkforce(id, code, name, active, current, c.getOrDefault(EmploymentStatus.ACTIVE, 0L),
                c.getOrDefault(EmploymentStatus.ON_LEAVE, 0L), c.getOrDefault(EmploymentStatus.SUSPENDED, 0L), covering);
    }

    private List<AssignmentView> toViews(List<BranchAssignmentEntity> rows) {
        Map<UUID, String> branchNames = branchRepository.findAllById(rows.stream().map(BranchAssignmentEntity::getBranchId).distinct().toList())
                .stream().collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
        Map<UUID, String> positionTitles = positionRepository.findAllById(rows.stream().map(BranchAssignmentEntity::getPositionId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PositionEntity::getId, PositionEntity::getTitle));
        LocalDate today = LocalDate.now(zone);
        return rows.stream().map(a -> new AssignmentView(a.getId(), a.getEmployeeId(), a.getBranchId(), branchNames.get(a.getBranchId()),
                a.getAssignmentType(), a.getPositionId(), a.getPositionId() == null ? null : positionTitles.get(a.getPositionId()),
                a.getStartDate(), a.getEndDate(), a.activeOn(today), a.getReason(), a.getEndReason(), a.getCreatedAt())).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AssignmentRequest(@NotNull UUID branchId, @NotNull AssignmentType assignmentType, UUID positionId,
                                    LocalDate startDate, LocalDate endDate, @Size(max = 500) String reason) {}

    public record EndRequest(LocalDate endDate, @Size(max = 500) String reason) {}

    public record AssignmentView(UUID id, UUID employeeId, UUID branchId, String branchName, AssignmentType assignmentType,
                                 UUID positionId, String positionTitle, LocalDate startDate, LocalDate endDate, boolean current,
                                 String reason, String endReason, Instant createdAt) {}

    public record BranchWorkforce(UUID branchId, String code, String name, boolean active, long currentEmployees,
                                  long activeCount, long onLeave, long suspended, long coveringFromOtherBranches) {}
}
