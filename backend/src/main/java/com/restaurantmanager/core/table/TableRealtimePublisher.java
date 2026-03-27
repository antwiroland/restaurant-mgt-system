package com.restaurantmanager.core.table;

public interface TableRealtimePublisher {
    void publishTableStatusChanged(String tableNumber, TableStatus status);
}
