package com.restaurantmanager.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupOrderSessionRepository extends JpaRepository<GroupOrderSessionEntity, UUID> {
    Optional<GroupOrderSessionEntity> findBySessionCodeIgnoreCase(String sessionCode);
}
