package com.restaurantmanager.core.hr.employee;

public enum EmploymentStatus {
    ACTIVE,
    ON_LEAVE,
    SUSPENDED,
    RESIGNED,
    TERMINATED,
    RETIRED;

    /** Statuses that end employment; they require {@code hr.employee.archive} and an exit date. */
    public boolean isExit() {
        return this == RESIGNED || this == TERMINATED || this == RETIRED;
    }
}
