package com.restaurantmanager.core.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An order a guest places from the table QR page. No account is needed; the guest may leave a name and a phone
 * number so staff can reach them (the phone is also offered as the Mobile Money number when they pay).
 */
public record PublicTableOrderCreateRequest(
        @NotBlank String tableToken,
        @NotEmpty List<@Valid OrderCreateItemRequest> items,
        @Size(max = 1000) String notes,
        String promoCode,
        @Size(max = 80) String guestName,
        @Pattern(regexp = "^\\+?[0-9 ()-]{9,20}$", message = "Enter a valid phone number") String guestPhone
) {
    public PublicTableOrderCreateRequest(String tableToken, List<OrderCreateItemRequest> items, String notes) {
        this(tableToken, items, notes, null, null, null);
    }

    public PublicTableOrderCreateRequest(String tableToken, List<OrderCreateItemRequest> items, String notes, String promoCode) {
        this(tableToken, items, notes, promoCode, null, null);
    }
}
