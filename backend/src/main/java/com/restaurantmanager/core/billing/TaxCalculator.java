package com.restaurantmanager.core.billing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure tax maths. Non-compound levies (e.g. NHIL, GETFund) are charged on the base; compound taxes (e.g. VAT) are
 * charged on the base plus all non-compound levies, applied in display order. With tax-inclusive prices the
 * base is backed out of the gross amount first.
 */
public final class TaxCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TaxCalculator() {
    }

    public record Rate(String code, String name, BigDecimal percent, boolean compound) {}

    public record Line(String code, String name, BigDecimal percent, BigDecimal amount) {}

    public record Result(BigDecimal netBase, BigDecimal taxTotal, List<Line> lines) {}

    /**
     * @param amount    taxable amount: the net base when {@code inclusive} is false, the gross amount when true
     * @param inclusive whether {@code amount} already contains the taxes
     */
    public static Result calculate(BigDecimal amount, List<Rate> rates, boolean inclusive) {
        if (rates.isEmpty() || amount.signum() <= 0) {
            return new Result(amount.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2), List.of());
        }
        BigDecimal base = inclusive ? amount.divide(grossFactor(rates), MathContext.DECIMAL64) : amount;

        BigDecimal nonCompoundSum = BigDecimal.ZERO;
        for (Rate rate : rates) {
            if (!rate.compound()) {
                nonCompoundSum = nonCompoundSum.add(base.multiply(fraction(rate)));
            }
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal compoundBase = base.add(nonCompoundSum);
        BigDecimal runningCompound = BigDecimal.ZERO;
        for (Rate rate : rates) {
            BigDecimal raw = rate.compound()
                    ? compoundBase.add(runningCompound).multiply(fraction(rate))
                    : base.multiply(fraction(rate));
            if (rate.compound()) {
                runningCompound = runningCompound.add(raw);
            }
            lines.add(new Line(rate.code(), rate.name(), rate.percent(), raw.setScale(2, RoundingMode.HALF_UP)));
        }

        BigDecimal total = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netBase = base.setScale(2, RoundingMode.HALF_UP);
        if (inclusive) {
            // Make net + taxes add up exactly to the gross amount; absorb rounding in the largest line.
            BigDecimal expected = amount.setScale(2, RoundingMode.HALF_UP).subtract(netBase);
            BigDecimal diff = expected.subtract(total);
            if (diff.signum() != 0) {
                int largest = 0;
                for (int i = 1; i < lines.size(); i++) {
                    if (lines.get(i).amount().compareTo(lines.get(largest).amount()) > 0) {
                        largest = i;
                    }
                }
                Line line = lines.get(largest);
                lines.set(largest, new Line(line.code(), line.name(), line.percent(), line.amount().add(diff)));
                total = expected;
            }
        }
        return new Result(netBase, total.setScale(2, RoundingMode.HALF_UP), List.copyOf(lines));
    }

    /** gross = base * factor */
    static BigDecimal grossFactor(List<Rate> rates) {
        BigDecimal nonCompound = BigDecimal.ONE;
        for (Rate rate : rates) {
            if (!rate.compound()) {
                nonCompound = nonCompound.add(fraction(rate));
            }
        }
        BigDecimal factor = nonCompound;
        for (Rate rate : rates) {
            if (rate.compound()) {
                factor = factor.multiply(BigDecimal.ONE.add(fraction(rate)));
            }
        }
        return factor;
    }

    private static BigDecimal fraction(Rate rate) {
        return rate.percent().divide(HUNDRED, MathContext.DECIMAL64);
    }
}
