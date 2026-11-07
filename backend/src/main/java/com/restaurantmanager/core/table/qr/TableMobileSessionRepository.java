package com.restaurantmanager.core.table.qr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableMobileSessionRepository extends JpaRepository<TableMobileSessionEntity, UUID> {
    List<TableMobileSessionEntity> findByTable_IdAndClosedAtIsNullOrderByLinkedAtAsc(UUID tableId);

    Optional<TableMobileSessionEntity> findFirstByTable_IdAndSessionIdAndClosedAtIsNull(UUID tableId, String sessionId);

    @Query("""
            select s.table.id as tableId, s.table.number as tableNumber, count(s) as sessionCount, max(s.lastSeenAt) as lastSeenAt
            from TableMobileSessionEntity s where s.closedAt is null
            group by s.table.id, s.table.number
            """)
    List<OpenSessionCount> countOpenByTable();

    interface OpenSessionCount {
        UUID getTableId();
        String getTableNumber();
        long getSessionCount();
        Instant getLastSeenAt();
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TableMobileSessionEntity s set s.closedAt = :now where s.table.id = :tableId and s.closedAt is null")
    int closeAllForTable(@Param("tableId") UUID tableId, @Param("now") Instant now);
}
