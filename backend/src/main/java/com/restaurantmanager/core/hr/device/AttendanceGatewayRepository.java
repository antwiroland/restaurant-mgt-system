package com.restaurantmanager.core.hr.device;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceGatewayRepository extends JpaRepository<AttendanceGatewayEntity, UUID> {
    Optional<AttendanceGatewayEntity> findByKeyPrefix(String keyPrefix);

    Optional<AttendanceGatewayEntity> findByGatewayIdentifierIgnoreCase(String gatewayIdentifier);

    List<AttendanceGatewayEntity> findAllByOrderByCreatedAtAsc();
}
