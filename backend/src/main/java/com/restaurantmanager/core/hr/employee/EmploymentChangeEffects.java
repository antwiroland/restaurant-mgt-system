package com.restaurantmanager.core.hr.employee;

import com.restaurantmanager.core.auth.RefreshTokenRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.contract.ContractEntity;
import com.restaurantmanager.core.hr.contract.ContractRepository;
import com.restaurantmanager.core.hr.contract.ContractStatus;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.leave.LeaveService;
import com.restaurantmanager.core.hr.schedule.ScheduleNotifier;
import com.restaurantmanager.core.security.DisabledAccounts;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What else changes when an employee's status changes, so a person who leaves or is suspended cannot keep working
 * on the system and their commitments do not linger:
 * <ul>
 *   <li>Suspension or exit: their login is switched off (unless asked to keep it) and their sessions ended. It is
 *       switched back on if they return to active work, but only when HR was the one who switched it off.</li>
 *   <li>Exit: shifts after their last day become open shifts (the branch's managers are told), their active contract
 *       ends on their last day, and pending leave, plus approved leave after their last day, is cancelled.</li>
 * </ul>
 */
@Component
public class EmploymentChangeEffects {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ContractRepository contractRepository;
    private final LeaveService leaveService;
    private final WorkScheduleRepository scheduleRepository;
    private final ScheduleNotifier scheduleNotifier;
    private final ZoneId zone;
    private final DisabledAccounts disabledAccounts;

    public EmploymentChangeEffects(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                   ContractRepository contractRepository, @Lazy LeaveService leaveService,
                                   WorkScheduleRepository scheduleRepository, ScheduleNotifier scheduleNotifier,
                                   DisabledAccounts disabledAccounts,
                                   @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.disabledAccounts = disabledAccounts;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.contractRepository = contractRepository;
        this.leaveService = leaveService;
        this.scheduleRepository = scheduleRepository;
        this.scheduleNotifier = scheduleNotifier;
        this.zone = ZoneId.of(timeZone);
    }

    /** What happened, for the audit trail and to tell the person who made the change. */
    public record Effects(boolean loginDisabled, boolean loginRestored, int shiftsReleased, int contractsEnded, int leaveCancelled) {
        public static final Effects NONE = new Effects(false, false, 0, 0, 0);

        public boolean any() {
            return loginDisabled || loginRestored || shiftsReleased > 0 || contractsEnded > 0 || leaveCancelled > 0;
        }
    }

    public Effects apply(HrAccess access, EmployeeEntity employee, EmploymentStatus from, EmploymentStatus to, LocalDate exitDate,
                         boolean keepLoginActive, String reason) {
        boolean loginDisabled = false;
        boolean loginRestored = false;
        boolean blocked = to.isExit() || to == EmploymentStatus.SUSPENDED;
        UserEntity user = employee.getUserId() == null ? null : userRepository.findById(employee.getUserId()).orElse(null);

        if (blocked && user != null && user.isActive() && !keepLoginActive) {
            user.setActive(false);
            userRepository.save(user);
            refreshTokenRepository.revokeAllForUser(user.getId());
            disabledAccounts.disabled(user.getId());
            employee.setLoginDisabledByHr(true);
            loginDisabled = true;
        } else if (!blocked && employee.isLoginDisabledByHr()) {
            if (user != null && !user.isActive()) {
                user.setActive(true);
                userRepository.save(user);
                disabledAccounts.enabled(user.getId());
                loginRestored = true;
            }
            employee.setLoginDisabledByHr(false);
        }

        if (!to.isExit() || from.isExit()) {
            return new Effects(loginDisabled, loginRestored, 0, 0, 0);
        }
        String why = "Employment ended" + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
        return new Effects(loginDisabled, loginRestored, releaseShifts(employee, exitDate), endContracts(employee, exitDate, why),
                leaveService.cancelForExit(access, employee, exitDate, why));
    }

    /** Shifts after the last working day (and not yet started) lose their employee and become open shifts. */
    private int releaseShifts(EmployeeEntity employee, LocalDate exitDate) {
        Instant afterLastDay = exitDate.plusDays(1).atStartOfDay(zone).toInstant();
        Instant from = afterLastDay.isAfter(Instant.now()) ? afterLastDay : Instant.now();
        List<WorkScheduleEntity> shifts = scheduleRepository.findActiveForEmployee(from, from.plusSeconds(400L * 24 * 3600), employee.getId())
                .stream().filter(s -> !s.getStartsAt().isBefore(from)).toList();
        Set<UUID> branches = new HashSet<>();
        for (WorkScheduleEntity shift : shifts) {
            shift.setEmployeeId(null);
            shift.setUserId(null);
            String note = "Was " + employee.fullName() + " (left)";
            shift.setNotes(shift.getNotes() == null ? note : shift.getNotes() + " · " + note);
            branches.add(shift.getBranchId());
        }
        scheduleRepository.saveAll(shifts);
        if (!shifts.isEmpty()) {
            List<UUID> managers = userRepository.findByRoleAndActiveTrueOrderByNameAsc(Role.MANAGER).stream()
                    .filter(u -> u.getBranch() != null && branches.contains(u.getBranch().getId()))
                    .map(UserEntity::getId).toList();
            scheduleNotifier.shiftsReleased(managers, employee.fullName(), shifts.size(), "has left");
        }
        return shifts.size();
    }

    /** The active contract ends on the last working day; an unsigned draft is closed. */
    private int endContracts(EmployeeEntity employee, LocalDate exitDate, String reason) {
        int ended = 0;
        for (ContractEntity contract : contractRepository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employee.getId())) {
            if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.DRAFT) continue;
            if (contract.getStatus() == ContractStatus.ACTIVE
                    && (contract.getEndDate() == null || contract.getEndDate().isAfter(exitDate))) {
                contract.setEndDate(exitDate.isBefore(contract.getStartDate()) ? contract.getStartDate() : exitDate);
            }
            contract.setStatus(ContractStatus.ENDED);
            contract.setEndReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            contractRepository.save(contract);
            ended++;
        }
        return ended;
    }

}
