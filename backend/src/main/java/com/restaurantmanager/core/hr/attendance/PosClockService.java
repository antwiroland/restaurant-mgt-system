package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.branch.BranchAssignmentRepository;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmploymentStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import com.restaurantmanager.core.workforce.TimeEntryRepository;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Staff clock on a POS terminal. The terminal is whichever staff account is signed in to the POS; its branch is the
 * terminal's branch. Employees identify themselves by picking their name and entering their own clock PIN. Clocking in
 * grants nothing on the POS: the signed-in account, its role and order attribution are untouched.
 */
@Service
public class PosClockService {
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAssignmentRepository assignmentRepository;
    private final TimeEntryRepository entryRepository;
    private final AttendanceBreakRepository breakRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final ClockPinService pinService;
    private final ClockGateway gateway;
    private final AttendanceService attendanceService;

    public PosClockService(UserRepository userRepository, EmployeeRepository employeeRepository, BranchAssignmentRepository assignmentRepository,
                           TimeEntryRepository entryRepository, AttendanceBreakRepository breakRepository,
                           WorkScheduleRepository scheduleRepository, ClockPinService pinService, ClockGateway gateway,
                           AttendanceService attendanceService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.entryRepository = entryRepository;
        this.breakRepository = breakRepository;
        this.scheduleRepository = scheduleRepository;
        this.pinService = pinService;
        this.gateway = gateway;
        this.attendanceService = attendanceService;
    }

    /** People who can clock at this terminal today: active and assigned to the branch, plus anyone still clocked in here. */
    @Transactional(readOnly = true)
    public List<RosterEntry> roster(UserPrincipal principal, UUID requestedBranch) {
        UUID branchId = terminalBranch(principal, requestedBranch);
        LocalDate today = LocalDate.now(attendanceService.zone());
        Instant now = Instant.now();
        Set<UUID> ids = new HashSet<>(assignmentRepository.findEmployeeIdsAtBranch(branchId, today, today));
        Map<UUID, TimeEntryEntity> open = entryRepository.findAtBranches(now.minus(Duration.ofDays(2)), now, List.of(branchId)).stream()
                .filter(e -> e.getClockOutAt() == null && e.getEmployeeId() != null)
                .collect(Collectors.toMap(TimeEntryEntity::getEmployeeId, Function.identity(), (a, b) -> a));
        ids.addAll(open.keySet());
        Instant dayStart = today.atStartOfDay(attendanceService.zone()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(attendanceService.zone()).toInstant();
        return employeeRepository.findByIdIn(ids).stream()
                .filter(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE || open.containsKey(e.getId()))
                .sorted(Comparator.comparing(EmployeeEntity::getFirstName, String.CASE_INSENSITIVE_ORDER).thenComparing(EmployeeEntity::fullName))
                .map(e -> {
                    TimeEntryEntity entry = open.get(e.getId());
                    String state = entry == null ? "OFF" : breakRepository.findFirstByTimeEntryIdAndEndedAtIsNull(entry.getId()).isPresent() ? "ON_BREAK" : "ON_SHIFT";
                    Instant nextShift = scheduleRepository.findPublishedForEmployee(dayStart, dayEnd, e.getId()).stream()
                            .filter(s -> branchId.equals(s.getBranchId()) && s.getEndsAt().isAfter(now))
                            .map(WorkScheduleEntity::getStartsAt).min(Comparator.naturalOrder()).orElse(null);
                    return new RosterEntry(e.getId(), displayName(e), e.getPosition() == null ? null : e.getPosition().getTitle(),
                            e.getClockPinHash() != null, state, nextShift);
                })
                .toList();
    }

    public AttendanceService.ClockResult clock(UserPrincipal principal, PosClockRequest request) {
        UUID branchId = terminalBranch(principal, request.branchId());
        ClockPinService.requireOk(pinService.verify(request.employeeId(), request.pin()));
        return gateway.clock(new AttendanceService.ClockCommand(request.employeeId(), request.action(), request.breakType(), branchId,
                AttendanceSource.POS, blankToNull(request.terminalId()), request.idempotencyKey(), request.occurredAt(), principal.userId()));
    }

    /** After a PIN check: the employee's state and upcoming shifts (their own information only). */
    @Transactional(readOnly = true)
    public PosSummary summary(UserPrincipal principal, PosIdentifyRequest request) {
        terminalBranch(principal, request.branchId());
        ClockPinService.requireOk(pinService.verify(request.employeeId(), request.pin()));
        EmployeeEntity employee = employeeRepository.findById(request.employeeId()).orElseThrow(() -> new ApiException(404, "Employee not found"));
        Instant now = Instant.now();
        AttendanceService.EntryView current = entryRepository.findFirstByEmployeeIdAndClockOutAtIsNull(employee.getId())
                .map(e -> attendanceService.views(List.of(e), now).get(0)).orElse(null);
        List<ShiftSummary> shifts = scheduleRepository.findPublishedForEmployee(now.minus(Duration.ofHours(12)), now.plus(Duration.ofDays(7)), employee.getId())
                .stream().filter(s -> s.getEndsAt().isAfter(now))
                .map(s -> new ShiftSummary(s.getId(), s.getBranchId(), s.getStartsAt(), s.getEndsAt(), s.getBreakMinutes())).toList();
        return new PosSummary(employee.getId(), displayName(employee), current, shifts);
    }

    /** POS operators only; the terminal's branch is the operator's own (admins without a branch must name one). */
    private UUID terminalBranch(UserPrincipal principal, UUID requested) {
        UserEntity operator = userRepository.findById(principal.userId()).orElseThrow(() -> new ApiException(401, "Unauthorized"));
        if (operator.getRole() != Role.ADMIN && operator.getRole() != Role.MANAGER && operator.getRole() != Role.CASHIER) {
            throw new ApiException(403, "Staff clock is available on POS terminals only");
        }
        UUID own = operator.getBranch() == null ? null : operator.getBranch().getId();
        if (own != null) {
            if (requested != null && !requested.equals(own)) {
                throw new ApiException(403, "This terminal belongs to another branch");
            }
            return own;
        }
        if (operator.getRole() == Role.ADMIN && requested != null) {
            return requested;
        }
        throw new ApiException(400, "This POS account has no branch; assign one to use the staff clock");
    }

    static String displayName(EmployeeEntity e) {
        String last = e.getLastName();
        return last == null || last.isBlank() ? e.getFirstName() : e.getFirstName() + " " + last.charAt(0) + ".";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RosterEntry(UUID employeeId, String displayName, String positionTitle, boolean hasPin, String state, Instant nextShiftAt) {}

    public record PosClockRequest(@NotNull UUID employeeId, @NotBlank @Size(max = 6) String pin, @NotNull ClockAction action,
                                  BreakType breakType, @NotBlank @Size(max = 100) String idempotencyKey, Instant occurredAt,
                                  @Size(max = 80) String terminalId, UUID branchId) {}

    public record PosIdentifyRequest(@NotNull UUID employeeId, @NotBlank @Size(max = 6) String pin, UUID branchId) {}

    public record ShiftSummary(UUID id, UUID branchId, Instant startsAt, Instant endsAt, int breakMinutes) {}

    public record PosSummary(UUID employeeId, String displayName, AttendanceService.EntryView current, List<ShiftSummary> upcomingShifts) {}
}
