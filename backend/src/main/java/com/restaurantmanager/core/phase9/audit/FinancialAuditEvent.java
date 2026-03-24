package com.restaurantmanager.core.phase9.audit;

import com.restaurantmanager.core.common.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record FinancialAuditEvent(UUID actorId, AuditAction action, Instant createdAt, String metadata) {
}
