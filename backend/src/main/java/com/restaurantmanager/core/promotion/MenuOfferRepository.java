package com.restaurantmanager.core.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuOfferRepository extends JpaRepository<MenuOfferEntity, UUID> {
    List<MenuOfferEntity> findByMenuItem_IdInAndActiveTrue(Collection<UUID> menuItemIds);

    @Query("select o from MenuOfferEntity o join fetch o.menuItem order by o.createdAt desc")
    List<MenuOfferEntity> findAllWithMenuItem();
}
