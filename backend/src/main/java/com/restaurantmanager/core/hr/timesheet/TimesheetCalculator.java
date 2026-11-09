package com.restaurantmanager.core.hr.timesheet;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Pure overtime arithmetic for a timesheet, kept free of persistence for unit testing. Works in whole minutes and never
 * converts to money; multipliers are carried for payroll.
 *
 * <ul>
 *   <li>Daily overtime: paid minutes in a day beyond the daily threshold (rule in force that day).</li>
 *   <li>Weekly overtime: per Monday–Sunday week, paid minutes not already counted as daily overtime beyond the weekly
 *       threshold (rule in force on the week's first worked day). Only days inside the timesheet period are counted.</li>
 *   <li>Holiday / weekend minutes: paid minutes on public holidays / the rule's weekend days (premium categories,
 *       reported alongside, not added to overtime).</li>
 *   <li>Night minutes: worked time inside the rule's night window, capped at the piece's paid minutes.</li>
 * </ul>
 */
public final class TimesheetCalculator {
    private TimesheetCalculator() {
    }

    public record Rule(Integer dailyThresholdMinutes, Integer weeklyThresholdMinutes, BigDecimal dailyMultiplier, BigDecimal weeklyMultiplier,
                       Set<DayOfWeek> weekendDays, BigDecimal weekendMultiplier, BigDecimal holidayMultiplier,
                       LocalTime nightStart, LocalTime nightEnd, BigDecimal nightMultiplier, boolean requiresApproval, String source) {}

    /** A worked period attributed to {@code workDate} (the local date it started). */
    public record Piece(LocalDate workDate, Instant start, Instant end, int paidMinutes) {}

    public record Day(LocalDate date, int paidMinutes, int dailyOvertimeMinutes, boolean holiday, boolean weekend, int nightMinutes) {}

    public record Result(List<Day> days, int paidMinutes, int regularMinutes, int dailyOvertimeMinutes, int weeklyOvertimeMinutes,
                         int holidayMinutes, int weekendMinutes, int nightMinutes, boolean requiresApproval) {
        public int overtimeMinutes() {
            return dailyOvertimeMinutes + weeklyOvertimeMinutes;
        }
    }

    public static Result compute(List<Piece> pieces, Function<LocalDate, Rule> ruleFor, Set<LocalDate> holidays, ZoneId zone) {
        Map<LocalDate, List<Piece>> byDay = new TreeMap<>();
        for (Piece p : pieces) {
            byDay.computeIfAbsent(p.workDate(), k -> new ArrayList<>()).add(p);
        }
        List<Day> days = new ArrayList<>();
        int paid = 0, dailyOt = 0, holiday = 0, weekend = 0, night = 0;
        boolean requiresApproval = false;
        Map<LocalDate, int[]> weeks = new TreeMap<>(); // week start -> [non-daily-OT paid minutes]
        Map<LocalDate, Rule> weekRule = new TreeMap<>();
        for (Map.Entry<LocalDate, List<Piece>> entry : byDay.entrySet()) {
            LocalDate date = entry.getKey();
            Rule rule = ruleFor.apply(date);
            int dayPaid = entry.getValue().stream().mapToInt(Piece::paidMinutes).sum();
            int dayOt = rule.dailyThresholdMinutes() == null ? 0 : Math.max(0, dayPaid - rule.dailyThresholdMinutes());
            boolean isHoliday = holidays.contains(date);
            boolean isWeekend = rule.weekendDays() != null && rule.weekendDays().contains(date.getDayOfWeek());
            int dayNight = 0;
            for (Piece p : entry.getValue()) {
                dayNight += Math.min(p.paidMinutes(), nightMinutes(p, rule, zone));
            }
            days.add(new Day(date, dayPaid, dayOt, isHoliday, isWeekend, dayNight));
            paid += dayPaid;
            dailyOt += dayOt;
            holiday += isHoliday ? dayPaid : 0;
            weekend += isWeekend ? dayPaid : 0;
            night += dayNight;
            requiresApproval |= dayPaid > 0 && rule.requiresApproval();
            LocalDate week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeks.computeIfAbsent(week, k -> new int[1])[0] += dayPaid - dayOt;
            weekRule.putIfAbsent(week, rule);
        }
        int weeklyOt = 0;
        for (Map.Entry<LocalDate, int[]> week : weeks.entrySet()) {
            Integer threshold = weekRule.get(week.getKey()).weeklyThresholdMinutes();
            if (threshold != null) {
                weeklyOt += Math.max(0, week.getValue()[0] - threshold);
            }
        }
        return new Result(days, paid, paid - dailyOt - weeklyOt, dailyOt, weeklyOt, holiday, weekend, night, requiresApproval);
    }

    /** Minutes of [start, end) inside the night window of the start date or the day before (windows may cross midnight). */
    static int nightMinutes(Piece piece, Rule rule, ZoneId zone) {
        if (rule.nightStart() == null || rule.nightEnd() == null || piece.end() == null) {
            return 0;
        }
        long total = 0;
        LocalDate startDay = piece.start().atZone(zone).toLocalDate();
        for (LocalDate day = startDay.minusDays(1); !day.isAfter(piece.end().atZone(zone).toLocalDate()); day = day.plusDays(1)) {
            Instant windowStart = day.atTime(rule.nightStart()).atZone(zone).toInstant();
            LocalDate endDay = rule.nightEnd().isAfter(rule.nightStart()) ? day : day.plusDays(1);
            Instant windowEnd = endDay.atTime(rule.nightEnd()).atZone(zone).toInstant();
            Instant from = piece.start().isAfter(windowStart) ? piece.start() : windowStart;
            Instant to = piece.end().isBefore(windowEnd) ? piece.end() : windowEnd;
            if (to.isAfter(from)) {
                total += Duration.between(from, to).toMinutes();
            }
        }
        return (int) total;
    }
}
