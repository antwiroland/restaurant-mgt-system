package com.restaurantmanager.core.hr.attendance;

import com.restaurantmanager.core.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Same package as the services so package-private rules can be tested directly. */
class AttendanceUnitTest {
    private static final AttendanceCalculator.Policy POLICY =
            new AttendanceCalculator.Policy(5, 5, 30, "ALLOW", true, false, true, true, 120, "TEST");
    private static final Instant NINE = Instant.parse("2026-11-16T09:00:00Z");

    private static Instant at(int hour, int minute) {
        return NINE.plus(Duration.ofHours(hour - 9)).plus(Duration.ofMinutes(minute));
    }

    private static final AttendanceCalculator.Scheduled SHIFT = new AttendanceCalculator.Scheduled(at(9, 0), at(17, 0), 30);

    @Test
    void onTimeShift_paidTimeExcludesUnpaidBreaks_noLateEarlyOrOvertime() {
        var m = AttendanceCalculator.compute(at(9, 3), at(17, 0), 30, SHIFT, POLICY, at(18, 0));
        assertThat(m.elapsedMinutes()).isEqualTo(477);
        assertThat(m.paidMinutes()).isEqualTo(447);
        assertThat(m.lateMinutes()).isZero(); // within the 5 minute grace
        assertThat(m.earlyLeaveMinutes()).isZero();
        assertThat(m.overtimeMinutes()).isZero();
        assertThat(m.missedClockOut()).isFalse();
    }

    @Test
    void lateArrival_countsFromShiftStartOnceGraceIsExceeded() {
        assertThat(AttendanceCalculator.compute(at(9, 5), at(17, 0), 0, SHIFT, POLICY, at(18, 0)).lateMinutes()).isZero();
        assertThat(AttendanceCalculator.compute(at(9, 6), at(17, 0), 0, SHIFT, POLICY, at(18, 0)).lateMinutes()).isEqualTo(6);
    }

    @Test
    void earlyDeparture_andOvertime() {
        assertThat(AttendanceCalculator.compute(at(9, 0), at(16, 30), 30, SHIFT, POLICY, at(18, 0)).earlyLeaveMinutes()).isEqualTo(30);
        assertThat(AttendanceCalculator.compute(at(9, 0), at(16, 56), 30, SHIFT, POLICY, at(18, 0)).earlyLeaveMinutes()).isZero();
        // scheduled paid 450 min; worked 9:00-18:15 with 30 unpaid = 525 -> 75 overtime
        assertThat(AttendanceCalculator.compute(at(9, 0), at(18, 15), 30, SHIFT, POLICY, at(19, 0)).overtimeMinutes()).isEqualTo(75);
    }

    @Test
    void openEntries_missedClockOutAfterThePolicyWindow() {
        assertThat(AttendanceCalculator.compute(at(9, 0), null, 0, SHIFT, POLICY, at(18, 59)).missedClockOut()).isFalse();
        assertThat(AttendanceCalculator.compute(at(9, 0), null, 0, SHIFT, POLICY, at(19, 1)).missedClockOut()).isTrue();
        // unscheduled: flagged after 16 hours
        assertThat(AttendanceCalculator.compute(at(9, 0), null, 0, null, POLICY, at(9, 0).plus(Duration.ofHours(17))).missedClockOut()).isTrue();
        // open entries report elapsed time so far and no overtime yet
        var open = AttendanceCalculator.compute(at(9, 0), null, 0, SHIFT, POLICY, at(20, 0));
        assertThat(open.elapsedMinutes()).isEqualTo(660);
        assertThat(open.overtimeMinutes()).isZero();
    }

    @Test
    void breakPayFollowsPolicy() {
        assertThat(POLICY.breakPaid(BreakType.MEAL)).isFalse();
        assertThat(POLICY.breakPaid(BreakType.REST)).isTrue();
        assertThat(POLICY.breakPaid(BreakType.OTHER)).isFalse();
    }

    @Test
    void shiftWindow_allowsEarlyClockInWithinTheConfiguredMinutes() {
        assertThat(AttendanceCalculator.withinShiftWindow(at(8, 30), at(9, 0), at(17, 0), POLICY)).isTrue();
        assertThat(AttendanceCalculator.withinShiftWindow(at(8, 29), at(9, 0), at(17, 0), POLICY)).isFalse();
        assertThat(AttendanceCalculator.withinShiftWindow(at(16, 59), at(9, 0), at(17, 0), POLICY)).isTrue();
        assertThat(AttendanceCalculator.withinShiftWindow(at(17, 0), at(9, 0), at(17, 0), POLICY)).isFalse();
    }

    @Test
    void pinRules_rejectWeakOrMalformedPins() {
        for (String bad : new String[]{"123", "1234567", "12a4", "0000", "1111", "1234", "4321", "987654", null}) {
            assertThatThrownBy(() -> ClockPinService.validate(bad)).as(String.valueOf(bad)).isInstanceOf(ApiException.class);
        }
        for (String good : new String[]{"2580", "1357", "904512", "1123"}) {
            assertThatCode(() -> ClockPinService.validate(good)).as(good).doesNotThrowAnyException();
        }
    }

    @Test
    void eventTime_acceptsQueuedDeviceTimeWithinLimits() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        assertThat(AttendanceService.eventTime(null, now)).isEqualTo(now);
        assertThat(AttendanceService.eventTime(now.minus(Duration.ofMinutes(40)), now)).isEqualTo(now.minus(Duration.ofMinutes(40)));
        assertThat(AttendanceService.eventTime(now.plusSeconds(30), now)).isEqualTo(now); // small skew is clamped
        assertThatThrownBy(() -> AttendanceService.eventTime(now.plus(Duration.ofMinutes(5)), now)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> AttendanceService.eventTime(now.minus(Duration.ofHours(13)), now)).isInstanceOf(ApiException.class);
    }
}
