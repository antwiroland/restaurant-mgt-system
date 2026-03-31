package com.restaurantmanager.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerDeliveryAddressRepository extends JpaRepository<CustomerDeliveryAddressEntity, UUID> {
    List<CustomerDeliveryAddressEntity> findByUser_IdOrderByDefaultAddressDescCreatedAtAsc(UUID userId);

    @Modifying
    @Query("update CustomerDeliveryAddressEntity a set a.defaultAddress = false where a.user.id = :userId")
    void clearDefaultForUser(@Param("userId") UUID userId);
}
