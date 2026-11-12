package com.restaurantmanager.core.hr.tips;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TipCalculatorTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static TipCalculator.Participant p(UUID id, int minutes, String points) {
        return new TipCalculator.Participant(id, minutes, d(points));
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void houseShare_isTakenFirst() {
        TipCalculator.Split split = TipCalculator.split(d("100.00"), d("10"));
        assertThat(split.house()).isEqualByComparingTo("10.00");
        assertThat(split.distributable()).isEqualByComparingTo("90.00");
        assertThat(TipCalculator.split(d("33.33"), d("12.5")).house()).isEqualByComparingTo("4.17");
    }

    @Test
    void hours_splitByTime_andCentsAlwaysAddUp() {
        Map<UUID, BigDecimal> shares = TipCalculator.allocate(d("100.00"), List.of(p(A, 300, "1"), p(B, 300, "1"), p(C, 300, "1")), TipMethod.HOURS, 0);
        assertThat(shares.values()).containsExactly(d("33.34"), d("33.33"), d("33.33"));
        assertThat(sum(shares)).isEqualByComparingTo("100.00");

        Map<UUID, BigDecimal> uneven = TipCalculator.allocate(d("10.00"), List.of(p(A, 480, "1"), p(B, 240, "1"), p(C, 7, "1")), TipMethod.HOURS, 0);
        assertThat(sum(uneven)).isEqualByComparingTo("10.00");
        assertThat(uneven.get(A)).isGreaterThan(uneven.get(B));
    }

    @Test
    void points_weightTimeByPosition_andZeroPointsExcluded() {
        Map<UUID, BigDecimal> shares = TipCalculator.allocate(d("90.00"), List.of(p(A, 480, "2"), p(B, 480, "1"), p(C, 480, "0")), TipMethod.POINTS, 0);
        assertThat(shares.get(A)).isEqualByComparingTo("60.00");
        assertThat(shares.get(B)).isEqualByComparingTo("30.00");
        assertThat(shares.get(C)).isZero();
    }

    @Test
    void minimumTime_andEqualSplit() {
        Map<UUID, BigDecimal> shares = TipCalculator.allocate(d("50.00"), List.of(p(A, 600, "1"), p(B, 30, "1")), TipMethod.HOURS, 60);
        assertThat(shares.get(A)).isEqualByComparingTo("50.00");
        assertThat(shares.get(B)).isZero();
        Map<UUID, BigDecimal> equal = TipCalculator.allocate(d("50.00"), List.of(p(A, 600, "1"), p(B, 90, "1")), TipMethod.EQUAL, 60);
        assertThat(equal.get(A)).isEqualByComparingTo("25.00");
        assertThat(equal.get(B)).isEqualByComparingTo("25.00");
    }

    @Test
    void nobodyEligible_orNothingCollected_givesZeroShares() {
        assertThat(sum(TipCalculator.allocate(d("40.00"), List.of(p(A, 10, "1")), TipMethod.HOURS, 60))).isZero();
        assertThat(sum(TipCalculator.allocate(d("0.00"), List.of(p(A, 600, "1")), TipMethod.HOURS, 0))).isZero();
        assertThat(TipCalculator.allocate(d("40.00"), List.of(), TipMethod.HOURS, 0)).isEmpty();
    }
}
