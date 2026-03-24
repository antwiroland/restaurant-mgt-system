package com.restaurantmanager.core.user.dto;

import com.restaurantmanager.core.common.Role;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull Role role) {
}
