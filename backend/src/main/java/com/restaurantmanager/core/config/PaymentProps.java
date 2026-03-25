package com.restaurantmanager.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public class PaymentProps {
    private final Paystack paystack = new Paystack();
    private final Reconciliation reconciliation = new Reconciliation();
    private String defaultCurrency = "GHS";

    public Paystack getPaystack() {
        return paystack;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public static class Paystack {
        private String secretKey;
        private String baseUrl = "https://api.paystack.co";

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Reconciliation {
        private boolean enabled = true;
        private int pendingOlderThanMinutes = 15;
        private int batchSize = 200;
        private long fixedDelayMs = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPendingOlderThanMinutes() {
            return pendingOlderThanMinutes;
        }

        public void setPendingOlderThanMinutes(int pendingOlderThanMinutes) {
            this.pendingOlderThanMinutes = pendingOlderThanMinutes;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }
}
