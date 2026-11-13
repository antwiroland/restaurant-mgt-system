package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.hr.contract.SalaryType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure payroll arithmetic: earnings from a contract and locked timesheets, then deductions and employer contributions
 * from the rules in force. Money is rounded to 2 decimals (half up) per line; rates keep 4 decimals.
 *
 * <p>Order: earnings → gross; pre-tax employee deductions (base BASIC or GROSS) → taxable = taxable earnings − pre-tax;
 * other employee deductions (base BASIC, GROSS or TAXABLE); employer contributions; one-off deductions; net = gross −
 * every employee deduction.</p>
 */
public final class PayrollCalculator {
    static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final BigDecimal WEEKS_PER_YEAR = BigDecimal.valueOf(52);
    static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    static final BigDecimal DEFAULT_WEEKLY_HOURS = BigDecimal.valueOf(40);
    static final int WORKDAYS_PER_WEEK = 5;

    private PayrollCalculator() {
    }

    public record Line(PayslipLineKind kind, String code, String description, BigDecimal quantity, BigDecimal rate, BigDecimal amount,
                       boolean taxable) {}

    public record Bracket(BigDecimal upTo, BigDecimal ratePercent) {}

    public record RuleSpec(String code, String name, PayrollRuleKind kind, PayrollRuleMethod method, PayrollRuleBase base,
                           BigDecimal ratePercent, BigDecimal fixedAmount, BigDecimal capAmount, List<Bracket> brackets, boolean preTax) {}

    /** Everything needed to price one employee's period. Minutes come from locked timesheets inside the period. */
    public record EarningsInput(SalaryType salaryType, BigDecimal rate, BigDecimal weeklyHours, LocalDate periodStart, LocalDate periodEnd,
                                LocalDate employedFrom, LocalDate employedTo, int paidMinutes, int payableOvertimeMinutes, int workedDays,
                                int holidayMinutes, int weekendMinutes, int nightMinutes, BigDecimal overtimeMultiplier,
                                BigDecimal holidayMultiplier, BigDecimal weekendMultiplier, BigDecimal nightMultiplier,
                                BigDecimal unpaidLeaveDays) {}

    public record Earnings(BigDecimal hourlyRate, BigDecimal basic, List<Line> lines, List<String> issues) {}

    public record Result(BigDecimal basic, BigDecimal gross, BigDecimal taxable, BigDecimal employeeDeductions, BigDecimal net,
                         BigDecimal employerContributions, List<Line> lines) {}

    // ------------------------------------------------------------ earnings

    /** The hourly equivalent of a contract's pay, as payroll works it out; null for pay types it cannot price. */
    public static BigDecimal hourlyRate(SalaryType type, BigDecimal rate, BigDecimal weeklyHours) {
        if (type == null || rate == null) {
            return null;
        }
        BigDecimal hours = weeklyHours == null || weeklyHours.signum() <= 0 ? DEFAULT_WEEKLY_HOURS : weeklyHours;
        return switch (type) {
            case MONTHLY -> rate.multiply(MONTHS_PER_YEAR).divide(WEEKS_PER_YEAR.multiply(hours), 4, RoundingMode.HALF_UP);
            case WEEKLY -> rate.divide(hours, 4, RoundingMode.HALF_UP);
            case DAILY -> rate.multiply(BigDecimal.valueOf(WORKDAYS_PER_WEEK)).divide(hours, 4, RoundingMode.HALF_UP);
            case HOURLY -> rate.setScale(4, RoundingMode.HALF_UP);
            default -> null;
        };
    }

