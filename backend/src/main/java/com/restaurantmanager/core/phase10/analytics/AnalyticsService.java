package com.restaurantmanager.core.phase10.analytics;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyticsService {
    public BigDecimal dailyRevenue(List<OrderAnalyticsRecord> records) {
        return records.stream()
                .filter(OrderAnalyticsRecord::paymentSuccessful)
                .map(OrderAnalyticsRecord::lineRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<TopItem> topItems(List<OrderAnalyticsRecord> records, int limit) {
        Map<String, TopItem> byName = new HashMap<>();
        for (OrderAnalyticsRecord record : records) {
            TopItem existing = byName.get(record.itemName());
            if (existing == null) {
                byName.put(record.itemName(), new TopItem(record.itemName(), record.quantity(), record.lineRevenue()));
            } else {
                byName.put(record.itemName(), new TopItem(
                        record.itemName(),
                        existing.quantitySold() + record.quantity(),
                        existing.revenue().add(record.lineRevenue())
                ));
            }
        }
        return byName.values().stream()
                .sorted(Comparator.comparingInt(TopItem::quantitySold).reversed())
                .limit(limit)
                .toList();
    }

    public int peakHour(List<OrderAnalyticsRecord> records) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (OrderAnalyticsRecord record : records) {
            counts.merge(record.orderHour(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    public int repeatCustomers(List<OrderAnalyticsRecord> records) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (OrderAnalyticsRecord record : records) {
            counts.merge(record.customerId(), 1, Integer::sum);
        }
        return (int) counts.values().stream().filter(c -> c >= 2).count();
    }
}
