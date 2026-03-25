package com.restaurantmanager.core.menu.dto;

import com.restaurantmanager.core.menu.ModifierSelectionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MenuModifierGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ModifierSelectionType selectionType,
        boolean required,
        @Min(0) Integer minSelect,
        @Min(0) Integer maxSelect,
        @Min(0) int displayOrder,
        Boolean active
) {
}
