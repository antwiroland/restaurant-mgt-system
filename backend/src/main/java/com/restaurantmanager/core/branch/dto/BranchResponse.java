package com.restaurantmanager.core.branch.dto;

import java.util.UUID;

public record BranchResponse(
        UUID id,
        String code,
        String name,
        boolean active
) {
}
