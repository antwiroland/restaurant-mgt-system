package com.restaurantmanager.core.user.dto;

import com.restaurantmanager.core.common.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String phone,
        String email,
        Role role,
        boolean active,
        UUID branchId,
        String branchName
) {
}
