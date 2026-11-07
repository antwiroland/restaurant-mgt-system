package com.restaurantmanager.core.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media")
public class MediaProps {
    /** local (default) or cloudinary */
    private String provider = "local";
    private long maxBytes = 5 * 1024 * 1024;
    private String localDir = "uploads";
    /** Public base URL for locally stored files, e.g. https://api.example.com/media. Defaults to the request host. */
    private String localPublicBaseUrl;
    private final Cloudinary cloudinary = new Cloudinary();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public String getLocalDir() { return localDir; }
    public void setLocalDir(String localDir) { this.localDir = localDir; }
    public String getLocalPublicBaseUrl() { return localPublicBaseUrl; }
    public void setLocalPublicBaseUrl(String localPublicBaseUrl) { this.localPublicBaseUrl = localPublicBaseUrl; }
    public Cloudinary getCloudinary() { return cloudinary; }

    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
        private String folder = "restaurant-menu";
        private String apiBaseUrl = "https://api.cloudinary.com/v1_1";

        public String getCloudName() { return cloudName; }
        public void setCloudName(String cloudName) { this.cloudName = cloudName; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public String getFolder() { return folder; }
        public void setFolder(String folder) { this.folder = folder; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    }
}
