package com.restaurantmanager.core.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuModifierGroupRepository extends JpaRepository<MenuModifierGroupEntity, UUID> {
    List<MenuModifierGroupEntity> findByMenuItem_IdAndActiveTrueOrderByDisplayOrderAsc(UUID menuItemId);
}
