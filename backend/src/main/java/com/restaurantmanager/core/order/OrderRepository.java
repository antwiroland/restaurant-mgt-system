package com.restaurantmanager.core.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByPickupCode(String pickupCode);

    List<OrderEntity> findByTableIdOrderByCreatedAtAsc(UUID tableId);

    List<OrderEntity> findByTable_QrTokenOrderByCreatedAtAsc(String tableToken);

    @Query("""
            select o from OrderEntity o
            where (:customerId is null or o.customerUserId = :customerId)
              and (:branchId is null or (o.branch is not null and o.branch.id = :branchId))
              and (:status is null or o.status = :status)
              and (:type is null or o.type = :type)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            order by o.createdAt desc
            """)
    List<OrderEntity> findVisibleOrders(@Param("customerId") UUID customerId,
                                        @Param("branchId") UUID branchId,
                                        @Param("status") OrderStatus status,
                                        @Param("type") OrderType type,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query(value = """
            select o from OrderEntity o
            where (:customerId is null or o.customerUserId = :customerId)
              and (:branchId is null or (o.branch is not null and o.branch.id = :branchId))
              and (:status is null or o.status = :status)
              and (:type is null or o.type = :type)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """,
           countQuery = """
            select count(o) from OrderEntity o
            where (:customerId is null or o.customerUserId = :customerId)
              and (:branchId is null or (o.branch is not null and o.branch.id = :branchId))
              and (:status is null or o.status = :status)
              and (:type is null or o.type = :type)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """)
    Page<OrderEntity> findVisibleOrdersPage(@Param("customerId") UUID customerId,
                                            @Param("branchId") UUID branchId,
                                            @Param("status") OrderStatus status,
                                            @Param("type") OrderType type,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            Pageable pageable);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.table WHERE o.status NOT IN :excluded ORDER BY o.createdAt ASC")
    List<OrderEntity> findActiveForPublicDisplay(@Param("excluded") List<OrderStatus> excluded);

    @Query("""
            SELECT o FROM OrderEntity o
            LEFT JOIN FETCH o.table
            LEFT JOIN FETCH o.branch
            WHERE o.type IN :types
              AND o.status IN :statuses
              AND (:branchId IS NULL OR (o.branch IS NOT NULL AND o.branch.id = :branchId))
            ORDER BY o.createdAt ASC
            """)
    List<OrderEntity> findForKdsBoard(@Param("types") List<OrderType> types,
                                      @Param("statuses") List<OrderStatus> statuses,
                                      @Param("branchId") UUID branchId);
}
