package com.restaurantmanager.core.security;

import com.restaurantmanager.core.common.Role;

import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        Role role
) {
}
