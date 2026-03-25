package com.restaurantmanager.core.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuModifierOptionRepository extends JpaRepository<MenuModifierOptionEntity, UUID> {
    List<MenuModifierOptionEntity> findByGroup_MenuItem_IdAndActiveTrueOrderByGroup_DisplayOrderAscDisplayOrderAsc(UUID menuItemId);

    List<MenuModifierOptionEntity> findByGroup_IdAndActiveTrueOrderByDisplayOrderAsc(UUID groupId);
}
