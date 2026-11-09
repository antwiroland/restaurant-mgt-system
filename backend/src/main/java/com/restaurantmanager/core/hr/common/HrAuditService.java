package com.restaurantmanager.core.hr.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.core.audit.AuditService;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Writes HR events to the shared audit log. Callers must never put salary figures, PINs, identification numbers or file
 * contents in {@code metadata}.
 */
@Service
public class HrAuditService {
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public HrAuditService(AuditService auditService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID actorId, AuditAction action, String entityType, UUID entityId, Map<String, ?> metadata) {
        String json;
        try {
            json = metadata == null || metadata.isEmpty() ? null : objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            json = null;
        }
        auditService.log(actorId == null ? null : userRepository.getReferenceById(actorId), action, entityType, entityId, json, null);
    }
}
