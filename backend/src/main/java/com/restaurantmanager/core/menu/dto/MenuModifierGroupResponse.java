package com.restaurantmanager.core.menu.dto;

import com.restaurantmanager.core.menu.ModifierSelectionType;

import java.util.List;
import java.util.UUID;

public record MenuModifierGroupResponse(
        UUID id,
        String name,
        ModifierSelectionType selectionType,
        boolean required,
        Integer minSelect,
        Integer maxSelect,
        List<MenuModifierOptionResponse> options
) {
}
