package com.restaurantmanager.core.phase8.qr;

import java.util.HashMap;
import java.util.Map;

public class MobileSessionService {
    private final Map<String, String> sessionsByTable = new HashMap<>();

    public void linkSession(String tableNumber, String sessionId) {
        sessionsByTable.put(tableNumber, sessionId);
    }

    public boolean hasActiveSession(String tableNumber) {
        return sessionsByTable.containsKey(tableNumber);
    }

    public void closeSession(String tableNumber) {
        sessionsByTable.remove(tableNumber);
    }
}
