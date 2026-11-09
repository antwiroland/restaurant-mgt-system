package com.restaurantmanager.core.hr.schedule;

import java.util.List;

/**
 * Raised when a shift cannot be saved as requested: blocking problems, or warnings the scheduler has not acknowledged.
 * Rendered by {@link HrScheduleExceptionHandler} with the individual issues so the client can show them.
 */
public class ScheduleIssuesException extends RuntimeException {
    private final List<ScheduleRules.Issue> issues;

    public ScheduleIssuesException(String message, List<ScheduleRules.Issue> issues) {
        super(message);
        this.issues = List.copyOf(issues);
    }

    public List<ScheduleRules.Issue> getIssues() {
        return issues;
    }

    public boolean blocking() {
        return issues.stream().anyMatch(i -> i.severity() == ScheduleRules.Severity.BLOCKING);
    }
}
