package com.restaurantmanager.core.table.dto;

import com.restaurantmanager.core.table.TableStatus;

import java.util.UUID;

/**
 * Public table listing. {@code id} lets guests pick a table for a reservation
 * (POST /reservations is public and takes a tableId); it is not a secret — the
 * QR token remains the credential for table ordering.
 */
public record TablePublicStatusView(
        UUID id,
        String number,
        int capacity,
        String zone,
        TableStatus status
) {
}
