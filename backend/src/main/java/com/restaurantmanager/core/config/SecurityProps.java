package com.restaurantmanager.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public class SecurityProps {
    private final Jwt jwt = new Jwt();
    private final Pin pin = new Pin();

    public Jwt getJwt() {
        return jwt;
    }

    public Pin getPin() {
        return pin;
    }

    public static class Jwt {
        private String secret;
        private long accessTtlSeconds;
        private long refreshTtlSeconds;
        private long overrideTtlSeconds;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getAccessTtlSeconds() {
            return accessTtlSeconds;
        }

        public void setAccessTtlSeconds(long accessTtlSeconds) {
            this.accessTtlSeconds = accessTtlSeconds;
        }

        public long getRefreshTtlSeconds() {
            return refreshTtlSeconds;
        }

        public void setRefreshTtlSeconds(long refreshTtlSeconds) {
            this.refreshTtlSeconds = refreshTtlSeconds;
        }

        public long getOverrideTtlSeconds() {
            return overrideTtlSeconds;
        }

        public void setOverrideTtlSeconds(long overrideTtlSeconds) {
            this.overrideTtlSeconds = overrideTtlSeconds;
        }
    }

    public static class Pin {
        private int maxFailedAttempts;
        private int lockoutMinutes;

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(int lockoutMinutes) {
            this.lockoutMinutes = lockoutMinutes;
        }
    }
}
