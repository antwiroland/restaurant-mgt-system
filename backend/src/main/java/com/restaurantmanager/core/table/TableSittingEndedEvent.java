package com.restaurantmanager.core.table;

import java.util.UUID;

/** A party's sitting at a table is over (bill settled and food served, or staff closed, reversed or freed the table). */
public record TableSittingEndedEvent(UUID tableId) {
}
