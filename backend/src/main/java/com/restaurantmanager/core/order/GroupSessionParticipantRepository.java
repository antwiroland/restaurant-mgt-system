package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupSessionParticipantRepository extends JpaRepository<GroupSessionParticipantEntity, UUID> {
    List<GroupSessionParticipantEntity> findBySessionId(UUID sessionId);

    Optional<GroupSessionParticipantEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
