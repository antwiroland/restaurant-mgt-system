package com.restaurantmanager.core.dining;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseReleaseRequestRepository extends JpaRepository<CourseReleaseRequestEntity, UUID> {

    List<CourseReleaseRequestEntity> findByDiningOrderIdAndStatus(UUID diningOrderId, CourseReleaseRequestEntity.Status status);

    List<CourseReleaseRequestEntity> findByDiningOrderCourseIdAndStatus(UUID diningOrderCourseId, CourseReleaseRequestEntity.Status status);

    List<CourseReleaseRequestEntity> findByDiningOrderIdOrderByRequestedAtDesc(UUID diningOrderId);

    List<CourseReleaseRequestEntity> findByTableIdAndStatus(UUID tableId, CourseReleaseRequestEntity.Status status);

    /** Requests staff of a branch see (unscoped staff pass null and see all). Requests with no branch are shared. */
    @Query("""
            select r from CourseReleaseRequestEntity r
            where r.status in :statuses
              and r.requestedAt >= :since
              and (:branchId is null or r.branchId is null or r.branchId = :branchId)
            order by r.requestedAt asc
            """)
    List<CourseReleaseRequestEntity> findForStaff(@Param("statuses") Collection<CourseReleaseRequestEntity.Status> statuses,
                                                  @Param("branchId") UUID branchId,
                                                  @Param("since") Instant since);
}
