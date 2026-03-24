package com.restaurantmanager.core.audit;

import com.restaurantmanager.core.common.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findByAction(AuditAction action);
}
