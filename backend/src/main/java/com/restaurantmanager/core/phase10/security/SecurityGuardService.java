package com.restaurantmanager.core.phase10.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class SecurityGuardService {
    private static final int MAX_FAILED_LOGINS = 10;
    private final Map<String, Integer> failedLoginByPhone = new ConcurrentHashMap<>();

    public String sanitizeSearch(String input) {
        return input.replace("'", "").replace(";", "").replace("--", "");
    }

    public String escapeReceiptNotes(String notes) {
        return notes.replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean canCustomerAccessOrder(UUID requesterId, UUID orderOwnerId) {
        return requesterId.equals(orderOwnerId);
    }

    public boolean isLoginRateLimited(String phone) {
        return failedLoginByPhone.getOrDefault(phone, 0) >= MAX_FAILED_LOGINS;
    }

    public void registerLoginFailure(String phone) {
        failedLoginByPhone.merge(phone, 1, Integer::sum);
    }

    public void clearLoginFailures(String phone) {
        failedLoginByPhone.remove(phone);
    }

    public void clearAllLoginFailures() {
        failedLoginByPhone.clear();
    }

    public boolean hasWebhookSignature(String signatureHeader) {
        return signatureHeader != null && !signatureHeader.isBlank();
    }
}
