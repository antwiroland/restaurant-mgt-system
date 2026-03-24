package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.GroupSessionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupViewResponse(
        UUID sessionId,
        String sessionCode,
        GroupSessionStatus status,
        BigDecimal groupTotal,
        List<GroupParticipantCartResponse> participants
) {
}
