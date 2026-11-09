package com.restaurantmanager.core.hr.branch;

import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Keeps the assignment history consistent with the employee's current primary branch. Callers have already checked
 * permissions and branch scope; this class only enforces the date rules.
 */
@Component
public class BranchAssignmentRecorder {
    private final BranchAssignmentRepository repository;

    public BranchAssignmentRecorder(BranchAssignmentRepository repository) {
        this.repository = repository;
    }

    /** Opens the first primary assignment for a new (or re-hired) employee. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void openPrimary(UUID employeeId, UUID branchId, UUID positionId, LocalDate startDate, String reason, UUID actorId) {
        if (branchId == null) {
            return;
        }
        repository.findFirstByEmployeeIdAndAssignmentTypeAndEndDateIsNull(employeeId, AssignmentType.PRIMARY)
                .ifPresent(open -> {
                    throw new IllegalStateException("Employee already has an open primary assignment");
                });
        // a secondary assignment at the new home branch is superseded by the primary one
        repository.findOverlapping(employeeId, branchId, startDate, null).stream()
                .filter(a -> a.getAssignmentType() != AssignmentType.PRIMARY)
                .forEach(a -> close(a, startDate.minusDays(1).isBefore(a.getStartDate()) ? a.getStartDate() : startDate.minusDays(1),
                        "Became the primary branch", actorId));
        repository.flush();
        BranchAssignmentEntity primary = new BranchAssignmentEntity();
        primary.setEmployeeId(employeeId);
        primary.setBranchId(branchId);
        primary.setAssignmentType(AssignmentType.PRIMARY);
        primary.setPositionId(positionId);
        primary.setStartDate(startDate);
        primary.setReason(reason);
        primary.setCreatedBy(actorId);
        repository.save(primary);
    }

    /**
     * Moves the primary branch: the open primary assignment ends the day before {@code effectiveDate} and a new one starts
     * on it. Returns the previous branch id (may be null for employees that had none).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID switchPrimary(UUID employeeId, UUID toBranchId, UUID positionId, LocalDate effectiveDate, String reason, UUID actorId) {
        BranchAssignmentEntity current = repository.findFirstByEmployeeIdAndAssignmentTypeAndEndDateIsNull(employeeId, AssignmentType.PRIMARY)
                .orElse(null);
        UUID previous = current == null ? null : current.getBranchId();
        if (current != null) {
            if (current.getBranchId().equals(toBranchId)) {
                throw new ApiException(400, "The employee is already based at this branch");
            }
            if (effectiveDate.equals(current.getStartDate()) && toBranchId != null) {
                // same-day correction (e.g. wrong branch entered at hire): fix the open row instead of a one-day stint
                repository.findOverlapping(employeeId, toBranchId, effectiveDate, null).stream()
                        .filter(a -> a.getAssignmentType() != AssignmentType.PRIMARY)
                        .forEach(a -> close(a, a.getStartDate().isBefore(effectiveDate) ? effectiveDate.minusDays(1) : a.getStartDate(),
                                "Became the primary branch", actorId));
                repository.flush();
                current.setBranchId(toBranchId);
                current.setPositionId(positionId);
                current.setReason(reason);
                return previous;
            }
            if (!effectiveDate.isAfter(current.getStartDate())) {
                throw new ApiException(400, "The transfer date must be after the current assignment started (" + current.getStartDate() + ")");
            }
            close(current, effectiveDate.minusDays(1), reason == null ? "Transferred" : reason, actorId);
            repository.flush();
        }
        if (toBranchId != null) {
            openPrimary(employeeId, toBranchId, positionId, effectiveDate, reason, actorId);
        }
        return previous;
    }

    /** Ends every open assignment when employment ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeAll(UUID employeeId, LocalDate lastDay, String reason, UUID actorId) {
        repository.findByEmployeeIdOrderByStartDateDescCreatedAtDesc(employeeId).stream()
                .filter(a -> a.getEndDate() == null || a.getEndDate().isAfter(lastDay))
                .forEach(a -> close(a, lastDay.isBefore(a.getStartDate()) ? a.getStartDate() : lastDay, reason, actorId));
    }

    void close(BranchAssignmentEntity assignment, LocalDate endDate, String reason, UUID actorId) {
        assignment.setEndDate(endDate);
        assignment.setEndReason(reason);
        assignment.setEndedBy(actorId);
    }
}
