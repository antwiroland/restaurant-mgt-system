package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.hr.contract.SalaryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollCalculatorTest {
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static PayrollCalculator.EarningsInput input(SalaryType type, String rate, LocalDate from, LocalDate to, int paid, int ot, int days,
                                                         int holiday, String unpaidLeave) {
        return new PayrollCalculator.EarningsInput(type, d(rate), d("40"), NOV_1, NOV_30, from, to, paid, ot, days, holiday, 0, 0,
                d("1.5"), d("2.0"), null, null, d(unpaidLeave));
    }

    private static BigDecimal line(List<PayrollCalculator.Line> lines, String code) {
        return lines.stream().filter(l -> l.code().equals(code)).map(PayrollCalculator.Line::amount).findFirst().orElse(null);
    }

    @Test
    void monthly_fullMonth_partialMonth_andUnpaidLeave() {
        PayrollCalculator.Earnings full = PayrollCalculator.earnings(input(SalaryType.MONTHLY, "3000", NOV_1, NOV_30, 0, 0, 0, 0, "0"));
        assertThat(full.basic()).isEqualByComparingTo("3000.00");
        assertThat(full.hourlyRate()).isEqualByComparingTo("17.3077"); // 3000 × 12 / (52 × 40)
        assertThat(full.issues()).isEmpty();

        PayrollCalculator.Earnings joiner = PayrollCalculator.earnings(input(SalaryType.MONTHLY, "3000", LocalDate.of(2026, 11, 16), NOV_30, 0, 0, 0, 0, "0"));
        assertThat(joiner.basic()).isEqualByComparingTo("1500.00");
        assertThat(joiner.issues()).containsExactly("PARTIAL_PERIOD");

        PayrollCalculator.Earnings leave = PayrollCalculator.earnings(input(SalaryType.MONTHLY, "3000", NOV_1, NOV_30, 0, 0, 0, 0, "2"));
        assertThat(line(leave.lines(), "UNPAID_LEAVE")).isEqualByComparingTo("-276.92"); // 2 × 3000 × 12 / 260
        assertThat(leave.basic()).isEqualByComparingTo("2723.08");
    }

    @Test
    void hourly_paysNormalHoursOnce_andApprovedOvertimeAtTheMultiplier_plusHolidayPremium() {
        PayrollCalculator.Earnings e = PayrollCalculator.earnings(input(SalaryType.HOURLY, "20", NOV_1, NOV_30, 600, 120, 2, 240, "0"));
        assertThat(line(e.lines(), "BASIC")).isEqualByComparingTo("160.00");    // (600 − 120) min at 20/h
        assertThat(line(e.lines(), "OVERTIME")).isEqualByComparingTo("60.00");  // 2 h × 20 × 1.5
        assertThat(line(e.lines(), "HOLIDAY")).isEqualByComparingTo("80.00");   // 4 h × 20 × (2.0 − 1)
        assertThat(e.basic()).isEqualByComparingTo("160.00");
        // unpaid leave never applies to hours that were not worked anyway
        assertThat(line(PayrollCalculator.earnings(input(SalaryType.HOURLY, "20", NOV_1, NOV_30, 600, 0, 2, 0, "3")).lines(), "UNPAID_LEAVE")).isNull();
    }

    @Test
    void daily_andManualPayTypes() {
        PayrollCalculator.Earnings daily = PayrollCalculator.earnings(input(SalaryType.DAILY, "120", NOV_1, NOV_30, 4800, 0, 10, 0, "0"));
        assertThat(daily.basic()).isEqualByComparingTo("1200.00");
        assertThat(daily.hourlyRate()).isEqualByComparingTo("15.0000"); // 120 × 5 / 40
        PayrollCalculator.Earnings commission = PayrollCalculator.earnings(input(SalaryType.COMMISSION, "0", NOV_1, NOV_30, 0, 0, 0, 0, "0"));
        assertThat(commission.basic()).isZero();
        assertThat(commission.issues()).containsExactly("MANUAL_PAY_TYPE");
    }

    @Test
    void brackets_areProgressive_andValidated() {
        List<PayrollCalculator.Bracket> bands = List.of(new PayrollCalculator.Bracket(d("490"), d("0")), new PayrollCalculator.Bracket(d("600"), d("5")),
                new PayrollCalculator.Bracket(d("730"), d("10")), new PayrollCalculator.Bracket(null, d("17.5")));
        assertThat(PayrollCalculator.brackets(d("1000"), bands)).isEqualByComparingTo("65.75"); // 0 + 5.5 + 13 + 47.25
        assertThat(PayrollCalculator.brackets(d("400"), bands)).isZero();
        assertThat(PayrollCalculator.brackets(d("600"), bands)).isEqualByComparingTo("5.5");
        assertThat(PayrollCalculator.bracketProblem(bands)).isNull();
        assertThat(PayrollCalculator.bracketProblem(List.of(new PayrollCalculator.Bracket(d("500"), d("5"))))).isEqualTo("The last band must be open-ended");
        assertThat(PayrollCalculator.bracketProblem(List.of(new PayrollCalculator.Bracket(d("500"), d("5")), new PayrollCalculator.Bracket(d("400"), d("5")),
                new PayrollCalculator.Bracket(null, d("5"))))).isEqualTo("Band limits must increase");
        assertThat(PayrollCalculator.bracketProblem(List.of(new PayrollCalculator.Bracket(null, d("5")), new PayrollCalculator.Bracket(null, d("5")))))
                .isEqualTo("Only the last band can be open-ended");
    }

    @Test
    void apply_preTaxThenTaxThenEmployerThenOneOffs() {
        List<PayrollCalculator.Line> earnings = new ArrayList<>();
        earnings.add(new PayrollCalculator.Line(PayslipLineKind.EARNING, "BASIC", "Salary", null, null, d("3000.00"), true));
        earnings.add(new PayrollCalculator.Line(PayslipLineKind.EARNING, "ADJUSTMENT", "Transport (tax-free)", null, null, d("200.00"), false));
        List<PayrollCalculator.RuleSpec> rules = List.of(
                new PayrollCalculator.RuleSpec("PENSION", "Pension", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.PERCENT, PayrollRuleBase.BASIC,
                        d("5.5"), null, null, List.of(), true),
                new PayrollCalculator.RuleSpec("TAX", "Income tax", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.PERCENT, PayrollRuleBase.TAXABLE,
                        d("10"), null, null, List.of(), false),
                new PayrollCalculator.RuleSpec("WELFARE", "Welfare", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.PERCENT, PayrollRuleBase.GROSS,
                        d("10"), null, d("50"), List.of(), false),
                new PayrollCalculator.RuleSpec("ER_PENSION", "Employer pension", PayrollRuleKind.EMPLOYER_CONTRIBUTION, PayrollRuleMethod.PERCENT,
                        PayrollRuleBase.BASIC, d("13"), null, null, List.of(), false));
        List<PayrollCalculator.Line> advance = List.of(new PayrollCalculator.Line(PayslipLineKind.DEDUCTION, "ADJUSTMENT", "Salary advance", null, null,
                d("100.00"), false));

        PayrollCalculator.Result r = PayrollCalculator.apply(earnings, d("3000.00"), rules, advance);
        assertThat(r.gross()).isEqualByComparingTo("3200.00");
        assertThat(r.taxable()).isEqualByComparingTo("2835.00");            // 3000 taxable − 165 pension
        assertThat(line(r.lines(), "PENSION")).isEqualByComparingTo("165.00");
        assertThat(line(r.lines(), "TAX")).isEqualByComparingTo("283.50");
        assertThat(line(r.lines(), "WELFARE")).isEqualByComparingTo("50.00"); // 320 capped at 50
        assertThat(r.employerContributions()).isEqualByComparingTo("390.00");
        assertThat(r.employeeDeductions()).isEqualByComparingTo("598.50");  // 165 + 283.5 + 50 + 100
        assertThat(r.net()).isEqualByComparingTo("2601.50");
    }

    @Test
    void fixedAmount_andNegativeBaseNeverProducesNegativeDeduction() {
        PayrollCalculator.RuleSpec dues = new PayrollCalculator.RuleSpec("DUES", "Union dues", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.FIXED,
                PayrollRuleBase.GROSS, null, d("25"), null, List.of(), false);
        assertThat(PayrollCalculator.amount(dues, d("1000"))).isEqualByComparingTo("25.00");
        PayrollCalculator.RuleSpec pct = new PayrollCalculator.RuleSpec("X", "X", PayrollRuleKind.EMPLOYEE_DEDUCTION, PayrollRuleMethod.PERCENT,
                PayrollRuleBase.GROSS, d("10"), null, null, List.of(), false);
        assertThat(PayrollCalculator.amount(pct, d("-50"))).isZero();
    }
}
