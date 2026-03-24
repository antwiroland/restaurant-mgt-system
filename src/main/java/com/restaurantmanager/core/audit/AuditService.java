package com.restaurantmanager.core.audit;

import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.user.UserEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UserEntity actor, AuditAction action, String entityType, UUID entityId, String metadata, String ipAddress) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActor(actor);
        entity.setAction(action);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setMetadata(metadata);
        entity.setIpAddress(ipAddress);
        auditLogRepository.save(entity);
    }
}
