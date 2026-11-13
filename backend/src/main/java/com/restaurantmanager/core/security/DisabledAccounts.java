package com.restaurantmanager.core.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accounts switched off in the last hour. Sign-in and token refresh already refuse disabled accounts; this closes the
 * gap of an access token issued before the switch-off, which would otherwise keep working until it expires.
 */
@Component
public class DisabledAccounts {
    private static final Duration KEEP = Duration.ofHours(1);
    private final Map<UUID, Instant> disabled = new ConcurrentHashMap<>();

    public void disabled(UUID userId) {
        disabled.put(userId, Instant.now());
    }

    public void enabled(UUID userId) {
        disabled.remove(userId);
    }

    public boolean isDisabled(UUID userId) {
        Instant at = disabled.get(userId);
        if (at == null) {
            return false;
        }
        if (at.plus(KEEP).isBefore(Instant.now())) {
            disabled.remove(userId);
            return false;
        }
        return true;
    }
}
