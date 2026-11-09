package com.restaurantmanager.core.hr.timesheet;

/** Draft -> Submitted -> Reviewed -> Approved -> Locked (locked = taken into payroll; never reopened). */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    REVIEWED,
    APPROVED,
    LOCKED
}
