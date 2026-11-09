package com.restaurantmanager.core.hr.timesheet;

import com.restaurantmanager.core.common.ApiException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Keeps attendance and timesheets consistent: once a timesheet has left DRAFT its days are frozen, so corrections in that
 * period are refused until the timesheet is reopened (locked timesheets cannot be reopened).
 */
@Component
public class TimesheetGuard {
    private final TimesheetRepository repository;

    public TimesheetGuard(TimesheetRepository repository) {
        this.repository = repository;
    }

    public void assertEditable(UUID employeeId, LocalDate... days) {
        if (employeeId == null) {
            return;
        }
        for (LocalDate day : days) {
            if (day == null) {
                continue;
            }
            repository.findOverlapping(employeeId, day, day).stream()
                    .filter(t -> t.getStatus() != TimesheetStatus.DRAFT)
                    .findFirst()
                    .ifPresent(t -> {
                        throw new ApiException(409, t.getStatus() == TimesheetStatus.LOCKED
                                ? "This day is on a locked timesheet (" + t.getPeriodStart() + " – " + t.getPeriodEnd() + ") and can no longer change"
                                : "This day is on a " + t.getStatus().name().toLowerCase() + " timesheet (" + t.getPeriodStart() + " – "
                                + t.getPeriodEnd() + "). Reopen the timesheet first.");
                    });
        }
    }
}
