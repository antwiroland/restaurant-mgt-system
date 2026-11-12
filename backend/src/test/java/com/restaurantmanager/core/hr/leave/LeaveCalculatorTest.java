package com.restaurantmanager.core.hr.leave;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveCalculatorTest {
    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    // Monday 2026-11-16
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void workdays_skipWeekendsAndHolidays_calendarCountsEverything() {
        LocalDate nextSunday = MONDAY.plusDays(6);
        assertThat(LeaveCalculator.countDays(MONDAY, nextSunday, false, false, CountBasis.WORKDAYS, WEEKEND, Set.of())).isEqualByComparingTo("5");
        assertThat(LeaveCalculator.countDays(MONDAY, nextSunday, false, false, CountBasis.WORKDAYS, WEEKEND, Set.of(MONDAY.plusDays(2))))
                .isEqualByComparingTo("4");
        assertThat(LeaveCalculator.countDays(MONDAY, nextSunday, false, false, CountBasis.CALENDAR, WEEKEND, Set.of(MONDAY.plusDays(2))))
                .isEqualByComparingTo("7");
        // a restaurant that works weekends counts them
        assertThat(LeaveCalculator.countDays(MONDAY, nextSunday, false, false, CountBasis.WORKDAYS, Set.of(), Set.of())).isEqualByComparingTo("7");
        // only non-working days selected
        assertThat(LeaveCalculator.countDays(MONDAY.plusDays(5), nextSunday, false, false, CountBasis.WORKDAYS, WEEKEND, Set.of())).isZero();
    }

    @Test
    void halfDays_onFirstAndLastDay() {
        LocalDate friday = MONDAY.plusDays(4);
        assertThat(LeaveCalculator.countDays(MONDAY, friday, true, true, CountBasis.WORKDAYS, WEEKEND, Set.of())).isEqualByComparingTo("4");
        assertThat(LeaveCalculator.countDays(MONDAY, friday, false, true, CountBasis.WORKDAYS, WEEKEND, Set.of())).isEqualByComparingTo("4.5");
        assertThat(LeaveCalculator.countDays(MONDAY, MONDAY, true, true, CountBasis.WORKDAYS, WEEKEND, Set.of())).isEqualByComparingTo("0.5");
        // a half flag on a day that does not count changes nothing
        assertThat(LeaveCalculator.countDays(MONDAY, MONDAY.plusDays(5), false, true, CountBasis.WORKDAYS, WEEKEND, Set.of())).isEqualByComparingTo("5");
    }

    @Test
    void entitlement_proRatedByMonthsOfServiceToHalfDays() {
        assertThat(LeaveCalculator.entitlement(d("20"), 2026, LocalDate.of(2020, 5, 1))).isEqualByComparingTo("20");
        assertThat(LeaveCalculator.entitlement(d("20"), 2026, null)).isEqualByComparingTo("20");
        // hired in July: 6 of 12 months
        assertThat(LeaveCalculator.entitlement(d("20"), 2026, LocalDate.of(2026, 7, 20))).isEqualByComparingTo("10");
        // hired in October: 3/12 of 15 = 3.75 -> 4.0 (nearest half)
        assertThat(LeaveCalculator.entitlement(d("15"), 2026, LocalDate.of(2026, 10, 1))).isEqualByComparingTo("4");
        assertThat(LeaveCalculator.entitlement(d("20"), 2026, LocalDate.of(2027, 1, 5))).isZero();
    }

    @Test
    void monthlyAccrual_earnsAShareEachMonthFromTheAccrualStart() {
        LocalDate jan1 = LocalDate.of(2026, 1, 1);
        assertThat(LeaveCalculator.earned(d("24"), Accrual.UPFRONT, jan1, 2026, LocalDate.of(2026, 1, 2))).isEqualByComparingTo("24");
        assertThat(LeaveCalculator.earned(d("24"), Accrual.MONTHLY, jan1, 2026, LocalDate.of(2026, 1, 2))).isEqualByComparingTo("2");
        assertThat(LeaveCalculator.earned(d("24"), Accrual.MONTHLY, jan1, 2026, LocalDate.of(2026, 6, 30))).isEqualByComparingTo("12");
        assertThat(LeaveCalculator.earned(d("24"), Accrual.MONTHLY, jan1, 2026, LocalDate.of(2026, 12, 31))).isEqualByComparingTo("24");
        assertThat(LeaveCalculator.earned(d("24"), Accrual.MONTHLY, jan1, 2026, LocalDate.of(2027, 2, 1))).isEqualByComparingTo("24");
        assertThat(LeaveCalculator.earned(d("24"), Accrual.MONTHLY, jan1, 2026, LocalDate.of(2025, 12, 1))).isZero();
        // hired in July with a pro-rated 10 days: July..December = 6 months
        LocalDate july = LocalDate.of(2026, 7, 20);
        assertThat(LeaveCalculator.earned(d("10"), Accrual.MONTHLY, july, 2026, LocalDate.of(2026, 6, 30))).isZero();
        assertThat(LeaveCalculator.earned(d("10"), Accrual.MONTHLY, july, 2026, LocalDate.of(2026, 9, 1))).isEqualByComparingTo("5");
    }

    @Test
    void availableAndCarryOver() {
        assertThat(LeaveCalculator.available(d("20"), d("3"), d("-1"), d("5"), d("2"))).isEqualByComparingTo("15");
        assertThat(LeaveCalculator.carryOver(d("7.5"), d("5"))).isEqualByComparingTo("5");
        assertThat(LeaveCalculator.carryOver(d("2.5"), d("5"))).isEqualByComparingTo("2.5");
        assertThat(LeaveCalculator.carryOver(d("-2"), d("5"))).isZero();
        assertThat(LeaveCalculator.carryOver(d("8"), BigDecimal.ZERO)).isZero();
    }
}
