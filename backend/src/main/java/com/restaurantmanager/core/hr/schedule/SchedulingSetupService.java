package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.org.DepartmentRepository;
import com.restaurantmanager.core.hr.org.PositionRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shift templates and scheduling policies (rest, shift length and weekly limits). */
@Service
public class SchedulingSetupService {
    private final ShiftTemplateRepository templateRepository;
    private final SchedulingPolicyRepository policyRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ScheduleRules.Policy defaults;

    public SchedulingSetupService(ShiftTemplateRepository templateRepository, SchedulingPolicyRepository policyRepository,
                                  BranchRepository branchRepository, DepartmentRepository departmentRepository,
                                  PositionRepository positionRepository, HrAccessService accessService, HrAuditService audit,
                                  @Value("${hr.scheduling.default-min-rest-minutes:480}") int defaultRest,
                                  @Value("${hr.scheduling.default-max-shift-minutes:720}") int defaultShift,
                                  @Value("${hr.scheduling.default-max-weekly-minutes:2880}") int defaultWeekly) {
        this.templateRepository = templateRepository;
        this.policyRepository = policyRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.defaults = new ScheduleRules.Policy(defaultRest, defaultShift, defaultWeekly, "CONFIGURATION");
    }

    // ------------------------------------------------------------ policy

    /** Branch row, else organisation default row, else the configured defaults. */
    @Transactional(readOnly = true)
    public ScheduleRules.Policy effectivePolicy(UUID branchId) {
        if (branchId != null) {
            var branchPolicy = policyRepository.findByBranchId(branchId);
            if (branchPolicy.isPresent()) {
                return toPolicy(branchPolicy.get(), "BRANCH");
            }
        }
        return policyRepository.findFirstByBranchIdIsNull().map(p -> toPolicy(p, "ORGANISATION")).orElse(defaults);
    }

    @Transactional(readOnly = true)
    public ScheduleRules.Policy policy(UserPrincipal principal, UUID branchId) {
        HrAccess access = accessService.requireAny(principal, HrPermission.SCHEDULE_VIEW, HrPermission.SETTINGS_MANAGE);
        if (branchId != null) {
            access.assertBranch(branchId);
        }
        return effectivePolicy(branchId);
    }

