package com.restaurantmanager.core.hr.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SwapRequestRepository extends JpaRepository<SwapRequestEntity, UUID> {
    List<SwapRequestEntity> findByStatusOrderByCreatedAtAsc(SwapStatus status);

    List<SwapRequestEntity> findAllByOrderByCreatedAtDesc();

    List<SwapRequestEntity> findByRequesterEmployeeIdOrColleagueEmployeeIdOrderByCreatedAtDesc(UUID requester, UUID colleague);

    boolean existsByScheduleIdInAndStatus(Collection<UUID> scheduleIds, SwapStatus status);
}
