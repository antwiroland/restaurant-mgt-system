package com.restaurantmanager.core.order.dto;

import java.util.List;
import java.util.UUID;

public record GroupJoinResponse(
        UUID sessionId,
        UUID participantId,
        List<GroupParticipantResponse> participants
) {
}
