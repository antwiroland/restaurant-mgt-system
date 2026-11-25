package com.restaurantmanager.core.dining;

import com.restaurantmanager.core.table.RestaurantTableEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningOrderRepository extends JpaRepository<DiningOrderEntity, UUID> {

    Optional<DiningOrderEntity> findFirstByTable_IdAndStatusOrderByOpenedAtDesc(UUID tableId, DiningOrderEntity.Status status);

    List<DiningOrderEntity> findByTable_IdAndStatus(UUID tableId, DiningOrderEntity.Status status);

    @Query("""
            select d from DiningOrderEntity d join fetch d.table
            where d.status = :status and (:branchId is null or d.branchId is null or d.branchId = :branchId)
            order by d.openedAt asc
            """)
    List<DiningOrderEntity> findForStaff(@Param("status") DiningOrderEntity.Status status, @Param("branchId") UUID branchId);

    /**
     * Locks the table row: everything that opens a meal or changes what a table has ordered or requested runs one at a
     * time per table, so two phones (or a phone and a waiter) cannot open two meals or two requests at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTableEntity t where t.id = :tableId")
    Optional<RestaurantTableEntity> lockTable(@Param("tableId") UUID tableId);
}
