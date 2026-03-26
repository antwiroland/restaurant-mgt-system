package com.restaurantmanager.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProps {
    private boolean enabled = true;
    private int authRequests = 20;
    private int authWindowSeconds = 60;
    private int pinRequests = 5;
    private int pinWindowSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAuthRequests() {
        return authRequests;
    }

    public void setAuthRequests(int authRequests) {
        this.authRequests = authRequests;
    }

    public int getAuthWindowSeconds() {
        return authWindowSeconds;
    }

    public void setAuthWindowSeconds(int authWindowSeconds) {
        this.authWindowSeconds = authWindowSeconds;
    }

    public int getPinRequests() {
        return pinRequests;
    }

    public void setPinRequests(int pinRequests) {
        this.pinRequests = pinRequests;
    }

    public int getPinWindowSeconds() {
        return pinWindowSeconds;
    }

    public void setPinWindowSeconds(int pinWindowSeconds) {
        this.pinWindowSeconds = pinWindowSeconds;
    }
}
