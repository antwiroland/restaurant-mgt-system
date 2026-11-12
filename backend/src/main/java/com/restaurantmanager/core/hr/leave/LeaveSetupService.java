package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmploymentType;
import com.restaurantmanager.core.hr.schedule.ShiftTemplateEntity;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Leave types (organisation-wide configuration) and effective-dated entitlement policies. */
@Service
public class LeaveSetupService {
    private final LeaveTypeRepository typeRepository;
    private final LeavePolicyRepository policyRepository;
    private final BranchRepository branchRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final Set<DayOfWeek> nonWorkingDays;

    public LeaveSetupService(LeaveTypeRepository typeRepository, LeavePolicyRepository policyRepository, BranchRepository branchRepository,
                             HrAccessService accessService, HrAuditService audit,
                             @Value("${hr.leave.non-working-days:SAT,SUN}") String nonWorkingDays) {
        this.typeRepository = typeRepository;
        this.policyRepository = policyRepository;
        this.branchRepository = branchRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.nonWorkingDays = parseDays(nonWorkingDays);
    }

    /** Weekdays skipped when a leave type counts WORKDAYS. */
    public Set<DayOfWeek> nonWorkingDays() {
        return nonWorkingDays;
    }

    // ------------------------------------------------------------ types

    @Transactional(readOnly = true)
    public List<TypeView> types(UserPrincipal principal) {
        HrAccess access = accessService.access(principal);
        boolean all = access.has(HrPermission.LEAVE_MANAGE);
        return typeRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .filter(t -> all || t.isActive())
                .map(LeaveSetupService::toView)
                .toList();
    }

