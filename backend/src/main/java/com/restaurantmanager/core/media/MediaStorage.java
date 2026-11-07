package com.restaurantmanager.core.media;

public interface MediaStorage {
    /**
     * @param baseUrl the public base URL of this API (used by local storage to build links)
     */
    StoredMedia store(byte[] content, String contentType, String extension, String baseUrl);

    void delete(String publicId);

    record StoredMedia(String url, String publicId) {}
}
