package com.restaurantmanager.core.hr.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LeaveApprovalRepository extends JpaRepository<LeaveApprovalEntity, UUID> {
    List<LeaveApprovalEntity> findByRequestIdOrderByStepAsc(UUID requestId);

    List<LeaveApprovalEntity> findByRequestIdIn(Collection<UUID> requestIds);
}
