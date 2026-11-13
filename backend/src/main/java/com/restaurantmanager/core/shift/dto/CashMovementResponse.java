package com.restaurantmanager.core.shift.dto;

import com.restaurantmanager.core.shift.CashMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(UUID id, CashMovementType type, BigDecimal amount, String reason,
                                   UUID createdBy, String createdByName, Instant createdAt) {
}