    @Transactional
    public ScheduleRules.Policy savePolicy(UserPrincipal principal, PolicyRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (request.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can change the organisation default");
            }
        } else {
            access.assertBranch(request.branchId());
            if (!branchRepository.existsById(request.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        SchedulingPolicyEntity policy = (request.branchId() == null ? policyRepository.findFirstByBranchIdIsNull()
                : policyRepository.findByBranchId(request.branchId())).orElseGet(SchedulingPolicyEntity::new);
        policy.setBranchId(request.branchId());
        policy.setMinRestMinutes(request.minRestMinutes());
        policy.setMaxShiftMinutes(request.maxShiftMinutes());
        policy.setMaxWeeklyMinutes(request.maxWeeklyMinutes());
        policy.setUpdatedBy(access.userId());
        policyRepository.save(policy);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "SchedulingPolicy", policy.getId(),
                Map.of("branchId", String.valueOf(request.branchId()), "minRestMinutes", request.minRestMinutes(),
                        "maxShiftMinutes", request.maxShiftMinutes(), "maxWeeklyMinutes", request.maxWeeklyMinutes()));
        return effectivePolicy(request.branchId());
    }

    private static ScheduleRules.Policy toPolicy(SchedulingPolicyEntity p, String source) {
        return new ScheduleRules.Policy(p.getMinRestMinutes(), p.getMaxShiftMinutes(), p.getMaxWeeklyMinutes(), source);
    }

    // ------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public List<TemplateView> templates(UserPrincipal principal, UUID branchId, boolean includeArchived) {
        HrAccess access = accessService.requireAny(principal, HrPermission.SCHEDULE_VIEW, HrPermission.SCHEDULE_MANAGE);
        if (branchId != null) {
            access.assertBranch(branchId);
        }
        return templateRepository.findAllByOrderByStartTimeAscNameAsc().stream()
                .filter(t -> includeArchived || t.isActive())
                .filter(t -> t.getBranchId() == null || access.canSeeBranch(t.getBranchId()))
                .filter(t -> branchId == null || t.getBranchId() == null || t.getBranchId().equals(branchId))
                .map(SchedulingSetupService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftTemplateEntity loadTemplate(UUID id, HrAccess access) {
        ShiftTemplateEntity template = templateRepository.findById(id).orElseThrow(() -> new ApiException(404, "Shift template not found"));
        if (template.getBranchId() != null && !access.canSeeBranch(template.getBranchId())) {
            throw new ApiException(404, "Shift template not found");
        }
        return template;
    }

    @Transactional
    public TemplateView saveTemplate(UserPrincipal principal, UUID id, TemplateRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        ShiftTemplateEntity template = id == null ? new ShiftTemplateEntity() : loadTemplate(id, access);
        if (request.branchId() == null && !access.allBranches()) {
            throw new ApiException(403, "Only central HR can create templates for every branch");
        }
        if (request.branchId() != null) {
            access.assertBranch(request.branchId());
        }
        if (request.startTime().equals(request.endTime())) {
            throw new ApiException(400, "Start and end time cannot be the same");
        }
        if (request.maxStaff() != null && request.maxStaff() < request.minStaff()) {
            throw new ApiException(400, "Maximum staff cannot be below the minimum");
        }
        if (request.departmentId() != null && !departmentRepository.existsById(request.departmentId())) {
            throw new ApiException(404, "Department not found");
        }
        if (request.positionId() != null && !positionRepository.existsById(request.positionId())) {
            throw new ApiException(404, "Position not found");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String day : request.days()) {
            try {
                days.add(ShiftTemplateEntity.parseDay(day));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(400, "Unknown day " + day);
            }
        }
        template.setName(request.name().trim());
        template.setShiftType(request.shiftType());
        template.setBranchId(request.branchId());
        template.setDepartmentId(request.departmentId());
        template.setPositionId(request.positionId());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setBreakMinutes(request.breakMinutes());
        template.setDays(days);
        template.setMinStaff(request.minStaff());
        template.setMaxStaff(request.maxStaff());
        ShiftTemplateEntity saved = templateRepository.save(template);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "ShiftTemplate", saved.getId(),
                Map.of("operation", id == null ? "created" : "updated", "name", saved.getName()));
        return toView(saved);
    }

    @Transactional
    public TemplateView setTemplateActive(UserPrincipal principal, UUID id, boolean active) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        ShiftTemplateEntity template = loadTemplate(id, access);
        if (template.getBranchId() == null && !access.allBranches()) {
            throw new ApiException(403, "Only central HR can change templates for every branch");
        }
        template.setActive(active);
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "ShiftTemplate", id, Map.of("operation", active ? "restored" : "archived"));
        return toView(template);
    }

    static TemplateView toView(ShiftTemplateEntity t) {
        return new TemplateView(t.getId(), t.getName(), t.getShiftType(), t.getBranchId(), t.getDepartmentId(), t.getPositionId(),
                t.getStartTime(), t.getEndTime(), t.crossesMidnight(), t.getBreakMinutes(),
                t.days().stream().sorted().map(ShiftTemplateEntity::formatDay).toList(), t.getMinStaff(), t.getMaxStaff(), t.isActive());
    }

    public record PolicyRequest(UUID branchId,
                                @Min(0) @Max(2880) int minRestMinutes,
                                @Min(60) @Max(1440) int maxShiftMinutes,
                                @Min(60) @Max(10080) int maxWeeklyMinutes) {}

    public record TemplateRequest(@NotBlank @Size(max = 80) String name, @NotNull ShiftType shiftType, UUID branchId,
                                  UUID departmentId, UUID positionId, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                  @Min(0) @Max(240) int breakMinutes, @NotEmpty Set<String> days,
                                  @Min(0) @Max(200) int minStaff, @Min(0) @Max(200) Integer maxStaff) {}

    public record TemplateView(UUID id, String name, ShiftType shiftType, UUID branchId, UUID departmentId, UUID positionId,
                               LocalTime startTime, LocalTime endTime, boolean overnight, int breakMinutes, List<String> days,
                               int minStaff, Integer maxStaff, boolean active) {}
}
