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

public interface DeviceEventRepository extends JpaRepository<DeviceEventEntity, UUID> {
    Optional<DeviceEventEntity> findByDeviceSerialNumberAndDeviceEventId(String deviceSerialNumber, String deviceEventId);

    /** The latest accepted scan of the same action by the same person shortly before, to spot a double scan. */
    @Query("""
            SELECT e FROM DeviceEventEntity e
            WHERE e.employeeId = :employeeId AND e.appliedAction = :action
              AND e.status IN (com.restaurantmanager.core.hr.device.DeviceEventEntity.Status.ACCEPTED,
                               com.restaurantmanager.core.hr.device.DeviceEventEntity.Status.REPEAT_SCAN,
                               com.restaurantmanager.core.hr.device.DeviceEventEntity.Status.RESOLVED)
              AND e.deviceTimestamp BETWEEN :from AND :to
            ORDER BY e.deviceTimestamp DESC
            """)
    List<DeviceEventEntity> recentSameAction(@Param("employeeId") UUID employeeId,
                                             @Param("action") com.restaurantmanager.core.hr.attendance.ClockAction action,
                                             @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            SELECT e FROM DeviceEventEntity e
            WHERE e.status IN :statuses AND e.deviceTimestamp >= :from AND e.deviceTimestamp < :to
            ORDER BY e.deviceTimestamp DESC
            """)
    List<DeviceEventEntity> findByStatusesBetween(@Param("statuses") Collection<DeviceEventEntity.Status> statuses,
                                                  @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    long countByStatusIn(Collection<DeviceEventEntity.Status> statuses);
}
