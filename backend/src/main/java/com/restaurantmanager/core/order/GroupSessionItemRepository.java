package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupSessionItemRepository extends JpaRepository<GroupSessionItemEntity, UUID> {
    List<GroupSessionItemEntity> findBySessionId(UUID sessionId);
}
