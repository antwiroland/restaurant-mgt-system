package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.hr.employee.EmploymentStatus;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure scheduling checks, kept free of persistence so every rule can be unit-tested. BLOCKING issues stop a save;
 * WARNING issues need the scheduler's explicit acknowledgement.
 */
public final class ScheduleRules {
    private ScheduleRules() {
    }

    public enum Severity { BLOCKING, WARNING }

    public record Issue(String code, Severity severity, String message) {}

    public record Interval(Instant start, Instant end, int breakMinutes) {
        public long paidMinutes() {
            return Math.max(0, Duration.between(start, end).toMinutes() - breakMinutes);
        }
    }

    public record Policy(int minRestMinutes, int maxShiftMinutes, int maxWeeklyMinutes, String source) {}

    /** A pending or approved leave request of the employee (inclusive dates; half flags apply to the first/last day). */
    public record LeavePeriod(LocalDate start, LocalDate end, boolean startHalf, boolean endHalf, boolean approved, String typeName) {
        boolean halfOn(LocalDate day) {
            return (startHalf && day.equals(start)) || (endHalf && day.equals(end));
        }
    }

    public static List<Issue> evaluate(Interval candidate, List<Interval> others, Policy policy, EmploymentStatus status,
                                       boolean assignedToBranch, List<AvailabilityEntity> availability, ZoneId zone) {
        return evaluate(candidate, others, policy, status, assignedToBranch, availability, List.of(), zone);
    }

    /**
     * @param candidate      the shift being saved
     * @param others         the employee's other non-cancelled shifts around the candidate (not including it)
     * @param assignedToBranch whether the employee belongs to the shift's branch on that date (primary or active assignment)
     */
    public static List<Issue> evaluate(Interval candidate, List<Interval> others, Policy policy, EmploymentStatus status,
                                       boolean assignedToBranch, List<AvailabilityEntity> availability, List<LeavePeriod> leave, ZoneId zone) {
        List<Issue> issues = new ArrayList<>();
        if (status != null && status.isExit()) {
            issues.add(new Issue("EMPLOYEE_LEFT", Severity.BLOCKING, "This person no longer works here"));
        } else if (status == EmploymentStatus.SUSPENDED) {
            issues.add(new Issue("EMPLOYEE_SUSPENDED", Severity.BLOCKING, "This employee is suspended"));
        } else if (status == EmploymentStatus.ON_LEAVE) {
            issues.add(new Issue("EMPLOYEE_ON_LEAVE", Severity.WARNING, "This employee is currently on leave"));
        }
        if (!assignedToBranch) {
            issues.add(new Issue("NOT_AT_BRANCH", Severity.BLOCKING,
                    "This employee is not assigned to this branch on that date. Add a branch assignment first."));
        }
        for (Interval other : others) {
            if (other.start().isBefore(candidate.end()) && other.end().isAfter(candidate.start())) {
                issues.add(new Issue("OVERLAP", Severity.BLOCKING, "Overlaps another shift (" + describe(other, zone) + ")"));
            }
        }
        long length = Duration.between(candidate.start(), candidate.end()).toMinutes();
        if (length > policy.maxShiftMinutes()) {
            issues.add(new Issue("LONG_SHIFT", Severity.WARNING,
                    "Shift is " + hours(length) + " long; the limit is " + hours(policy.maxShiftMinutes())));
        }
        restIssue(candidate, others, policy).ifPresent(issues::add);
        long weekly = weeklyPaidMinutes(candidate, others, zone);
        if (weekly > policy.maxWeeklyMinutes()) {
            issues.add(new Issue("WEEKLY_HOURS", Severity.WARNING,
                    "Brings the week to " + hours(weekly) + "; the limit is " + hours(policy.maxWeeklyMinutes())));
        }
        leaveIssue(candidate, leave, zone).ifPresent(issues::add);
        for (AvailabilityEntity rule : availability) {
            if (rule.getKind() == AvailabilityKind.UNAVAILABLE && conflicts(rule, candidate, zone)) {
                issues.add(new Issue("UNAVAILABLE", Severity.WARNING, "Marked unavailable" + (rule.getNote() == null ? "" : ": " + rule.getNote())));
                break;
            }
        }
        return issues;
    }

