package com.restaurantmanager.core.user.dto;

import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        String label,
        String addressLine,
        String city,
        String landmark,
        boolean isDefault
) {
}
