package com.restaurantmanager.core.hr.tips;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure tip and service-charge arithmetic. Shares are computed in cents with the largest-remainder method, so they always add
 * up exactly to the amount being shared (no cent is lost or invented by rounding).
 */
public final class TipCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TipCalculator() {
    }

    /** Someone who worked at the branch in the period: paid minutes there and their position's points. */
    public record Participant(UUID employeeId, int minutes, BigDecimal points) {}

    public record Split(BigDecimal house, BigDecimal distributable) {}

    /** What the house keeps (rounded half up) and what is left to share. */
    public static Split split(BigDecimal collected, BigDecimal housePercent) {
        BigDecimal house = collected.multiply(housePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Split(house, collected.setScale(2, RoundingMode.HALF_UP).subtract(house));
    }

    /**
     * Shares of {@code amount} by method: HOURS by minutes, POINTS by minutes × points, EQUAL per person. Participants
     * below {@code minMinutes} or with zero weight get nothing. Returns every participant (zero shares included), in input order.
     */
    public static Map<UUID, BigDecimal> allocate(BigDecimal amount, List<Participant> participants, TipMethod method, int minMinutes) {
        Map<UUID, BigDecimal> weights = new LinkedHashMap<>();
        for (Participant p : participants) {
            boolean eligible = p.minutes() > 0 && p.minutes() >= minMinutes && p.points().signum() > 0;
            BigDecimal w = !eligible ? BigDecimal.ZERO : switch (method) {
                case HOURS -> BigDecimal.valueOf(p.minutes());
                case POINTS -> BigDecimal.valueOf(p.minutes()).multiply(p.points());
                case EQUAL -> BigDecimal.ONE;
            };
            weights.put(p.employeeId(), w);
        }
        return largestRemainder(amount, weights);
    }

    static Map<UUID, BigDecimal> largestRemainder(BigDecimal amount, Map<UUID, BigDecimal> weights) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        weights.keySet().forEach(k -> result.put(k, BigDecimal.ZERO.setScale(2)));
        BigDecimal total = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long cents = amount.movePointRight(2).setScale(0, RoundingMode.DOWN).longValueExact();
        if (total.signum() == 0 || cents <= 0) {
            return result;
        }
        record Part(UUID id, long floor, BigDecimal remainder) {}
        List<Part> parts = new ArrayList<>();
        long assigned = 0;
        for (Map.Entry<UUID, BigDecimal> e : weights.entrySet()) {
            if (e.getValue().signum() == 0) {
                continue;
            }
            BigDecimal exact = BigDecimal.valueOf(cents).multiply(e.getValue()).divide(total, 10, RoundingMode.DOWN);
            long floor = exact.setScale(0, RoundingMode.DOWN).longValue();
            parts.add(new Part(e.getKey(), floor, exact.subtract(BigDecimal.valueOf(floor))));
            assigned += floor;
        }
        long left = cents - assigned;
        // biggest remainders first; ties broken by input order, which is stable and explainable
        List<Part> byRemainder = new ArrayList<>(parts);
        byRemainder.sort(Comparator.comparing(Part::remainder).reversed());
        Map<UUID, Long> extra = new LinkedHashMap<>();
        for (int i = 0; i < left && i < byRemainder.size(); i++) {
            extra.merge(byRemainder.get(i).id(), 1L, Long::sum);
        }
        for (Part p : parts) {
            long c = p.floor() + extra.getOrDefault(p.id(), 0L);
            result.put(p.id(), BigDecimal.valueOf(c).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY));
        }
        return result;
    }
}
