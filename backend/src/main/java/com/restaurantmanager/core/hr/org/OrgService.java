package com.restaurantmanager.core.hr.org;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Departments and positions. Neither is ever deleted: archiving keeps historical references intact. */
@Service
public class OrgService {
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public OrgService(DepartmentRepository departmentRepository, PositionRepository positionRepository,
                      EmployeeRepository employeeRepository, BranchRepository branchRepository,
                      HrAccessService accessService, HrAuditService audit) {
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.accessService = accessService;
        this.audit = audit;
    }

    // ------------------------------------------------------------ departments

    @Transactional(readOnly = true)
    public List<DepartmentView> departments(UserPrincipal principal, boolean includeArchived) {
        HrAccess access = accessService.requireAny(principal, HrPermission.EMPLOYEE_VIEW, HrPermission.DEPARTMENT_MANAGE,
                HrPermission.POSITION_MANAGE);
        List<DepartmentEntity> rows = departmentRepository.findAllByOrderByNameAsc().stream()
                .filter(d -> includeArchived || d.isActive())
                .filter(d -> d.getBranchId() == null || access.canSeeBranch(d.getBranchId()))
                .toList();
        Map<UUID, String> heads = headNames(rows);
        return rows.stream().map(d -> toView(d, heads.get(d.getHeadEmployeeId()))).toList();
    }

