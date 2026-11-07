package com.restaurantmanager.core.payment.split;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits a total into shares proportional to weights, in pesewas, using the largest-remainder method so the
 * shares always add up exactly to the total.
 */
public final class SplitAllocator {
    private SplitAllocator() {
    }

    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("At least one share is required");
        }
        long totalMinor = total.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> normalized = weightSum.signum() == 0
                ? weights.stream().map(w -> BigDecimal.ONE).toList()
                : weights;
        BigDecimal sum = weightSum.signum() == 0 ? BigDecimal.valueOf(weights.size()) : weightSum;

        long[] shares = new long[normalized.size()];
        BigDecimal[] remainders = new BigDecimal[normalized.size()];
        long allocated = 0;
        for (int i = 0; i < normalized.size(); i++) {
            BigDecimal exact = BigDecimal.valueOf(totalMinor).multiply(normalized.get(i)).divide(sum, 6, RoundingMode.DOWN);
            shares[i] = exact.setScale(0, RoundingMode.DOWN).longValueExact();
            remainders[i] = exact.subtract(BigDecimal.valueOf(shares[i]));
            allocated += shares[i];
        }
        long leftover = totalMinor - allocated;
        List<Integer> order = new ArrayList<>(IntStream.range(0, shares.length).boxed().toList());
        order.sort(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed().thenComparing(i -> i));
        for (int k = 0; k < leftover; k++) {
            shares[order.get(k % order.size())]++;
        }
        List<BigDecimal> result = new ArrayList<>();
        for (long share : shares) {
            result.add(BigDecimal.valueOf(share).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY));
        }
        return result;
    }
}
