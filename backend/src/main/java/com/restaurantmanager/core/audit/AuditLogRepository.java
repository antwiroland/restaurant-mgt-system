package com.restaurantmanager.core.audit;

import com.restaurantmanager.core.common.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findByAction(AuditAction action);
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc();

    @Query("select a from AuditLogEntity a left join fetch a.actor order by a.createdAt desc")
    List<AuditLogEntity> findAllWithActorOrderByCreatedAtDesc();

    @Query("select a from AuditLogEntity a left join fetch a.actor where a.action in :actions and a.createdAt >= :from and a.createdAt < :to order by a.createdAt desc")
    List<AuditLogEntity> findByActionsBetween(@org.springframework.data.repository.query.Param("actions") java.util.Collection<AuditAction> actions,
                                              @org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                              @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
