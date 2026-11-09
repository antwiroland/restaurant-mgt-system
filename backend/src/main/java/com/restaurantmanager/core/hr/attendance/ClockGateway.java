package com.restaurantmanager.core.hr.attendance;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Entry point for clock actions. Two concurrent submissions of the same action (e.g. a double tap or a retry racing the
 * original) can both pass the duplicate check; the database's unique constraints stop the second one, and here it is
 * answered with the first one's stored outcome instead of an error.
 */
@Component
public class ClockGateway {
    private final AttendanceService attendanceService;

    public ClockGateway(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public AttendanceService.ClockResult clock(AttendanceService.ClockCommand command) {
        try {
            return attendanceService.clock(command);
        } catch (DataIntegrityViolationException race) {
            return attendanceService.replayKey(command);
        }
    }
}
