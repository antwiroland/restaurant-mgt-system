package com.restaurantmanager.core.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 160) String name,
        boolean active
) {
}
