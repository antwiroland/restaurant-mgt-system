package com.restaurantmanager.core.hr.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrUserBranchAccessRepository extends JpaRepository<HrUserBranchAccessEntity, HrUserBranchAccessEntity.Key> {
    List<HrUserBranchAccessEntity> findByUserId(UUID userId);

    List<HrUserBranchAccessEntity> findByUserIdIn(Collection<UUID> userIds);
}
