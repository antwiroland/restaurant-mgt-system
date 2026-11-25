package com.restaurantmanager.core.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Min(0) int displayOrder,
        boolean active,
        /** Beverages skip course holds. Null keeps the current setting (older clients do not send it). */
        Boolean beverage,
        /** Preparation station, e.g. KITCHEN or BAR. Null keeps the current one; blank clears it. */
        @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9 _-]*$", message = "Use letters, numbers, spaces, - or _") String kitchenStation
) {
    public CategoryRequest(String name, String description, int displayOrder, boolean active) {
        this(name, description, displayOrder, active, null, null);
    }
}
