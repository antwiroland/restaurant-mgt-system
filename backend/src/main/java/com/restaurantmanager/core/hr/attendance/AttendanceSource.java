package com.restaurantmanager.core.hr.attendance;

/** Where an attendance record came from. Stored as text on time entries, breaks and events. */
public enum AttendanceSource {
    POS,
    SELF_SERVICE,
    MANAGER,
    DEVICE,
    CORRECTION
}
