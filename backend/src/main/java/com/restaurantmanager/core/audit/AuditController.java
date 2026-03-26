package com.restaurantmanager.core.audit;

import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Pagination;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {
    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<List<AuditLogResponse>> list(@RequestParam(required = false) AuditAction action,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        List<AuditLogResponse> all = auditLogRepository.findAllWithActorOrderByCreatedAtDesc().stream()
                .filter(log -> action == null || log.getAction() == action)
                .filter(log -> from == null || !log.getCreatedAt().isBefore(from))
                .filter(log -> to == null || !log.getCreatedAt().isAfter(to))
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getActor() == null ? null : log.getActor().getId(),
                        log.getActor() == null ? null : log.getActor().getName(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getMetadata(),
                        log.getIpAddress(),
                        log.getCreatedAt()
                ))
                .toList();
        Pagination.Params params = Pagination.from(page, size);
        List<AuditLogResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }

    public record AuditLogResponse(
            UUID id,
            UUID actorId,
            String actorName,
            AuditAction action,
            String entityType,
            UUID entityId,
            String metadata,
            String ipAddress,
            Instant createdAt
    ) {
    }
}
