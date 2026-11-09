package com.restaurantmanager.core.hr.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrUserPermissionRepository extends JpaRepository<HrUserPermissionEntity, HrUserPermissionEntity.Key> {
    List<HrUserPermissionEntity> findByUserId(UUID userId);

    List<HrUserPermissionEntity> findByUserIdIn(Collection<UUID> userIds);
}