    /**
     * Approved full-day leave on a day the shift touches blocks it; approved half-day leave and pending requests are
     * warnings so the scheduler can still plan around them.
     */
    static java.util.Optional<Issue> leaveIssue(Interval shift, List<LeavePeriod> leave, ZoneId zone) {
        LocalDate first = shift.start().atZone(zone).toLocalDate();
        LocalDate last = shift.end().minusSeconds(1).atZone(zone).toLocalDate();
        Issue warning = null;
        for (LeavePeriod period : leave) {
            for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                if (day.isBefore(period.start()) || day.isAfter(period.end())) {
                    continue;
                }
                if (period.approved() && !period.halfOn(day)) {
                    return java.util.Optional.of(new Issue("ON_APPROVED_LEAVE", Severity.BLOCKING,
                            "On approved " + period.typeName().toLowerCase() + " (" + period.start() + " – " + period.end() + ")"));
                }
                if (warning == null) {
                    warning = period.approved()
                            ? new Issue("HALF_DAY_LEAVE", Severity.WARNING, "On approved half-day " + period.typeName().toLowerCase() + " on " + day)
                            : new Issue("LEAVE_PENDING", Severity.WARNING, "Has a pending " + period.typeName().toLowerCase() + " request for " + day);
                }
            }
        }
        return java.util.Optional.ofNullable(warning);
    }

    static java.util.Optional<Issue> restIssue(Interval candidate, List<Interval> others, Policy policy) {
        if (policy.minRestMinutes() <= 0) {
            return java.util.Optional.empty();
        }
        long shortest = Long.MAX_VALUE;
        for (Interval other : others) {
            long gap;
            if (!other.end().isAfter(candidate.start())) {
                gap = Duration.between(other.end(), candidate.start()).toMinutes();
            } else if (!other.start().isBefore(candidate.end())) {
                gap = Duration.between(candidate.end(), other.start()).toMinutes();
            } else {
                continue; // overlapping shifts are reported separately
            }
            shortest = Math.min(shortest, gap);
        }
        if (shortest < policy.minRestMinutes()) {
            return java.util.Optional.of(new Issue("SHORT_REST", Severity.WARNING,
                    "Only " + hours(shortest) + " rest between shifts; the minimum is " + hours(policy.minRestMinutes())));
        }
        return java.util.Optional.empty();
    }

    /** Paid minutes of all shifts starting in the candidate's week (Monday to Sunday, local time), including the candidate. */
    static long weeklyPaidMinutes(Interval candidate, List<Interval> others, ZoneId zone) {
        ZonedDateTime weekStart = candidate.start().atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone);
        Instant from = weekStart.toInstant();
        Instant to = weekStart.plusWeeks(1).toInstant();
        long total = candidate.paidMinutes();
        for (Interval other : others) {
            if (!other.start().isBefore(from) && other.start().isBefore(to)) {
                total += other.paidMinutes();
            }
        }
        return total;
    }

    /** An UNAVAILABLE rule conflicts when it applies on a date the shift touches and its time window (if any) overlaps. */
    static boolean conflicts(AvailabilityEntity rule, Interval shift, ZoneId zone) {
        LocalDate first = shift.start().atZone(zone).toLocalDate();
        LocalDate last = shift.end().minusSeconds(1).atZone(zone).toLocalDate();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (!rule.appliesOn(day)) {
                continue;
            }
            if (rule.getStartTime() == null) {
                return true;
            }
            Instant windowStart = day.atTime(rule.getStartTime()).atZone(zone).toInstant();
            LocalTime endTime = rule.getEndTime();
            Instant windowEnd = (endTime.isAfter(rule.getStartTime()) ? day : day.plusDays(1)).atTime(endTime).atZone(zone).toInstant();
            if (windowStart.isBefore(shift.end()) && windowEnd.isAfter(shift.start())) {
                return true;
            }
        }
        return false;
    }

    static String hours(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private static String describe(Interval interval, ZoneId zone) {
        ZonedDateTime s = interval.start().atZone(zone);
        ZonedDateTime e = interval.end().atZone(zone);
        return String.format("%s %02d:%02d–%02d:%02d", s.getDayOfWeek().toString().substring(0, 3), s.getHour(), s.getMinute(), e.getHour(), e.getMinute());
    }
}
