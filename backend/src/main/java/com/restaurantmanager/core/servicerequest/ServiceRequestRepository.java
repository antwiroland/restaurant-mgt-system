package com.restaurantmanager.core.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequestEntity, UUID> {

    @Query("""
            select r from ServiceRequestEntity r join fetch r.table
            where r.status in :statuses
              and (:branchId is null or r.branchId is null or r.branchId = :branchId)
            order by r.createdAt asc
            """)
    List<ServiceRequestEntity> findForStaff(@Param("statuses") Collection<ServiceRequestEntity.Status> statuses,
                                            @Param("branchId") UUID branchId);

    List<ServiceRequestEntity> findByTable_IdAndCreatedAtAfterOrderByCreatedAtDesc(UUID tableId, Instant after);
}
