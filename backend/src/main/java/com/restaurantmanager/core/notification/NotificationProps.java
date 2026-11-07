package com.restaurantmanager.core.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications")
public class NotificationProps {
    /** Default country calling code used to normalise local numbers (e.g. 0244123456 -> 233244123456). */
    private String defaultCountryCode = "233";
    private final WhatsApp whatsapp = new WhatsApp();
    private final Sms sms = new Sms();

    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }

    public void setDefaultCountryCode(String defaultCountryCode) {
        this.defaultCountryCode = defaultCountryCode;
    }

    public WhatsApp getWhatsapp() {
        return whatsapp;
    }

    public Sms getSms() {
        return sms;
    }

    public static class WhatsApp {
        /** log = only log messages, meta = WhatsApp Cloud API */
        private String provider = "log";
        private String apiBaseUrl = "https://graph.facebook.com";
        private String apiVersion = "v20.0";
        private String accessToken;
        private String phoneNumberId;
        /** Optional approved template; when set, messages are sent as template with one body parameter. */
        private String templateName;
        private String templateLanguage = "en";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getTemplateLanguage() { return templateLanguage; }
        public void setTemplateLanguage(String templateLanguage) { this.templateLanguage = templateLanguage; }
    }

    public static class Sms {
        /** log = only log messages, hubtel = Hubtel SMS API, arkesel = Arkesel SMS API */
        private String provider = "log";
        private String senderId = "RestoMgr";
        private String hubtelClientId;
        private String hubtelClientSecret;
        private String hubtelBaseUrl = "https://smsc.hubtel.com/v1/messages/send";
        private String arkeselApiKey;
        private String arkeselBaseUrl = "https://sms.arkesel.com/api/v2/sms/send";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getHubtelClientId() { return hubtelClientId; }
        public void setHubtelClientId(String hubtelClientId) { this.hubtelClientId = hubtelClientId; }
        public String getHubtelClientSecret() { return hubtelClientSecret; }
        public void setHubtelClientSecret(String hubtelClientSecret) { this.hubtelClientSecret = hubtelClientSecret; }
        public String getHubtelBaseUrl() { return hubtelBaseUrl; }
        public void setHubtelBaseUrl(String hubtelBaseUrl) { this.hubtelBaseUrl = hubtelBaseUrl; }
        public String getArkeselApiKey() { return arkeselApiKey; }
        public void setArkeselApiKey(String arkeselApiKey) { this.arkeselApiKey = arkeselApiKey; }
        public String getArkeselBaseUrl() { return arkeselBaseUrl; }
        public void setArkeselBaseUrl(String arkeselBaseUrl) { this.arkeselBaseUrl = arkeselBaseUrl; }
    }
}
