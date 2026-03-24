package com.restaurantmanager.core.phase9.audit;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AuditReviewService {
    public List<FinancialAuditEvent> filterByAction(List<FinancialAuditEvent> events, AuditAction action) {
        return events.stream().filter(event -> event.action() == action).toList();
    }

    public List<FinancialAuditEvent> filterByDate(List<FinancialAuditEvent> events, Instant from, Instant to) {
        return events.stream().filter(event -> !event.createdAt().isBefore(from) && !event.createdAt().isAfter(to)).toList();
    }

    public List<FinancialAuditEvent> query(List<FinancialAuditEvent> events, Role requesterRole) {
        if (requesterRole == Role.CASHIER) {
            throw new ApiException(403, "Forbidden");
        }
        return new ArrayList<>(events);
    }
}
