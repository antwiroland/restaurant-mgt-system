package com.restaurantmanager.core.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuItemStockoutRepository extends JpaRepository<MenuItemStockoutEntity, UUID> {
    List<MenuItemStockoutEntity> findByMenuItemIdInAndBranchId(Collection<UUID> menuItemIds, UUID branchId);

    List<MenuItemStockoutEntity> findByMenuItemId(UUID menuItemId);
}
