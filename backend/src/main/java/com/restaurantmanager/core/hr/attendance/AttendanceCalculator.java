package com.restaurantmanager.core.hr.attendance;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure attendance arithmetic, kept free of persistence for unit testing. All results are whole minutes.
 * Overtime here is provisional (worked beyond the scheduled paid time); approval rules arrive with timesheets.
 */
public final class AttendanceCalculator {
    private AttendanceCalculator() {
    }

    public record Policy(int lateGraceMinutes, int earlyLeaveGraceMinutes, int earlyClockInMinutes, String unscheduledClockIn,
                         boolean requireCorrectionApproval, boolean mealBreakPaid, boolean restBreakPaid, boolean allowSelfClock,
                         int missedClockOutMinutes, String source) {
        public boolean breakPaid(BreakType type) {
            return switch (type) {
                case MEAL -> mealBreakPaid;
                case REST -> restBreakPaid;
                case OTHER -> false;
            };
        }
    }

    /** Scheduled shift the entry belongs to (null when unscheduled). */
    public record Scheduled(Instant start, Instant end, int breakMinutes) {
        long paidMinutes() {
            return Math.max(0, Duration.between(start, end).toMinutes() - breakMinutes);
        }
    }

    public record Metrics(long elapsedMinutes, long unpaidBreakMinutes, long paidMinutes, long lateMinutes, long earlyLeaveMinutes,
                          long overtimeMinutes, boolean missedClockOut) {}

    /** Unscheduled entries count as missing a clock-out this long after clock-in. */
    static final Duration UNSCHEDULED_MISSED_AFTER = Duration.ofHours(16);

    public static Metrics compute(Instant clockIn, Instant clockOut, long unpaidBreakMinutes, Scheduled scheduled, Policy policy, Instant now) {
        Instant end = clockOut != null ? clockOut : now;
        long elapsed = Math.max(0, Duration.between(clockIn, end).toMinutes());
        long unpaid = Math.min(Math.max(0, unpaidBreakMinutes), elapsed);
        long paid = elapsed - unpaid;
        long late = 0;
        long early = 0;
        long overtime = 0;
        boolean missed;
        if (scheduled != null) {
            if (clockIn.isAfter(scheduled.start().plus(Duration.ofMinutes(policy.lateGraceMinutes())))) {
                late = Duration.between(scheduled.start(), clockIn).toMinutes();
            }
            if (clockOut != null && clockOut.isBefore(scheduled.end().minus(Duration.ofMinutes(policy.earlyLeaveGraceMinutes())))) {
                early = Duration.between(clockOut, scheduled.end()).toMinutes();
            }
            if (clockOut != null) {
                overtime = Math.max(0, paid - scheduled.paidMinutes());
            }
            missed = clockOut == null && now.isAfter(scheduled.end().plus(Duration.ofMinutes(policy.missedClockOutMinutes())));
        } else {
            missed = clockOut == null && now.isAfter(clockIn.plus(UNSCHEDULED_MISSED_AFTER));
        }
        return new Metrics(elapsed, unpaid, paid, late, early, overtime, missed);
    }

    /** Whether a clock-in at {@code at} belongs to a shift: from the early window before the start until the shift ends. */
    public static boolean withinShiftWindow(Instant at, Instant shiftStart, Instant shiftEnd, Policy policy) {
        return !at.isBefore(shiftStart.minus(Duration.ofMinutes(policy.earlyClockInMinutes()))) && at.isBefore(shiftEnd);
    }
}
