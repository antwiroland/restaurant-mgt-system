package com.restaurantmanager.core.order.dto;

import com.restaurantmanager.core.order.GroupSessionStatus;

import java.util.UUID;

public record GroupSessionResponse(
        UUID sessionId,
        String sessionCode,
        GroupSessionStatus status,
        UUID hostUserId,
        UUID hostParticipantId
) {
}
