package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.timesheet.OvertimeSettingsService;
import com.restaurantmanager.core.workforce.WorkScheduleEntity;
import com.restaurantmanager.core.workforce.WorkScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How many days a leave request costs. Calendar types count every day. Working-day types count the days the person
 * would actually have worked, which in a restaurant are not Monday to Friday:
 * <ol>
 *   <li>in a week where they already have shifts on the rota, the days they are scheduled;</li>
 *   <li>otherwise their usual pattern: weekdays they worked at least twice in the 8 weeks before the leave;</li>
 *   <li>with no shift history at all, the standard week (system non-working days skipped).</li>
 * </ol>
 * Public holidays are never counted. Half days apply to the first and last day when those are working days.
 */
@Component
public class LeaveDayCounter {
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final WorkScheduleRepository scheduleRepository;
    private final OvertimeSettingsService overtimeSettings;
    private final LeaveSetupService setup;
    private final ZoneId zone;

    public LeaveDayCounter(WorkScheduleRepository scheduleRepository, OvertimeSettingsService overtimeSettings, LeaveSetupService setup,
                           @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.scheduleRepository = scheduleRepository;
        this.overtimeSettings = overtimeSettings;
        this.setup = setup;
        this.zone = ZoneId.of(timeZone);
    }

    /** {@code nextYearDays}: the part of {@code days} that falls in the year after the start year. {@code basis}: ROTA, USUAL_PATTERN, STANDARD_WEEK or CALENDAR. */
    public record Count(BigDecimal days, BigDecimal nextYearDays, String basis) {}

    public Count count(EmployeeEntity employee, LeaveTypeEntity type, LocalDate start, LocalDate end, boolean startHalf, boolean endHalf) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal nextYear = BigDecimal.ZERO;
        if (type.getCountBasis() == CountBasis.CALENDAR) {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                BigDecimal d = (startHalf && day.equals(start)) || (endHalf && day.equals(end)) ? HALF : BigDecimal.ONE;
                total = total.add(d);
                if (day.getYear() > start.getYear()) nextYear = nextYear.add(d);
            }
            return new Count(total.setScale(2), nextYear.setScale(2), "CALENDAR");
        }
        Set<LocalDate> holidays = overtimeSettings.observedHolidays(employee.primaryBranchId(), start, end);
        LocalDate historyFrom = start.minusWeeks(8);
        LocalDate rotaFrom = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate rotaTo = end.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Set<LocalDate> scheduled = new HashSet<>();
        Map<DayOfWeek, Integer> history = new EnumMap<>(DayOfWeek.class);
        if (employee.getId() != null) {
            LocalDate from = historyFrom.isBefore(rotaFrom) ? historyFrom : rotaFrom;
            for (WorkScheduleEntity s : scheduleRepository.findActiveForEmployee(from.atStartOfDay(zone).toInstant(),
                    rotaTo.plusDays(1).atStartOfDay(zone).toInstant(), employee.getId())) {
                LocalDate day = s.getStartsAt().atZone(zone).toLocalDate();
                if (!day.isBefore(rotaFrom)) scheduled.add(day);
                if (!day.isBefore(historyFrom) && day.isBefore(start)) history.merge(day.getDayOfWeek(), 1, Integer::sum);
            }
        }
        Set<DayOfWeek> usual = new HashSet<>();
        history.forEach((dow, n) -> { if (n >= 2) usual.add(dow); });
        Set<DayOfWeek> standardOff = setup.nonWorkingDays();

        boolean usedRota = false;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (holidays.contains(day)) continue;
            LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            boolean rotaWeek = false;
            for (int i = 0; i < 7 && !rotaWeek; i++) rotaWeek = scheduled.contains(monday.plusDays(i));
            boolean working;
            if (rotaWeek) {
                working = scheduled.contains(day);
                usedRota = true;
            } else if (!usual.isEmpty()) {
                working = usual.contains(day.getDayOfWeek());
            } else {
                working = !standardOff.contains(day.getDayOfWeek());
            }
            if (!working) continue;
            BigDecimal d = (startHalf && day.equals(start)) || (endHalf && day.equals(end)) ? HALF : BigDecimal.ONE;
            total = total.add(d);
            if (day.getYear() > start.getYear()) nextYear = nextYear.add(d);
        }
        String basis = usedRota ? "ROTA" : !usual.isEmpty() ? "USUAL_PATTERN" : "STANDARD_WEEK";
        return new Count(total.setScale(2), nextYear.setScale(2), basis);
    }
}
