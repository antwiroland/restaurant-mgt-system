package com.restaurantmanager.core.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuOfferRepository extends JpaRepository<MenuOfferEntity, UUID> {
    List<MenuOfferEntity> findByMenuItem_IdInAndActiveTrue(Collection<UUID> menuItemIds);

    @Query("select o from MenuOfferEntity o left join fetch o.menuItem left join fetch o.freeMenuItem left join fetch o.branch order by o.createdAt desc")
    List<MenuOfferEntity> findAllWithMenuItem();

    /** Active offers with what pricing needs; callers filter by schedule and branch. */
    @Query("select o from MenuOfferEntity o left join fetch o.menuItem left join fetch o.freeMenuItem left join fetch o.branch where o.active = true")
    List<MenuOfferEntity> findActiveForPricing();
}