    @Transactional
    public DepartmentView saveDepartment(UUID id, DepartmentRequest request, UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.DEPARTMENT_MANAGE);
        String code = normalizeCode(request.code());
        DepartmentEntity department = id == null ? new DepartmentEntity() : loadDepartment(id, access);
        boolean duplicate = id == null ? departmentRepository.existsByCodeIgnoreCase(code)
                : departmentRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) {
            throw new ApiException(409, "A department with code " + code + " already exists");
        }
        if (request.branchId() != null) {
            access.assertBranch(request.branchId());
            if (!branchRepository.existsById(request.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        } else if (!access.allBranches()) {
            throw new ApiException(403, "Only central HR can manage organisation-wide departments");
        }
        if (request.headEmployeeId() != null && !employeeRepository.existsById(request.headEmployeeId())) {
            throw new ApiException(404, "Department head not found");
        }
        department.setCode(code);
        department.setName(request.name().trim());
        department.setCategory(request.category());
        department.setDescription(blankToNull(request.description()));
        department.setBranchId(request.branchId());
        department.setHeadEmployeeId(request.headEmployeeId());
        DepartmentEntity saved = departmentRepository.save(department);
        audit.log(access.userId(), AuditAction.HR_DEPARTMENT_CHANGED, "Department", saved.getId(),
                Map.of("operation", id == null ? "created" : "updated", "code", code));
        return toView(saved, headNames(List.of(saved)).get(saved.getHeadEmployeeId()));
    }

    @Transactional
    public DepartmentView setDepartmentActive(UUID id, boolean active, UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.DEPARTMENT_MANAGE);
        DepartmentEntity department = loadDepartment(id, access);
        if (department.getBranchId() == null && !access.allBranches()) {
            throw new ApiException(403, "Only central HR can manage organisation-wide departments");
        }
        department.setActive(active);
        audit.log(access.userId(), AuditAction.HR_DEPARTMENT_CHANGED, "Department", id,
                Map.of("operation", active ? "restored" : "archived"));
        return toView(department, headNames(List.of(department)).get(department.getHeadEmployeeId()));
    }

    // ------------------------------------------------------------ positions

    @Transactional(readOnly = true)
    public List<PositionView> positions(UserPrincipal principal, boolean includeArchived) {
        HrAccess access = accessService.requireAny(principal, HrPermission.EMPLOYEE_VIEW, HrPermission.DEPARTMENT_MANAGE,
                HrPermission.POSITION_MANAGE);
        return positionRepository.findAllByOrderByTitleAsc().stream()
                .filter(p -> includeArchived || p.isActive())
                .filter(p -> p.getDepartment() == null || p.getDepartment().getBranchId() == null
                        || access.canSeeBranch(p.getDepartment().getBranchId()))
                .map(OrgService::toView)
                .toList();
    }

    @Transactional
    public PositionView savePosition(UUID id, PositionRequest request, UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.POSITION_MANAGE);
        String code = normalizeCode(request.code());
        PositionEntity position = id == null ? new PositionEntity()
                : positionRepository.findById(id).orElseThrow(() -> new ApiException(404, "Position not found"));
        boolean duplicate = id == null ? positionRepository.existsByCodeIgnoreCase(code)
                : positionRepository.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicate) {
            throw new ApiException(409, "A position with code " + code + " already exists");
        }
        DepartmentEntity department = request.departmentId() == null ? null : loadDepartment(request.departmentId(), access);
        if (department != null && !department.isActive()) {
            throw new ApiException(400, "Department " + department.getName() + " is archived");
        }
        position.setCode(code);
        position.setTitle(request.title().trim());
        position.setDepartment(department);
        position.setCategory(request.category() != null ? request.category()
                : department != null ? department.getCategory() : StaffCategory.OTHER);
        position.setDescription(blankToNull(request.description()));
        position.setRequirements(blankToNull(request.requirements()));
        PositionEntity saved = positionRepository.save(position);
        audit.log(access.userId(), AuditAction.HR_POSITION_CHANGED, "Position", saved.getId(),
                Map.of("operation", id == null ? "created" : "updated", "code", code));
        return toView(saved);
    }

    @Transactional
    public PositionView setPositionActive(UUID id, boolean active, UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.POSITION_MANAGE);
        PositionEntity position = positionRepository.findById(id).orElseThrow(() -> new ApiException(404, "Position not found"));
        if (active && position.getDepartment() != null && !position.getDepartment().isActive()) {
            throw new ApiException(400, "Restore the department first");
        }
        position.setActive(active);
        audit.log(access.userId(), AuditAction.HR_POSITION_CHANGED, "Position", id,
                Map.of("operation", active ? "restored" : "archived"));
        return toView(position);
    }

    // ------------------------------------------------------------ helpers

    private DepartmentEntity loadDepartment(UUID id, HrAccess access) {
        DepartmentEntity department = departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Department not found"));
        if (department.getBranchId() != null && !access.canSeeBranch(department.getBranchId())) {
            throw new ApiException(404, "Department not found");
        }
        return department;
    }

    private Map<UUID, String> headNames(List<DepartmentEntity> departments) {
        List<UUID> ids = departments.stream().map(DepartmentEntity::getHeadEmployeeId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> names = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            employeeRepository.findByIdIn(ids).forEach(e -> names.put(e.getId(), e.fullName()));
        }
        return names;
    }

    private static DepartmentView toView(DepartmentEntity d, String headName) {
        return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getCategory(), d.getDescription(), d.getBranchId(),
                d.getHeadEmployeeId(), headName, d.isActive());
    }

    static PositionView toView(PositionEntity p) {
        DepartmentEntity d = p.getDepartment();
        return new PositionView(p.getId(), p.getCode(), p.getTitle(), d == null ? null : d.getId(), d == null ? null : d.getName(),
                p.getCategory(), p.getDescription(), p.getRequirements(), p.isActive());
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------ DTOs

    public record DepartmentRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9 _-]+", message = "Use letters, numbers, spaces, - or _") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull StaffCategory category,
            @Size(max = 1000) String description,
            UUID branchId,
            UUID headEmployeeId) {}

    public record DepartmentView(UUID id, String code, String name, StaffCategory category, String description, UUID branchId,
                                 UUID headEmployeeId, String headEmployeeName, boolean active) {}

    public record PositionRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9 _-]+", message = "Use letters, numbers, spaces, - or _") String code,
            @NotBlank @Size(max = 120) String title,
            UUID departmentId,
            StaffCategory category,
            @Size(max = 2000) String description,
            @Size(max = 2000) String requirements) {}

    public record PositionView(UUID id, String code, String title, UUID departmentId, String departmentName,
                               StaffCategory category, String description, String requirements, boolean active) {}
}
