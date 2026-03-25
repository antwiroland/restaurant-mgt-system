package com.restaurantmanager.core.user.dto;

import com.restaurantmanager.core.common.Role;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(@NotNull Role role, UUID branchId) {
}