    @Transactional
    public TypeView createType(UserPrincipal principal, TypeRequest request) {
        HrAccess access = requireCentral(principal);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (typeRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException(409, "A leave type with this code already exists");
        }
        LeaveTypeEntity type = new LeaveTypeEntity();
        type.setCode(code);
        apply(type, request);
        typeRepository.save(type);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "LeaveType", type.getId(), Map.of("operation", "created", "code", code));
        return toView(type);
    }

    @Transactional
    public TypeView updateType(UserPrincipal principal, UUID id, TypeRequest request) {
        HrAccess access = requireCentral(principal);
        LeaveTypeEntity type = typeRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave type not found"));
        if (!type.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new ApiException(400, "The code of a leave type cannot change");
        }
        apply(type, request);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "LeaveType", type.getId(), Map.of("operation", "updated", "code", type.getCode()));
        return toView(type);
    }

    private static void apply(LeaveTypeEntity type, TypeRequest r) {
        Set<ApprovalStep> distinct = new LinkedHashSet<>(r.approvalSteps());
        if (distinct.size() != r.approvalSteps().size()) {
            throw new ApiException(400, "Each approval step can appear only once");
        }
        type.setName(r.name().trim());
        type.setPaid(r.paid());
        type.setDeductsBalance(r.deductsBalance());
        type.setRequiresDocument(r.requiresDocument());
        type.setAllowNegative(r.deductsBalance() && r.allowNegative());
        type.setCountBasis(r.countBasis());
        type.setApprovalSteps(LeaveTypeEntity.formatSteps(List.copyOf(distinct)));
        type.setMaxDaysPerRequest(r.maxDaysPerRequest());
        type.setActive(r.active() == null || r.active());
        type.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
    }

    public LeaveTypeEntity loadType(UUID id) {
        return typeRepository.findById(id).orElseThrow(() -> new ApiException(404, "Leave type not found"));
    }

    private HrAccess requireCentral(UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.LEAVE_MANAGE);
        if (!access.allBranches()) {
            throw new ApiException(403, "Leave types apply to every branch; only central HR can change them");
        }
        return access;
    }

    // ------------------------------------------------------------ policies

    @Transactional(readOnly = true)
    public List<PolicyView> policies(UserPrincipal principal, UUID leaveTypeId) {
        HrAccess access = accessService.requireAny(principal, HrPermission.LEAVE_MANAGE, HrPermission.LEAVE_APPROVE);
        List<LeavePolicyEntity> rows = leaveTypeId == null ? policyRepository.findAllByOrderByEffectiveFromDesc()
                : policyRepository.findByLeaveTypeIdOrderByEffectiveFromDesc(leaveTypeId);
        Map<UUID, LeaveTypeEntity> types = new LinkedHashMap<>();
        typeRepository.findAll().forEach(t -> types.put(t.getId(), t));
        return rows.stream()
                .filter(p -> p.getBranchId() == null || access.canSeeBranch(p.getBranchId()))
                .map(p -> toView(p, types.get(p.getLeaveTypeId())))
                .toList();
    }

    @Transactional
    public PolicyView addPolicy(UserPrincipal principal, PolicyRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.LEAVE_MANAGE);
        LeaveTypeEntity type = loadType(r.leaveTypeId());
        if (r.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can set organisation-wide entitlements");
            }
        } else {
            access.assertBranch(r.branchId());
            if (!branchRepository.existsById(r.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        boolean clash = policyRepository.findByLeaveTypeIdOrderByEffectiveFromDesc(type.getId()).stream()
                .anyMatch(p -> p.getEffectiveFrom().equals(r.effectiveFrom())
                        && java.util.Objects.equals(p.getBranchId(), r.branchId())
                        && p.getEmploymentType() == r.employmentType());
        if (clash) {
            throw new ApiException(409, "An entitlement for the same scope already takes effect on that date");
        }
        LeavePolicyEntity policy = new LeavePolicyEntity();
        policy.setLeaveTypeId(type.getId());
        policy.setBranchId(r.branchId());
        policy.setEmploymentType(r.employmentType());
        policy.setAnnualDays(r.annualDays());
        policy.setAccrual(r.accrual());
        policy.setCarryOverMaxDays(r.carryOverMaxDays() == null ? BigDecimal.ZERO : r.carryOverMaxDays());
        policy.setMinServiceDays(r.minServiceDays() == null ? 0 : r.minServiceDays());
        policy.setEffectiveFrom(r.effectiveFrom());
        policy.setCreatedBy(access.userId());
        policyRepository.save(policy);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rule", "leave-entitlement");
        meta.put("leaveType", type.getCode());
        meta.put("branchId", String.valueOf(r.branchId()));
        meta.put("employmentType", String.valueOf(r.employmentType()));
        meta.put("effectiveFrom", r.effectiveFrom().toString());
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "LeavePolicy", policy.getId(), meta);
        return toView(policy, type);
    }

    /**
     * The entitlement that applies to {@code employee} for a leave type in {@code year}: among policies matching the
     * employee's home branch and employment type, the most specific one in force on the reference date (1 January, or the
     * hire date when hired that year). When none is in force yet, the first one taking effect later that year is used, so
     * an entitlement configured mid-year applies to the current year.
     */
    @Transactional(readOnly = true)
    public Optional<LeavePolicyEntity> resolvePolicy(UUID leaveTypeId, EmployeeEntity employee, int year) {
        LocalDate reference = LocalDate.of(year, 1, 1);
        if (employee.getHireDate() != null && employee.getHireDate().getYear() == year) {
            reference = employee.getHireDate();
        }
        List<LeavePolicyEntity> matching = policyRepository.findByLeaveTypeIdOrderByEffectiveFromDesc(leaveTypeId).stream()
                .filter(p -> p.getBranchId() == null || p.getBranchId().equals(employee.primaryBranchId()))
                .filter(p -> p.getEmploymentType() == null || p.getEmploymentType() == employee.getEmploymentType())
                .toList();
        LocalDate ref = reference;
        Optional<LeavePolicyEntity> inForce = matching.stream()
                .filter(p -> !p.getEffectiveFrom().isAfter(ref))
                .max(Comparator.comparingInt(LeavePolicyEntity::specificity).thenComparing(LeavePolicyEntity::getEffectiveFrom));
        if (inForce.isPresent()) {
            return inForce;
        }
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        return matching.stream()
                .filter(p -> p.getEffectiveFrom().isAfter(ref) && !p.getEffectiveFrom().isAfter(yearEnd))
                .min(Comparator.comparing(LeavePolicyEntity::getEffectiveFrom)
                        .thenComparing(Comparator.comparingInt(LeavePolicyEntity::specificity).reversed()));
    }

    static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (csv != null) {
            for (String d : csv.split(",")) {
                if (!d.isBlank()) {
                    days.add(ShiftTemplateEntity.parseDay(d.trim()));
                }
            }
        }
        return days;
    }

    static TypeView toView(LeaveTypeEntity t) {
        return new TypeView(t.getId(), t.getCode(), t.getName(), t.isPaid(), t.isDeductsBalance(), t.isRequiresDocument(), t.isAllowNegative(),
                t.getCountBasis(), t.steps(), t.getMaxDaysPerRequest(), t.isActive(), t.getDisplayOrder());
    }

    private static PolicyView toView(LeavePolicyEntity p, LeaveTypeEntity type) {
        return new PolicyView(p.getId(), p.getLeaveTypeId(), type == null ? null : type.getName(), p.getBranchId(), p.getEmploymentType(),
                p.getAnnualDays(), p.getAccrual(), p.getCarryOverMaxDays(), p.getMinServiceDays(), p.getEffectiveFrom(), p.getCreatedAt());
    }

    // ------------------------------------------------------------ DTOs

    public record TypeRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{2,30}", message = "Use 2–30 letters, digits or underscores") String code,
                              @NotBlank @Size(max = 80) String name,
                              boolean paid, boolean deductsBalance, boolean requiresDocument, boolean allowNegative,
                              @NotNull CountBasis countBasis,
                              @NotEmpty @Size(max = 3) List<@NotNull ApprovalStep> approvalSteps,
                              @DecimalMin("0.5") @DecimalMax("366") BigDecimal maxDaysPerRequest,
                              Boolean active, @Min(0) @Max(999) Integer displayOrder) {}

    public record TypeView(UUID id, String code, String name, boolean paid, boolean deductsBalance, boolean requiresDocument,
                           boolean allowNegative, CountBasis countBasis, List<ApprovalStep> approvalSteps, BigDecimal maxDaysPerRequest,
                           boolean active, int displayOrder) {}

    public record PolicyRequest(@NotNull UUID leaveTypeId, UUID branchId, EmploymentType employmentType,
                                @NotNull @DecimalMin("0") @DecimalMax("366") BigDecimal annualDays,
                                @NotNull Accrual accrual,
                                @DecimalMin("0") @DecimalMax("366") BigDecimal carryOverMaxDays,
                                @Min(0) @Max(3650) Integer minServiceDays,
                                @NotNull LocalDate effectiveFrom) {}

    public record PolicyView(UUID id, UUID leaveTypeId, String leaveTypeName, UUID branchId, EmploymentType employmentType,
                             BigDecimal annualDays, Accrual accrual, BigDecimal carryOverMaxDays, int minServiceDays,
                             LocalDate effectiveFrom, Instant createdAt) {}
}
