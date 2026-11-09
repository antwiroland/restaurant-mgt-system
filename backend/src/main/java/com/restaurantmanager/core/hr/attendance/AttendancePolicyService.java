package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Attendance policy: branch row, else organisation row, else configured defaults. */
@Service
public class AttendancePolicyService {
    private final AttendancePolicyRepository repository;
    private final BranchRepository branchRepository;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final AttendanceCalculator.Policy defaults;

    public AttendancePolicyService(AttendancePolicyRepository repository, BranchRepository branchRepository, HrAccessService accessService,
                                   HrAuditService audit,
                                   @Value("${hr.attendance.default-late-grace-minutes:5}") int lateGrace,
                                   @Value("${hr.attendance.default-early-leave-grace-minutes:5}") int earlyGrace,
                                   @Value("${hr.attendance.default-early-clock-in-minutes:30}") int earlyClockIn,
                                   @Value("${hr.attendance.default-unscheduled-clock-in:ALLOW}") String unscheduled,
                                   @Value("${hr.attendance.default-require-correction-approval:true}") boolean requireApproval,
                                   @Value("${hr.attendance.default-meal-break-paid:false}") boolean mealPaid,
                                   @Value("${hr.attendance.default-rest-break-paid:true}") boolean restPaid,
                                   @Value("${hr.attendance.default-allow-self-clock:true}") boolean selfClock,
                                   @Value("${hr.attendance.default-missed-clock-out-minutes:120}") int missedClockOut) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.accessService = accessService;
        this.audit = audit;
        this.defaults = new AttendanceCalculator.Policy(lateGrace, earlyGrace, earlyClockIn, unscheduled, requireApproval, mealPaid, restPaid,
                selfClock, missedClockOut, "CONFIGURATION");
    }

    @Transactional(readOnly = true)
    public AttendanceCalculator.Policy effective(UUID branchId) {
        if (branchId != null) {
            var row = repository.findByBranchId(branchId);
            if (row.isPresent()) {
                return toPolicy(row.get(), "BRANCH");
            }
        }
        return repository.findFirstByBranchIdIsNull().map(p -> toPolicy(p, "ORGANISATION")).orElse(defaults);
    }

    @Transactional(readOnly = true)
    public AttendanceCalculator.Policy get(UserPrincipal principal, UUID branchId) {
        HrAccess access = accessService.requireAny(principal, HrPermission.ATTENDANCE_VIEW, HrPermission.SETTINGS_MANAGE);
        if (branchId != null) {
            access.assertBranch(branchId);
        }
        return effective(branchId);
    }

    @Transactional
    public AttendanceCalculator.Policy save(UserPrincipal principal, PolicyRequest r) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (r.branchId() == null) {
            if (!access.allBranches()) {
                throw new ApiException(403, "Only central HR can change the organisation default");
            }
        } else {
            access.assertBranch(r.branchId());
            if (!branchRepository.existsById(r.branchId())) {
                throw new ApiException(404, "Branch not found");
            }
        }
        AttendancePolicyEntity p = (r.branchId() == null ? repository.findFirstByBranchIdIsNull() : repository.findByBranchId(r.branchId()))
                .orElseGet(AttendancePolicyEntity::new);
        p.setBranchId(r.branchId());
        p.setLateGraceMinutes(r.lateGraceMinutes());
        p.setEarlyLeaveGraceMinutes(r.earlyLeaveGraceMinutes());
        p.setEarlyClockInMinutes(r.earlyClockInMinutes());
        p.setUnscheduledClockIn(r.unscheduledClockIn());
        p.setRequireCorrectionApproval(r.requireCorrectionApproval());
        p.setMealBreakPaid(r.mealBreakPaid());
        p.setRestBreakPaid(r.restBreakPaid());
        p.setAllowSelfClock(r.allowSelfClock());
        p.setMissedClockOutMinutes(r.missedClockOutMinutes());
        p.setUpdatedBy(access.userId());
        repository.save(p);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("policy", "attendance");
        meta.put("branchId", String.valueOf(r.branchId()));
        meta.put("unscheduledClockIn", r.unscheduledClockIn());
        meta.put("requireCorrectionApproval", r.requireCorrectionApproval());
        audit.log(access.userId(), AuditAction.HR_SETTINGS_CHANGED, "AttendancePolicy", p.getId(), meta);
        return effective(r.branchId());
    }

    private static AttendanceCalculator.Policy toPolicy(AttendancePolicyEntity p, String source) {
        return new AttendanceCalculator.Policy(p.getLateGraceMinutes(), p.getEarlyLeaveGraceMinutes(), p.getEarlyClockInMinutes(),
                p.getUnscheduledClockIn(), p.isRequireCorrectionApproval(), p.isMealBreakPaid(), p.isRestBreakPaid(), p.isAllowSelfClock(),
                p.getMissedClockOutMinutes(), source);
    }

    public record PolicyRequest(UUID branchId,
                                @Min(0) @Max(120) int lateGraceMinutes,
                                @Min(0) @Max(120) int earlyLeaveGraceMinutes,
                                @Min(0) @Max(240) int earlyClockInMinutes,
                                @NotNull @Pattern(regexp = "ALLOW|BLOCK") String unscheduledClockIn,
                                boolean requireCorrectionApproval, boolean mealBreakPaid, boolean restBreakPaid, boolean allowSelfClock,
                                @Min(15) @Max(1440) int missedClockOutMinutes) {}
}
