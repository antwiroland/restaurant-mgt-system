package com.restaurantmanager.core.hr.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Pure leave arithmetic (day counts, pro-rating, accrual, carry-over), free of persistence so it can be unit-tested. */
public final class LeaveCalculator {
    static final BigDecimal HALF = new BigDecimal("0.5");
    static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private LeaveCalculator() {
    }

    /**
     * Leave days between {@code start} and {@code end} (inclusive). CALENDAR counts every day; WORKDAYS skips the
     * organisation's non-working weekdays and observed public holidays. A half flag makes that end day count 0.5 (a
     * single-day request with either flag is a half day).
     */
    public static BigDecimal countDays(LocalDate start, LocalDate end, boolean startHalf, boolean endHalf, CountBasis basis,
                                       Set<DayOfWeek> nonWorkingDays, Set<LocalDate> holidays) {
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (basis == CountBasis.WORKDAYS && (nonWorkingDays.contains(day.getDayOfWeek()) || holidays.contains(day))) {
                continue;
            }
            boolean half = (startHalf && day.equals(start)) || (endHalf && day.equals(end));
            total = total.add(half ? HALF : BigDecimal.ONE);
        }
        return total.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Annual entitlement pro-rated by whole months of employment in the year (the hire month counts), to the nearest half day. */
    public static BigDecimal entitlement(BigDecimal annualDays, int year, LocalDate hireDate) {
        if (hireDate == null || hireDate.getYear() < year) {
            return scale(annualDays);
        }
        if (hireDate.getYear() > year) {
            return scale(BigDecimal.ZERO);
        }
        int months = 12 - hireDate.getMonthValue() + 1;
        return roundToHalf(annualDays.multiply(BigDecimal.valueOf(months)).divide(TWELVE, 4, RoundingMode.HALF_UP));
    }

    /**
     * Days earned by {@code asOf}. UPFRONT: everything on the first day. MONTHLY: an equal share at the start of each month
     * from the accrual start month to December, to the nearest half day; the full entitlement after the year.
     */
    public static BigDecimal earned(BigDecimal entitledDays, Accrual accrual, LocalDate accrualStart, int year, LocalDate asOf) {
        if (accrual != Accrual.MONTHLY || asOf.getYear() > year) {
            return scale(entitledDays);
        }
        if (asOf.getYear() < year) {
            return scale(BigDecimal.ZERO);
        }
        int firstMonth = accrualStart != null && accrualStart.getYear() == year ? accrualStart.getMonthValue() : 1;
        int totalMonths = 12 - firstMonth + 1;
        int elapsed = Math.max(0, asOf.getMonthValue() - firstMonth + 1);
        if (elapsed >= totalMonths) {
            return scale(entitledDays);
        }
        return roundToHalf(entitledDays.multiply(BigDecimal.valueOf(elapsed)).divide(BigDecimal.valueOf(totalMonths), 4, RoundingMode.HALF_UP));
    }

    /** Earned + carried over + adjustments − used − pending. Can be negative when the leave type allows it. */
    public static BigDecimal available(BigDecimal earned, BigDecimal carried, BigDecimal adjustment, BigDecimal used, BigDecimal pending) {
        return scale(earned.add(carried).add(adjustment).subtract(used).subtract(pending));
    }

    /** What may be carried into the next year: the unused balance, never below zero, capped by the policy. */
    public static BigDecimal carryOver(BigDecimal unusedAtYearEnd, BigDecimal maxDays) {
        if (maxDays == null || maxDays.signum() <= 0 || unusedAtYearEnd.signum() <= 0) {
            return scale(BigDecimal.ZERO);
        }
        return scale(unusedAtYearEnd.min(maxDays));
    }

    static BigDecimal roundToHalf(BigDecimal value) {
        return scale(value.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO, 2, RoundingMode.UNNECESSARY));
    }

    static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
