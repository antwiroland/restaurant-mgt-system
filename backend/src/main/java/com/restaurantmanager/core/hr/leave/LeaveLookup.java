package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.hr.schedule.ScheduleRules;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only view of pending and approved leave for scheduling and timesheets. */
@Component
public class LeaveLookup {
    private final LeaveRequestRepository requestRepository;
    private final LeaveTypeRepository typeRepository;

    public LeaveLookup(LeaveRequestRepository requestRepository, LeaveTypeRepository typeRepository) {
        this.requestRepository = requestRepository;
        this.typeRepository = typeRepository;
    }

    /** Pending and approved leave of an employee overlapping [from, to], as scheduling rule input. */
    @Transactional(readOnly = true)
    public List<ScheduleRules.LeavePeriod> periods(UUID employeeId, LocalDate from, LocalDate to) {
        List<LeaveRequestEntity> rows = requestRepository.findOverlapping(employeeId, LeaveService.ACTIVE, from, to);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new HashMap<>();
        typeRepository.findAllById(rows.stream().map(LeaveRequestEntity::getLeaveTypeId).distinct().toList())
                .forEach(t -> names.put(t.getId(), t.getName()));
        return rows.stream().map(r -> new ScheduleRules.LeavePeriod(r.getStartDate(), r.getEndDate(), r.isStartHalf(), r.isEndHalf(),
                r.getStatus() == LeaveStatus.APPROVED, names.getOrDefault(r.getLeaveTypeId(), "Leave"))).toList();
    }

    /** Leave type code per day of approved leave of an employee within [from, to]. */
    @Transactional(readOnly = true)
    public Map<LocalDate, String> approvedDays(UUID employeeId, LocalDate from, LocalDate to) {
        List<LeaveRequestEntity> rows = requestRepository.findOverlapping(employeeId, EnumSet.of(LeaveStatus.APPROVED), from, to);
        Map<LocalDate, String> days = new HashMap<>();
        if (rows.isEmpty()) {
            return days;
        }
        Map<UUID, String> codes = new HashMap<>();
        typeRepository.findAllById(rows.stream().map(LeaveRequestEntity::getLeaveTypeId).distinct().toList())
                .forEach(t -> codes.put(t.getId(), t.getCode()));
        for (LeaveRequestEntity r : rows) {
            for (LocalDate d = r.getStartDate().isBefore(from) ? from : r.getStartDate(); !d.isAfter(r.getEndDate()) && !d.isAfter(to); d = d.plusDays(1)) {
                days.put(d, codes.getOrDefault(r.getLeaveTypeId(), "LEAVE"));
            }
        }
        return days;
    }

    /** Which of the employees are on approved leave on {@code day} (one query). */
    @Transactional(readOnly = true)
    public Set<UUID> onApprovedLeave(Collection<UUID> employeeIds, LocalDate day) {
        if (employeeIds.isEmpty()) {
            return Set.of();
        }
        return requestRepository.findOverlappingForEmployees(employeeIds, EnumSet.of(LeaveStatus.APPROVED), day, day).stream()
                .map(LeaveRequestEntity::getEmployeeId).collect(Collectors.toSet());
    }
}
