package com.restaurantmanager.core.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupParticipantCartResponse(
        UUID participantId,
        UUID userId,
        String displayName,
        BigDecimal subtotal,
        List<GroupCartItemResponse> items
) {
}
