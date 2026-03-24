package com.restaurantmanager.core.phase9.voiding;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class VoidService {
    public VoidableOrder voidOrder(VoidableOrder order,
                                   boolean overrideTokenValid,
                                   UUID actorId,
                                   List<FinancialAuditEvent> auditSink) {
        if (!overrideTokenValid) {
            throw new ApiException(403, "Override token required");
        }
        if (order.getStatus() == VoidableOrderStatus.COMPLETED) {
            throw new ApiException(400, "Completed orders cannot be voided");
        }

        order.setStatus(VoidableOrderStatus.VOIDED);
        if (order.getChannel() == OrderChannel.DINE_IN) {
            order.setTableStatus(TableLinkStatus.AVAILABLE);
        }
        auditSink.add(new FinancialAuditEvent(actorId, AuditAction.ORDER_VOIDED, Instant.now(), "order=" + order.getId()));
        return order;
    }
}
