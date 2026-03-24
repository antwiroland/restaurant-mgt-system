package com.restaurantmanager.core.phase9.reconciliation;

import com.restaurantmanager.core.common.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ReconciliationService {
    public ReconciliationSummary summarize(List<ReconciliationEntry> entries) {
        ReconciliationSummary summary = new ReconciliationSummary();
        summary.setTotalSales(entries.stream().map(ReconciliationEntry::sales).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setTotalRefunds(entries.stream().map(ReconciliationEntry::refunds).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setTotalVoids(entries.stream().map(ReconciliationEntry::voids).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setTotalDiscounts(entries.stream().map(ReconciliationEntry::discounts).reduce(BigDecimal.ZERO, BigDecimal::add));
        return summary;
    }

    public ReconciliationSummary signOff(ReconciliationSummary summary, boolean overrideTokenValid, UUID managerId) {
        if (!overrideTokenValid) {
            throw new ApiException(403, "Override token required");
        }
        if (summary.getSignedAt() != null) {
            throw new ApiException(409, "Day already signed off");
        }
        summary.setSignedBy(managerId);
        summary.setSignedAt(Instant.now());
        return summary;
    }
}
