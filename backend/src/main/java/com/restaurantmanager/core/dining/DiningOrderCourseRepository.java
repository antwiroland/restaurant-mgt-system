package com.restaurantmanager.core.dining;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningOrderCourseRepository extends JpaRepository<DiningOrderCourseEntity, UUID> {

    List<DiningOrderCourseEntity> findByDiningOrder_IdOrderBySequenceAscCreatedAtAsc(UUID diningOrderId);

    List<DiningOrderCourseEntity> findByDiningOrder_IdIn(Collection<UUID> diningOrderIds);

    Optional<DiningOrderCourseEntity> findByDiningOrder_IdAndCourseId(UUID diningOrderId, UUID courseId);

    /** Locks one course for a release, hold or serve: the second of two simultaneous releases waits, then sees it done. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DiningOrderCourseEntity c join fetch c.diningOrder d join fetch d.table where c.id = :id")
    Optional<DiningOrderCourseEntity> lockById(@Param("id") UUID id);
}
