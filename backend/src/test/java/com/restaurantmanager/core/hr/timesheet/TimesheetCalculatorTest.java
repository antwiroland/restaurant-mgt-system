package com.restaurantmanager.core.hr.timesheet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TimesheetCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final BigDecimal X15 = new BigDecimal("1.50");

    private static TimesheetCalculator.Rule rule(Integer daily, Integer weekly) {
        return new TimesheetCalculator.Rule(daily, weekly, X15, X15, EnumSet.of(DayOfWeek.SUNDAY), X15, new BigDecimal("2.00"),
                LocalTime.of(22, 0), LocalTime.of(6, 0), new BigDecimal("1.25"), true, "TEST");
    }

    private static TimesheetCalculator.Piece piece(LocalDate day, int startHour, int endHour, int unpaidBreak) {
        Instant start = day.atTime(startHour, 0).atZone(ZONE).toInstant();
        Instant end = (endHour <= startHour ? day.plusDays(1) : day).atTime(endHour % 24, 0).atZone(ZONE).toInstant();
        int paid = (int) java.time.Duration.between(start, end).toMinutes() - unpaidBreak;
        return new TimesheetCalculator.Piece(day, start, end, paid);
    }

    @Test
    void dailyOvertime_isPaidTimeBeyondTheThresholdEachDay() {
        var result = TimesheetCalculator.compute(List.of(piece(MONDAY, 8, 19, 60), piece(MONDAY.plusDays(1), 9, 17, 30)),
                d -> rule(480, null), Set.of(), ZONE);
        assertThat(result.paidMinutes()).isEqualTo(600 + 450);
        assertThat(result.dailyOvertimeMinutes()).isEqualTo(120);
        assertThat(result.weeklyOvertimeMinutes()).isZero();
        assertThat(result.regularMinutes()).isEqualTo(930);
        assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    void weeklyOvertime_doesNotDoubleCountDailyOvertime() {
        List<TimesheetCalculator.Piece> week = new ArrayList<>();
        for (int d = 0; d < 6; d++) {
            week.add(piece(MONDAY.plusDays(d), 8, 18, 0)); // 6 x 600 min
        }
        var result = TimesheetCalculator.compute(week, d -> rule(540, 2400), Set.of(), ZONE);
        // daily: 6 x 60 = 360; remaining 6 x 540 = 3240, weekly threshold 2400 -> 840
        assertThat(result.dailyOvertimeMinutes()).isEqualTo(360);
        assertThat(result.weeklyOvertimeMinutes()).isEqualTo(840);
        assertThat(result.overtimeMinutes()).isEqualTo(1200);
        assertThat(result.regularMinutes()).isEqualTo(2400);
    }

    @Test
    void weeklyThreshold_appliesPerCalendarWeek() {
        List<TimesheetCalculator.Piece> twoWeeks = new ArrayList<>();
        for (int d = 0; d < 14; d++) {
            twoWeeks.add(piece(MONDAY.plusDays(d), 9, 15, 0)); // 360 per day, 2520 per week
        }
        var result = TimesheetCalculator.compute(twoWeeks, d -> rule(null, 2400), Set.of(), ZONE);
        assertThat(result.weeklyOvertimeMinutes()).isEqualTo(240); // 120 in each week
    }

    @Test
    void holidayWeekendAndNight_areReportedAsPremiumCategories() {
        LocalDate sunday = MONDAY.plusDays(6);
        var result = TimesheetCalculator.compute(List.of(
                        piece(MONDAY, 9, 17, 0),          // holiday
                        piece(sunday, 12, 18, 0),          // weekend
                        piece(MONDAY.plusDays(2), 20, 2, 0)), // 4h inside 22:00-06:00
                d -> rule(null, null), Set.of(MONDAY), ZONE);
        assertThat(result.holidayMinutes()).isEqualTo(480);
        assertThat(result.weekendMinutes()).isEqualTo(360);
        assertThat(result.nightMinutes()).isEqualTo(240);
        assertThat(result.overtimeMinutes()).isZero();
    }

    @Test
    void ruleChangesMidPeriod_areAppliedPerDay() {
        LocalDate change = MONDAY.plusDays(2);
        var result = TimesheetCalculator.compute(List.of(piece(MONDAY, 8, 18, 0), piece(change, 8, 18, 0)),
                d -> d.isBefore(change) ? rule(480, null) : rule(540, null), Set.of(), ZONE);
        assertThat(result.dailyOvertimeMinutes()).isEqualTo(120 + 60);
    }

    @Test
    void noThresholds_andNoApprovalRequired_meansNoOvertime() {
        var noApproval = new TimesheetCalculator.Rule(null, null, X15, X15, Set.of(), null, null, null, null, null, false, "TEST");
        var result = TimesheetCalculator.compute(List.of(piece(MONDAY, 6, 22, 0)), d -> noApproval, Set.of(), ZONE);
        assertThat(result.overtimeMinutes()).isZero();
        assertThat(result.requiresApproval()).isFalse();
        assertThat(result.nightMinutes()).isZero();
    }
}