    public static Earnings earnings(EarningsInput in) {
        List<Line> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        BigDecimal weeklyHours = in.weeklyHours() == null || in.weeklyHours().signum() <= 0 ? DEFAULT_WEEKLY_HOURS : in.weeklyHours();
        BigDecimal rate = in.rate() == null ? BigDecimal.ZERO : in.rate();
        long periodDays = ChronoUnit.DAYS.between(in.periodStart(), in.periodEnd()) + 1;
        long employedDays = in.employedTo().isBefore(in.employedFrom()) ? 0 : ChronoUnit.DAYS.between(in.employedFrom(), in.employedTo()) + 1;
        BigDecimal hourly;
        BigDecimal dailyRate;
        switch (in.salaryType()) {
            case MONTHLY -> {
                hourly = rate.multiply(MONTHS_PER_YEAR).divide(WEEKS_PER_YEAR.multiply(weeklyHours), 4, RoundingMode.HALF_UP);
                dailyRate = rate.multiply(MONTHS_PER_YEAR).divide(WEEKS_PER_YEAR.multiply(BigDecimal.valueOf(WORKDAYS_PER_WEEK)), 4, RoundingMode.HALF_UP);
                int monthDays = in.periodStart().lengthOfMonth();
                BigDecimal amount = money(rate.multiply(BigDecimal.valueOf(employedDays)).divide(BigDecimal.valueOf(monthDays), 6, RoundingMode.HALF_UP));
                if (employedDays >= monthDays) {
                    amount = money(rate);
                }
                lines.add(earning("BASIC", "Monthly salary" + (employedDays < monthDays ? " (" + employedDays + " of " + monthDays + " days)" : ""),
                        BigDecimal.valueOf(employedDays), rate, amount));
                if (employedDays < periodDays) issues.add("PARTIAL_PERIOD");
            }
            case WEEKLY -> {
                hourly = rate.divide(weeklyHours, 4, RoundingMode.HALF_UP);
                dailyRate = rate.divide(BigDecimal.valueOf(WORKDAYS_PER_WEEK), 4, RoundingMode.HALF_UP);
                BigDecimal amount = money(rate.multiply(BigDecimal.valueOf(employedDays)).divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP));
                lines.add(earning("BASIC", "Weekly wage (" + employedDays + " days)", BigDecimal.valueOf(employedDays), rate, amount));
                if (employedDays < periodDays) issues.add("PARTIAL_PERIOD");
            }
            case DAILY -> {
                dailyRate = rate;
                hourly = rate.multiply(BigDecimal.valueOf(WORKDAYS_PER_WEEK)).divide(weeklyHours, 4, RoundingMode.HALF_UP);
                lines.add(earning("BASIC", "Days worked", BigDecimal.valueOf(in.workedDays()), rate,
                        money(rate.multiply(BigDecimal.valueOf(in.workedDays())))));
            }
            case HOURLY -> {
                hourly = rate.setScale(4, RoundingMode.HALF_UP);
                dailyRate = BigDecimal.ZERO;
                // hours at the normal rate: everything paid except the overtime approved for the premium rate
                int normalMinutes = Math.max(0, in.paidMinutes() - in.payableOvertimeMinutes());
                BigDecimal hours = hours(normalMinutes);
                lines.add(earning("BASIC", "Hours worked", hours, rate, money(rate.multiply(BigDecimal.valueOf(normalMinutes)).divide(SIXTY, 6, RoundingMode.HALF_UP))));
            }
            default -> {
                hourly = BigDecimal.ZERO;
                dailyRate = BigDecimal.ZERO;
                issues.add("MANUAL_PAY_TYPE");
            }
        }
        BigDecimal basic = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if ((in.salaryType() == SalaryType.MONTHLY || in.salaryType() == SalaryType.WEEKLY)
                && in.unpaidLeaveDays() != null && in.unpaidLeaveDays().signum() > 0) {
            BigDecimal deduction = money(dailyRate.multiply(in.unpaidLeaveDays())).min(basic);
            lines.add(new Line(PayslipLineKind.EARNING, "UNPAID_LEAVE", "Unpaid leave", in.unpaidLeaveDays(), dailyRate, deduction.negate(), true));
            basic = basic.subtract(deduction);
        }
        if (hourly.signum() > 0) {
            if (in.payableOvertimeMinutes() > 0) {
                BigDecimal mult = multiplier(in.overtimeMultiplier(), BigDecimal.ONE);
                BigDecimal r = hourly.multiply(mult).setScale(4, RoundingMode.HALF_UP);
                lines.add(earning("OVERTIME", "Approved overtime ×" + mult.stripTrailingZeros().toPlainString(), hours(in.payableOvertimeMinutes()), r,
                        money(r.multiply(BigDecimal.valueOf(in.payableOvertimeMinutes())).divide(SIXTY, 6, RoundingMode.HALF_UP))));
            }
            premium(lines, "HOLIDAY", "Public holiday premium", in.holidayMinutes(), hourly, in.holidayMultiplier());
            premium(lines, "WEEKEND", "Weekend premium", in.weekendMinutes(), hourly, in.weekendMultiplier());
            premium(lines, "NIGHT", "Night premium", in.nightMinutes(), hourly, in.nightMultiplier());
        }
        return new Earnings(hourly, money(basic), lines, issues);
    }

    /** Extra pay for time already paid at the normal rate: minutes × hourly × (multiplier − 1). */
    private static void premium(List<Line> lines, String code, String label, int minutes, BigDecimal hourly, BigDecimal multiplier) {
        if (minutes <= 0 || multiplier == null || multiplier.compareTo(BigDecimal.ONE) <= 0) {
            return;
        }
        BigDecimal r = hourly.multiply(multiplier.subtract(BigDecimal.ONE)).setScale(4, RoundingMode.HALF_UP);
        lines.add(earning(code, label + " (×" + multiplier.stripTrailingZeros().toPlainString() + ")", hours(minutes), r,
                money(r.multiply(BigDecimal.valueOf(minutes)).divide(SIXTY, 6, RoundingMode.HALF_UP))));
    }

    // ------------------------------------------------------------ deductions

    /**
     * @param earnings     earning lines (including one-off earnings; taxable flag decides what counts towards taxable pay)
     * @param basic        basic pay (base for BASIC rules)
     * @param rules        rules in force for this employee, in display order
     * @param otherDeductions one-off post-tax deductions (amounts positive)
     */
    public static Result apply(List<Line> earnings, BigDecimal basic, List<RuleSpec> rules, List<Line> otherDeductions) {
        List<Line> lines = new ArrayList<>(earnings);
        BigDecimal gross = money(earnings.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal taxableEarnings = money(earnings.stream().filter(Line::taxable).map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal deductions = BigDecimal.ZERO;

        BigDecimal preTax = BigDecimal.ZERO;
        for (RuleSpec rule : rules) {
            if (rule.kind() == PayrollRuleKind.EMPLOYEE_DEDUCTION && rule.preTax()) {
                BigDecimal amount = amount(rule, rule.base() == PayrollRuleBase.BASIC ? basic : gross);
                lines.add(new Line(PayslipLineKind.DEDUCTION, rule.code(), rule.name(), null, rule.ratePercent(), amount, false));
                preTax = preTax.add(amount);
            }
        }
        deductions = deductions.add(preTax);
        BigDecimal taxable = money(taxableEarnings.subtract(preTax).max(BigDecimal.ZERO));

        for (RuleSpec rule : rules) {
            if (rule.kind() == PayrollRuleKind.EMPLOYEE_DEDUCTION && !rule.preTax()) {
                BigDecimal amount = amount(rule, base(rule.base(), basic, gross, taxable));
                lines.add(new Line(PayslipLineKind.DEDUCTION, rule.code(), rule.name(), null, rule.ratePercent(), amount, false));
                deductions = deductions.add(amount);
            }
        }
        BigDecimal employer = BigDecimal.ZERO;
        for (RuleSpec rule : rules) {
            if (rule.kind() == PayrollRuleKind.EMPLOYER_CONTRIBUTION) {
                BigDecimal amount = amount(rule, base(rule.base(), basic, gross, taxable));
                lines.add(new Line(PayslipLineKind.EMPLOYER, rule.code(), rule.name(), null, rule.ratePercent(), amount, false));
                employer = employer.add(amount);
            }
        }
        for (Line d : otherDeductions) {
            lines.add(d);
            deductions = deductions.add(d.amount());
        }
        deductions = money(deductions);
        return new Result(money(basic), gross, taxable, deductions, money(gross.subtract(deductions)), money(employer), lines);
    }

    static BigDecimal base(PayrollRuleBase base, BigDecimal basic, BigDecimal gross, BigDecimal taxable) {
        return switch (base) {
            case BASIC -> basic;
            case GROSS -> gross;
            case TAXABLE -> taxable;
        };
    }

    /** The rule's amount on {@code base}; never negative, capped when the rule has a cap. */
    static BigDecimal amount(RuleSpec rule, BigDecimal base) {
        BigDecimal positive = base.max(BigDecimal.ZERO);
        BigDecimal amount = switch (rule.method()) {
            case PERCENT -> positive.multiply(rule.ratePercent()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
            case FIXED -> rule.fixedAmount();
            case BRACKETS -> brackets(positive, rule.brackets());
        };
        if (rule.capAmount() != null) {
            amount = amount.min(rule.capAmount());
        }
        return money(amount);
    }

    /** Progressive bands: each band's rate applies to the part of {@code base} inside it. Bands ascend; the last has no upper bound. */
    static BigDecimal brackets(BigDecimal base, List<Bracket> brackets) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (Bracket band : brackets) {
            if (base.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal upper = band.upTo() == null ? base : band.upTo().min(base);
            BigDecimal slice = upper.subtract(lower);
            if (slice.signum() > 0) {
                total = total.add(slice.multiply(band.ratePercent()).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            }
            if (band.upTo() == null) {
                break;
            }
            lower = band.upTo();
        }
        return total;
    }

    /** Checks a bracket table: ascending limits, only the last one open-ended, rates between 0 and 100. */
    static String bracketProblem(List<Bracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return "Add at least one band";
        }
        BigDecimal previous = BigDecimal.ZERO;
        for (int i = 0; i < brackets.size(); i++) {
            Bracket b = brackets.get(i);
            if (b.ratePercent() == null || b.ratePercent().signum() < 0 || b.ratePercent().compareTo(HUNDRED) > 0) {
                return "Band rates must be between 0 and 100";
            }
            boolean last = i == brackets.size() - 1;
            if (b.upTo() == null && !last) {
                return "Only the last band can be open-ended";
            }
            if (b.upTo() != null) {
                if (b.upTo().compareTo(previous) <= 0) {
                    return "Band limits must increase";
                }
                previous = b.upTo();
            }
        }
        return brackets.get(brackets.size() - 1).upTo() == null ? null : "The last band must be open-ended";
    }

    private static Line earning(String code, String description, BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
        return new Line(PayslipLineKind.EARNING, code, description, quantity, rate, amount, true);
    }

    private static BigDecimal multiplier(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() <= 0 ? fallback : value;
    }

    static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
