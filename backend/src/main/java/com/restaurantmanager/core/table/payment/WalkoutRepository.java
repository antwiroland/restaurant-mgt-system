package com.restaurantmanager.core.table.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalkoutRepository extends JpaRepository<WalkoutEntity, UUID> {
    List<WalkoutEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(Instant from, Instant to);
}
