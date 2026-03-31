package com.restaurantmanager.core.phase10.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecurityGuardService {
    private static final Logger log = LoggerFactory.getLogger(SecurityGuardService.class);

    private static final int MAX_FAILED_LOGINS = 10;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redis;
    private final Map<String, Integer> fallback = new ConcurrentHashMap<>();

    public SecurityGuardService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLoginRateLimited(String phone) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + phone);
            return value != null && Integer.parseInt(value) >= MAX_FAILED_LOGINS;
        } catch (Exception e) {
            log.warn("Redis unavailable in isLoginRateLimited, using in-memory fallback: {}", e.getMessage());
            return fallback.getOrDefault(phone, 0) >= MAX_FAILED_LOGINS;
        }
    }

    public void registerLoginFailure(String phone) {
        try {
            String key = KEY_PREFIX + phone;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, LOCKOUT_WINDOW);
            }
            return;
        } catch (Exception e) {
            log.warn("Redis unavailable in registerLoginFailure, using in-memory fallback: {}", e.getMessage());
        }
        fallback.merge(phone, 1, Integer::sum);
    }

    public void clearLoginFailures(String phone) {
        try {
            redis.delete(KEY_PREFIX + phone);
        } catch (Exception e) {
            log.warn("Redis unavailable in clearLoginFailures, using in-memory fallback: {}", e.getMessage());
        }
        fallback.remove(phone);
    }

    public void clearAllLoginFailures() {
        try {
            Set<String> keys = redis.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable in clearAllLoginFailures, using in-memory fallback: {}", e.getMessage());
        }
        fallback.clear();
    }

    public String sanitizeSearch(String input) {
        return input.replace("'", "").replace(";", "").replace("--", "");
    }

    public String escapeReceiptNotes(String notes) {
        return notes.replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean canCustomerAccessOrder(UUID requesterId, UUID orderOwnerId) {
        return requesterId.equals(orderOwnerId);
    }

    public boolean hasWebhookSignature(String signatureHeader) {
        return signatureHeader != null && !signatureHeader.isBlank();
    }
}
