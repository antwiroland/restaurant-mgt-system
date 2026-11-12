package com.restaurantmanager.core.hr;

import com.restaurantmanager.core.hr.employee.EmploymentStatus;
import com.restaurantmanager.core.hr.schedule.AvailabilityEntity;
import com.restaurantmanager.core.hr.schedule.AvailabilityKind;
import com.restaurantmanager.core.hr.schedule.ScheduleRules;
import com.restaurantmanager.core.hr.schedule.ScheduleRules.Interval;
import com.restaurantmanager.core.hr.schedule.ScheduleRules.Issue;
import com.restaurantmanager.core.hr.schedule.ScheduleRules.Policy;
import com.restaurantmanager.core.hr.schedule.ShiftTemplateEntity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRulesTest {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    private static final Policy POLICY = new Policy(8 * 60, 12 * 60, 48 * 60, "TEST");
    // Monday 2026-11-16
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static Interval shift(LocalDate day, int startHour, int endHour, int breakMinutes) {
        Instant start = day.atTime(startHour, 0).atZone(ZONE).toInstant();
        Instant end = (endHour <= startHour ? day.plusDays(1) : day).atTime(endHour % 24, 0).atZone(ZONE).toInstant();
        return new Interval(start, end, breakMinutes);
    }

    private static List<String> codes(List<Issue> issues) {
        return issues.stream().map(Issue::code).toList();
    }

    private static List<Issue> evaluate(Interval candidate, List<Interval> others) {
        return ScheduleRules.evaluate(candidate, others, POLICY, EmploymentStatus.ACTIVE, true, List.of(), ZONE);
    }

    @Test
    void cleanShift_hasNoIssues() {
        assertThat(evaluate(shift(MONDAY, 9, 17, 30), List.of())).isEmpty();
    }

    @Test
    void overlap_isBlocking_butBackToBackIsAllowed() {
        List<Issue> overlap = evaluate(shift(MONDAY, 9, 17, 0), List.of(shift(MONDAY, 16, 22, 0)));
        assertThat(codes(overlap)).contains("OVERLAP");
        assertThat(overlap).filteredOn(i -> i.code().equals("OVERLAP")).allMatch(i -> i.severity() == ScheduleRules.Severity.BLOCKING);
        assertThat(codes(evaluate(shift(MONDAY, 9, 17, 0), List.of(shift(MONDAY, 17, 22, 0))))).doesNotContain("OVERLAP");
    }

    @Test
    void shortRest_isAWarningInBothDirections() {
        // closing 22:00 then opening 06:00 = 8h (allowed); opening 05:00 = 7h (warning)
        assertThat(codes(evaluate(shift(MONDAY.plusDays(1), 6, 14, 0), List.of(shift(MONDAY, 14, 22, 0))))).doesNotContain("SHORT_REST");
        List<Issue> issues = evaluate(shift(MONDAY.plusDays(1), 5, 13, 0), List.of(shift(MONDAY, 14, 22, 0)));
        assertThat(codes(issues)).containsExactly("SHORT_REST");
        assertThat(issues.get(0).severity()).isEqualTo(ScheduleRules.Severity.WARNING);
        assertThat(issues.get(0).message()).contains("7h");
        // the later shift already exists, the earlier one is being added
        assertThat(codes(evaluate(shift(MONDAY, 14, 22, 0), List.of(shift(MONDAY.plusDays(1), 5, 13, 0))))).containsExactly("SHORT_REST");
    }

    @Test
    void longShift_andWeeklyHours_areWarnings() {
        assertThat(codes(evaluate(shift(MONDAY, 8, 21, 0), List.of()))).containsExactly("LONG_SHIFT");
        List<Interval> week = new ArrayList<>();
        for (int d = 0; d < 5; d++) {
            week.add(shift(MONDAY.plusDays(d), 8, 18, 0)); // 5 x 10h = 50h, all with 14h rest in between
        }
        Interval saturday = shift(MONDAY.plusDays(5), 10, 14, 0);
        assertThat(codes(evaluate(saturday, week))).containsExactly("WEEKLY_HOURS");
        // the following Monday starts a new week
        assertThat(codes(evaluate(shift(MONDAY.plusDays(7), 10, 14, 0), week))).isEmpty();
    }

    @Test
    void weeklyHours_countPaidTimeAfterBreaks() {
        List<Interval> week = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            week.add(shift(MONDAY.plusDays(d), 8, 20, 120)); // 12h elapsed, 10h paid each -> 40h
        }
        assertThat(codes(evaluate(shift(MONDAY.plusDays(4), 9, 17, 60), week))).isEmpty(); // +7h = 47h
        assertThat(codes(evaluate(shift(MONDAY.plusDays(4), 9, 19, 60), week))).containsExactly("WEEKLY_HOURS"); // +9h = 49h
    }

    @Test
    void employeeStatusAndBranchMembership() {
        Interval s = shift(MONDAY, 9, 17, 0);
        assertThat(codes(ScheduleRules.evaluate(s, List.of(), POLICY, EmploymentStatus.RESIGNED, true, List.of(), ZONE))).containsExactly("EMPLOYEE_LEFT");
        assertThat(codes(ScheduleRules.evaluate(s, List.of(), POLICY, EmploymentStatus.SUSPENDED, true, List.of(), ZONE))).containsExactly("EMPLOYEE_SUSPENDED");
        List<Issue> onLeave = ScheduleRules.evaluate(s, List.of(), POLICY, EmploymentStatus.ON_LEAVE, true, List.of(), ZONE);
        assertThat(onLeave).singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo(ScheduleRules.Severity.WARNING));
        assertThat(codes(ScheduleRules.evaluate(s, List.of(), POLICY, EmploymentStatus.ACTIVE, false, List.of(), ZONE))).containsExactly("NOT_AT_BRANCH");
    }

    @Test
    void unavailability_matchesWeekdaysDatesAndTimeWindows() {
        AvailabilityEntity mondays = new AvailabilityEntity();
        mondays.setKind(AvailabilityKind.UNAVAILABLE);
        mondays.setDayOfWeek("MON");
        AvailabilityEntity mondayEvenings = new AvailabilityEntity();
        mondayEvenings.setKind(AvailabilityKind.UNAVAILABLE);
        mondayEvenings.setDayOfWeek("MON");
        mondayEvenings.setStartTime(LocalTime.of(18, 0));
        mondayEvenings.setEndTime(LocalTime.of(23, 0));
        AvailabilityEntity oneDay = new AvailabilityEntity();
        oneDay.setKind(AvailabilityKind.UNAVAILABLE);
        oneDay.setSpecificDate(MONDAY.plusDays(2));
        AvailabilityEntity preferred = new AvailabilityEntity();
        preferred.setKind(AvailabilityKind.PREFERRED);
        preferred.setDayOfWeek("MON");

        Interval mondayDay = shift(MONDAY, 9, 17, 0);
        assertThat(codes(ScheduleRules.evaluate(mondayDay, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(mondays), ZONE))).containsExactly("UNAVAILABLE");
        assertThat(codes(ScheduleRules.evaluate(mondayDay, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(mondayEvenings), ZONE))).isEmpty();
        assertThat(codes(ScheduleRules.evaluate(shift(MONDAY, 16, 20, 0), List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(mondayEvenings), ZONE)))
                .containsExactly("UNAVAILABLE");
        assertThat(codes(ScheduleRules.evaluate(shift(MONDAY.plusDays(2), 9, 17, 0), List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(oneDay), ZONE)))
                .containsExactly("UNAVAILABLE");
        assertThat(codes(ScheduleRules.evaluate(mondayDay, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(preferred), ZONE))).isEmpty();
        // an overnight Sunday shift running into Monday touches the Monday rule
        assertThat(codes(ScheduleRules.evaluate(shift(MONDAY.minusDays(1), 22, 6, 0), List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(mondays), ZONE)))
                .containsExactly("UNAVAILABLE");
    }

    @Test
    void templates_handleOvernightShiftsAndDays() {
        ShiftTemplateEntity night = new ShiftTemplateEntity();
        night.setStartTime(LocalTime.of(22, 0));
        night.setEndTime(LocalTime.of(6, 0));
        night.setDays(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
        assertThat(night.crossesMidnight()).isTrue();
        assertThat(night.getDaysOfWeek()).isEqualTo("FRI,SAT");
        Instant start = night.startOn(MONDAY, ZONE);
        Instant end = night.endOn(MONDAY, ZONE);
        assertThat(java.time.Duration.between(start, end).toHours()).isEqualTo(8);
        assertThat(night.days()).containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }

    @Test
    void approvedLeave_blocks_halfDayAndPendingLeaveWarn() {
        Interval mondayShift = shift(MONDAY, 9, 17, 30);
        ScheduleRules.LeavePeriod approvedWeek = new ScheduleRules.LeavePeriod(MONDAY, MONDAY.plusDays(4), false, false, true, "Annual leave");
        ScheduleRules.LeavePeriod approvedHalf = new ScheduleRules.LeavePeriod(MONDAY.minusDays(2), MONDAY, false, true, true, "Annual leave");
        ScheduleRules.LeavePeriod pending = new ScheduleRules.LeavePeriod(MONDAY, MONDAY, false, false, false, "Study leave");
        ScheduleRules.LeavePeriod elsewhere = new ScheduleRules.LeavePeriod(MONDAY.plusDays(1), MONDAY.plusDays(2), false, false, true, "Annual leave");

        List<Issue> blocked = ScheduleRules.evaluate(mondayShift, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(), List.of(approvedWeek), ZONE);
        assertThat(codes(blocked)).containsExactly("ON_APPROVED_LEAVE");
        assertThat(blocked.get(0).severity()).isEqualTo(ScheduleRules.Severity.BLOCKING);
        assertThat(codes(ScheduleRules.evaluate(mondayShift, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(), List.of(approvedHalf), ZONE)))
                .containsExactly("HALF_DAY_LEAVE");
        assertThat(codes(ScheduleRules.evaluate(mondayShift, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(), List.of(pending), ZONE)))
                .containsExactly("LEAVE_PENDING");
        assertThat(ScheduleRules.evaluate(mondayShift, List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(), List.of(elsewhere), ZONE)).isEmpty();
        // an overnight Sunday shift runs into Monday's approved leave
        assertThat(codes(ScheduleRules.evaluate(shift(MONDAY.minusDays(1), 22, 6, 0), List.of(), POLICY, EmploymentStatus.ACTIVE, true, List.of(),
                List.of(approvedWeek), ZONE))).containsExactly("ON_APPROVED_LEAVE");
    }
}
