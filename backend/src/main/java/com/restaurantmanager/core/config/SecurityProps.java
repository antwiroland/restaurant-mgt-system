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
        private String secret = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        private long accessTtlSeconds = 900;
        private long refreshTtlSeconds = 1_209_600;
        private long overrideTtlSeconds = 300;

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
        private int maxFailedAttempts = 5;
        private int lockoutMinutes = 30;

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
