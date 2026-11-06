package com.restaurantmanager.core.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderTaxLineRepository extends JpaRepository<OrderTaxLineEntity, UUID> {
    List<OrderTaxLineEntity> findByOrderIdOrderByDisplayOrderAsc(UUID orderId);

    List<OrderTaxLineEntity> findByOrderIdIn(Collection<UUID> orderIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from OrderTaxLineEntity l where l.orderId = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);
}
