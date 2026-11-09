package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import com.restaurantmanager.core.workforce.ScheduleStatus;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shift swaps: an employee offers one of their published future shifts to a colleague (optionally taking one of the
 * colleague's shifts in exchange); a manager with {@code hr.shift.swap.approve} for the branch decides.
 */
@Service
public class SwapService {
    private final SwapRequestRepository swapRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final com.restaurantmanager.core.hr.branch.BranchAssignmentRepository assignmentRepository;
    private final ScheduleService scheduleService;
    private final HrAccessService accessService;
    private final HrAuditService audit;
    private final ScheduleNotifier notifier;

    public SwapService(SwapRequestRepository swapRepository, WorkScheduleRepository scheduleRepository, EmployeeRepository employeeRepository,
                       UserRepository userRepository, com.restaurantmanager.core.hr.branch.BranchAssignmentRepository assignmentRepository,
                       ScheduleService scheduleService, HrAccessService accessService, HrAuditService audit,
                       ScheduleNotifier notifier) {
        this.assignmentRepository = assignmentRepository;
        this.swapRepository = swapRepository;
        this.scheduleRepository = scheduleRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.scheduleService = scheduleService;
        this.accessService = accessService;
        this.audit = audit;
        this.notifier = notifier;
    }

    // ------------------------------------------------------------ employee side

    @Transactional
    public SwapView request(UserPrincipal principal, SwapRequest request) {
        EmployeeEntity me = me(principal);
        WorkScheduleEntity shift = scheduleRepository.findById(request.scheduleId())
                .filter(s -> me.getId().equals(s.getEmployeeId()))
                .orElseThrow(() -> new ApiException(404, "Shift not found"));
        requireSwappable(shift, "your shift");
        if (request.colleagueEmployeeId().equals(me.getId())) {
            throw new ApiException(400, "Choose a colleague other than yourself");
        }
        EmployeeEntity colleague = employeeRepository.findById(request.colleagueEmployeeId())
                .orElseThrow(() -> new ApiException(404, "Colleague not found"));
        WorkScheduleEntity theirShift = null;
        if (request.colleagueScheduleId() != null) {
            theirShift = scheduleRepository.findById(request.colleagueScheduleId())
                    .filter(s -> colleague.getId().equals(s.getEmployeeId()))
                    .orElseThrow(() -> new ApiException(404, "Colleague's shift not found"));
            requireSwappable(theirShift, "the colleague's shift");
        }
        if (swapRepository.existsByScheduleIdInAndStatus(Stream.of(shift.getId(), request.colleagueScheduleId()).filter(Objects::nonNull).toList(),
                SwapStatus.PENDING)) {
            throw new ApiException(409, "A swap for this shift is already waiting for approval");
        }
        List<ScheduleRules.Issue> blocking = check(shift, theirShift, me, colleague).stream()
                .filter(i -> i.severity() == ScheduleRules.Severity.BLOCKING).toList();
        if (!blocking.isEmpty()) {
            throw new ScheduleIssuesException("This swap is not possible", blocking);
        }
        SwapRequestEntity swap = new SwapRequestEntity();
        swap.setScheduleId(shift.getId());
        swap.setRequesterEmployeeId(me.getId());
        swap.setColleagueEmployeeId(colleague.getId());
        swap.setColleagueScheduleId(theirShift == null ? null : theirShift.getId());
        swap.setReason(request.reason().trim());
        swap.setCreatedBy(principal.userId());
        SwapRequestEntity saved = swapRepository.save(swap);
        audit.log(principal.userId(), AuditAction.HR_SHIFT_SWAP_REQUESTED, "Schedule", shift.getId(),
                Map.of("swapId", saved.getId().toString(), "colleagueEmployeeId", colleague.getId().toString()));
        notifier.swapRequested(branchManagerUserIds(shift.getBranchId()), me.fullName());
        return views(List.of(saved)).get(0);
    }

    @Transactional
    public SwapView cancel(UserPrincipal principal, UUID swapId) {
        EmployeeEntity me = me(principal);
        SwapRequestEntity swap = swapRepository.findById(swapId).filter(s -> s.getRequesterEmployeeId().equals(me.getId()))
                .orElseThrow(() -> new ApiException(404, "Swap request not found"));
        if (swap.getStatus() != SwapStatus.PENDING) {
            throw new ApiException(409, "Only pending requests can be withdrawn");
        }
        swap.setStatus(SwapStatus.CANCELLED);
        swap.setDecidedAt(Instant.now());
        swap.setDecisionNote("Withdrawn by requester");
        audit.log(principal.userId(), AuditAction.HR_SHIFT_SWAP_DECIDED, "Schedule", swap.getScheduleId(),
                Map.of("swapId", swapId.toString(), "decision", "WITHDRAWN"));
        return views(List.of(swap)).get(0);
    }

    /**
     * Colleagues who could take {@code scheduleId}: assigned to the shift's branch on that day, still employed, with their
     * published shifts in the surrounding fortnight (candidates for an exchange). Only names and shift times are shared.
     */
    @Transactional(readOnly = true)
    public List<SwapOption> options(UserPrincipal principal, UUID scheduleId) {
        EmployeeEntity me = me(principal);
        WorkScheduleEntity shift = scheduleRepository.findById(scheduleId)
                .filter(s -> me.getId().equals(s.getEmployeeId()))
                .orElseThrow(() -> new ApiException(404, "Shift not found"));
        java.time.LocalDate day = shift.getStartsAt().atZone(scheduleService.zone()).toLocalDate();
        List<UUID> ids = assignmentRepository.findEmployeeIdsAtBranch(shift.getBranchId(), day, day);
        Instant from = shift.getStartsAt().minus(java.time.Duration.ofDays(7));
        Instant to = shift.getStartsAt().plus(java.time.Duration.ofDays(7));
        Instant now = Instant.now();
        return employeeRepository.findByIdIn(ids).stream()
                .filter(e -> !e.getId().equals(me.getId()) && e.getEmploymentStatus() == com.restaurantmanager.core.hr.employee.EmploymentStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(EmployeeEntity::fullName))
                .map(e -> new SwapOption(e.getId(), e.fullName(), e.getPosition() == null ? null : e.getPosition().getTitle(),
                        scheduleRepository.findPublishedForEmployee(from, to, e.getId()).stream()
                                .filter(s -> s.getStartsAt().isAfter(now))
                                .map(s -> new SwapOption.Shift(s.getId(), s.getBranchId(), s.getStartsAt(), s.getEndsAt()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SwapView> mine(UserPrincipal principal) {
        EmployeeEntity me = me(principal);
        return views(swapRepository.findByRequesterEmployeeIdOrColleagueEmployeeIdOrderByCreatedAtDesc(me.getId(), me.getId()));
    }

    // ------------------------------------------------------------ manager side

    @Transactional(readOnly = true)
    public List<SwapView> list(UserPrincipal principal, SwapStatus status) {
        HrAccess access = accessService.require(principal, HrPermission.SHIFT_SWAP_APPROVE);
        List<SwapRequestEntity> rows = status == null ? swapRepository.findAllByOrderByCreatedAtDesc() : swapRepository.findByStatusOrderByCreatedAtAsc(status);
        Map<UUID, WorkScheduleEntity> shifts = scheduleRepository.findAllById(rows.stream().map(SwapRequestEntity::getScheduleId).distinct().toList())
                .stream().collect(Collectors.toMap(WorkScheduleEntity::getId, s -> s));
        return views(rows.stream().filter(r -> shifts.containsKey(r.getScheduleId()) && access.canSeeBranch(shifts.get(r.getScheduleId()).getBranchId()))
                .limit(200).toList());
    }

    @Transactional
    public SwapView decide(UserPrincipal principal, UUID swapId, DecisionRequest decision) {
        HrAccess access = accessService.require(principal, HrPermission.SHIFT_SWAP_APPROVE);
        SwapRequestEntity swap = swapRepository.findById(swapId).orElseThrow(() -> new ApiException(404, "Swap request not found"));
        WorkScheduleEntity shift = scheduleRepository.findById(swap.getScheduleId()).orElseThrow(() -> new ApiException(404, "Swap request not found"));
        if (!access.canSeeBranch(shift.getBranchId())) {
            throw new ApiException(404, "Swap request not found");
        }
        if (swap.getStatus() != SwapStatus.PENDING) {
            throw new ApiException(409, "This request has already been decided");
        }
        employeeRepository.findByUserId(access.userId()).ifPresent(approver -> {
            if (approver.getId().equals(swap.getRequesterEmployeeId()) || approver.getId().equals(swap.getColleagueEmployeeId())) {
                throw new ApiException(403, "You cannot decide a swap you are part of");
            }
        });
        EmployeeEntity requester = employeeRepository.findById(swap.getRequesterEmployeeId()).orElseThrow();
        EmployeeEntity colleague = employeeRepository.findById(swap.getColleagueEmployeeId()).orElseThrow();
        WorkScheduleEntity theirShift = swap.getColleagueScheduleId() == null ? null
                : scheduleRepository.findById(swap.getColleagueScheduleId()).orElse(null);

        if (!decision.approve()) {
            close(swap, SwapStatus.REJECTED, access.userId(), decision.note());
            notifier.swapDecided(java.util.Arrays.asList(requester.getUserId(), colleague.getUserId()), false, scheduleService.when(shift.getStartsAt()));
            return views(List.of(swap)).get(0);
        }
        boolean stillValid = requester.getId().equals(shift.getEmployeeId()) && shift.getStatus() == ScheduleStatus.PUBLISHED
                && shift.getStartsAt().isAfter(Instant.now())
                && (swap.getColleagueScheduleId() == null || (theirShift != null && colleague.getId().equals(theirShift.getEmployeeId())
                && theirShift.getStatus() == ScheduleStatus.PUBLISHED && theirShift.getStartsAt().isAfter(Instant.now())));
        if (!stillValid) {
            close(swap, SwapStatus.CANCELLED, access.userId(), "The shifts changed after the request was made");
            throw new ApiException(409, "The shifts changed after this request was made; it has been closed");
        }
        ScheduleService.enforce(check(shift, theirShift, requester, colleague), decision.acknowledgeWarnings());

        shift.setEmployeeId(colleague.getId());
        shift.setUserId(colleague.getUserId());
        if (theirShift != null) {
            theirShift.setEmployeeId(requester.getId());
            theirShift.setUserId(requester.getUserId());
        }
        close(swap, SwapStatus.APPROVED, access.userId(), decision.note());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("operation", "swap-approved");
        meta.put("swapId", swapId.toString());
        meta.put("from", requester.getId().toString());
        meta.put("to", colleague.getId().toString());
        audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", shift.getId(), meta);
        if (theirShift != null) {
            audit.log(access.userId(), AuditAction.HR_SCHEDULE_CHANGED, "Schedule", theirShift.getId(),
                    Map.of("operation", "swap-approved", "swapId", swapId.toString()));
        }
        notifier.swapDecided(java.util.Arrays.asList(requester.getUserId(), colleague.getUserId()), true, scheduleService.when(shift.getStartsAt()));
        return views(List.of(swap)).get(0);
    }

    // ------------------------------------------------------------ helpers

    /** Issues for the resulting schedule: the colleague takes the shift; in an exchange, the requester takes theirs. */
    private List<ScheduleRules.Issue> check(WorkScheduleEntity shift, WorkScheduleEntity theirShift, EmployeeEntity requester, EmployeeEntity colleague) {
        List<ScheduleRules.Issue> issues = new ArrayList<>();
        Set<UUID> excludeForColleague = theirShift == null ? Set.of() : Set.of(theirShift.getId());
        scheduleService.evaluate(colleague, shift.getBranchId(), ScheduleService.interval(shift), excludeForColleague)
                .forEach(i -> issues.add(new ScheduleRules.Issue(i.code(), i.severity(), colleague.fullName() + ": " + i.message())));
        if (theirShift != null) {
            scheduleService.evaluate(requester, theirShift.getBranchId(), ScheduleService.interval(theirShift), Set.of(shift.getId()))
                    .forEach(i -> issues.add(new ScheduleRules.Issue(i.code(), i.severity(), requester.fullName() + ": " + i.message())));
        }
        return issues;
    }

    private static void requireSwappable(WorkScheduleEntity shift, String label) {
        if (shift.getStatus() != ScheduleStatus.PUBLISHED) {
            throw new ApiException(400, "Only published shifts can be swapped (" + label + ")");
        }
        if (!shift.getStartsAt().isAfter(Instant.now())) {
            throw new ApiException(400, "Shifts that have started cannot be swapped (" + label + ")");
        }
    }

    private void close(SwapRequestEntity swap, SwapStatus status, UUID actorId, String note) {
        swap.setStatus(status);
        swap.setDecidedBy(actorId);
        swap.setDecidedAt(Instant.now());
        swap.setDecisionNote(note == null || note.isBlank() ? null : note.trim());
        audit.log(actorId, AuditAction.HR_SHIFT_SWAP_DECIDED, "Schedule", swap.getScheduleId(),
                Map.of("swapId", swap.getId().toString(), "decision", status.name()));
    }

    private List<UUID> branchManagerUserIds(UUID branchId) {
        return userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.MANAGER).stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branchId))
                .map(UserEntity::getId)
                .toList();
    }

    private EmployeeEntity me(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    private List<SwapView> views(List<SwapRequestEntity> rows) {
        List<UUID> shiftIds = rows.stream().flatMap(r -> Stream.of(r.getScheduleId(), r.getColleagueScheduleId())).filter(Objects::nonNull).distinct().toList();
        Map<UUID, WorkScheduleEntity> shifts = new HashMap<>();
        scheduleRepository.findAllById(shiftIds).forEach(s -> shifts.put(s.getId(), s));
        List<UUID> employeeIds = rows.stream().flatMap(r -> Stream.of(r.getRequesterEmployeeId(), r.getColleagueEmployeeId())).distinct().toList();
        Map<UUID, String> names = new HashMap<>();
        employeeRepository.findByIdIn(employeeIds).forEach(e -> names.put(e.getId(), e.fullName()));
        return rows.stream().map(r -> {
            WorkScheduleEntity s = shifts.get(r.getScheduleId());
            WorkScheduleEntity t = r.getColleagueScheduleId() == null ? null : shifts.get(r.getColleagueScheduleId());
            return new SwapView(r.getId(), r.getScheduleId(), s == null ? null : s.getBranchId(), s == null ? null : s.getStartsAt(),
                    s == null ? null : s.getEndsAt(), r.getRequesterEmployeeId(), names.get(r.getRequesterEmployeeId()),
                    r.getColleagueEmployeeId(), names.get(r.getColleagueEmployeeId()), r.getColleagueScheduleId(),
                    t == null ? null : t.getStartsAt(), t == null ? null : t.getEndsAt(), r.getReason(), r.getStatus(),
                    r.getDecisionNote(), r.getDecidedAt(), r.getCreatedAt());
        }).toList();
    }

    public record SwapRequest(@NotNull UUID scheduleId, @NotNull UUID colleagueEmployeeId, UUID colleagueScheduleId,
                              @NotBlank @Size(max = 500) String reason) {}

    public record SwapOption(UUID employeeId, String name, String positionTitle, List<Shift> shifts) {
        public record Shift(UUID id, UUID branchId, Instant startsAt, Instant endsAt) {}
    }

    public record DecisionRequest(boolean approve, @Size(max = 500) String note, boolean acknowledgeWarnings) {}

    public record SwapView(UUID id, UUID scheduleId, UUID branchId, Instant startsAt, Instant endsAt, UUID requesterEmployeeId,
                           String requesterName, UUID colleagueEmployeeId, String colleagueName, UUID colleagueScheduleId,
                           Instant colleagueStartsAt, Instant colleagueEndsAt, String reason, SwapStatus status, String decisionNote,
                           Instant decidedAt, Instant createdAt) {}
}
