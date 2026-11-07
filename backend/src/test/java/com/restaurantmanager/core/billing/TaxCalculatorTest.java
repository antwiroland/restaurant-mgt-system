package com.restaurantmanager.core.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxCalculatorTest {
    private static final List<TaxCalculator.Rate> GHANA = List.of(
            new TaxCalculator.Rate("NHIL", "NHIL", new BigDecimal("2.5"), false),
            new TaxCalculator.Rate("GETFUND", "GETFund", new BigDecimal("2.5"), false),
            new TaxCalculator.Rate("VAT", "VAT", new BigDecimal("15"), true));

    @Test
    void givenGhanaLevies_whenExclusive_thenVatChargedOnBasePlusLevies() {
        TaxCalculator.Result result = TaxCalculator.calculate(new BigDecimal("100.00"), GHANA, false);

        assertEquals(new BigDecimal("2.50"), result.lines().get(0).amount());
        assertEquals(new BigDecimal("2.50"), result.lines().get(1).amount());
        assertEquals(new BigDecimal("15.75"), result.lines().get(2).amount()); // 15% of 105
        assertEquals(new BigDecimal("20.75"), result.taxTotal());               // effective 20.75%
        assertEquals(new BigDecimal("100.00"), result.netBase());
    }

    @Test
    void givenGhanaLevies_whenInclusive_thenNetPlusTaxesEqualsGross() {
        TaxCalculator.Result result = TaxCalculator.calculate(new BigDecimal("120.75"), GHANA, true);

        assertEquals(new BigDecimal("100.00"), result.netBase());
        assertEquals(new BigDecimal("20.75"), result.taxTotal());
        BigDecimal lineSum = result.lines().stream().map(TaxCalculator.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(result.taxTotal(), lineSum);
    }

    @Test
    void givenAwkwardInclusiveAmount_whenSplit_thenRoundingAbsorbedSoTotalsReconcile() {
        TaxCalculator.Result result = TaxCalculator.calculate(new BigDecimal("33.33"), GHANA, true);
        BigDecimal lineSum = result.lines().stream().map(TaxCalculator.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("33.33"), result.netBase().add(lineSum));
    }

    @Test
    void givenNoRates_whenCalculated_thenNoTax() {
        TaxCalculator.Result result = TaxCalculator.calculate(new BigDecimal("50"), List.of(), false);

        assertEquals(0, result.taxTotal().signum());
        assertTrue(result.lines().isEmpty());
    }
}
