package com.restaurantmanager.core.phase9.discount;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DiscountService {
    public DiscountResult apply(BigDecimal total,
                                DiscountMode mode,
                                BigDecimal value,
                                boolean overrideTokenValid,
                                UUID actorId,
                                List<FinancialAuditEvent> auditSink) {
        if (!overrideTokenValid) {
            throw new ApiException(403, "Override token required");
        }

        BigDecimal discount = mode == DiscountMode.FLAT
                ? value
                : total.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        if (discount.compareTo(total) > 0) {
            throw new ApiException(400, "Discount exceeds order total");
        }

        BigDecimal newTotal = total.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        auditSink.add(new FinancialAuditEvent(actorId, AuditAction.DISCOUNT_APPLIED, Instant.now(), "discount=" + discount));
        return new DiscountResult(discount.setScale(2, RoundingMode.HALF_UP), newTotal);
    }
}
