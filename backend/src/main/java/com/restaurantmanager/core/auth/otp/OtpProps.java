package com.restaurantmanager.core.auth.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.otp")
public class OtpProps {
    /** When true, customer registration requires a phone verification token from /auth/otp/verify. */
    private boolean requiredForRegistration = true;
    private int codeLength = 6;
    private int ttlSeconds = 300;
    private int resendCooldownSeconds = 60;
    private int maxRequestsPerHour = 5;
    private int maxAttempts = 5;
    private int verificationTokenTtlSeconds = 900;
    private String appName = "Restaurant";

    public boolean isRequiredForRegistration() { return requiredForRegistration; }
    public void setRequiredForRegistration(boolean requiredForRegistration) { this.requiredForRegistration = requiredForRegistration; }
    public int getCodeLength() { return codeLength; }
    public void setCodeLength(int codeLength) { this.codeLength = codeLength; }
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }
    public int getMaxRequestsPerHour() { return maxRequestsPerHour; }
    public void setMaxRequestsPerHour(int maxRequestsPerHour) { this.maxRequestsPerHour = maxRequestsPerHour; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getVerificationTokenTtlSeconds() { return verificationTokenTtlSeconds; }
    public void setVerificationTokenTtlSeconds(int verificationTokenTtlSeconds) { this.verificationTokenTtlSeconds = verificationTokenTtlSeconds; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
