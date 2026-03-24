package com.restaurantmanager.core.order.dto;

import java.util.UUID;

public record GroupParticipantResponse(
        UUID participantId,
        UUID userId,
        String displayName
) {
}
