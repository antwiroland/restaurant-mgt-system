package com.restaurantmanager.core.hr.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEventEntity, UUID> {
    Optional<AttendanceEventEntity> findByIdempotencyKey(String idempotencyKey);
}
