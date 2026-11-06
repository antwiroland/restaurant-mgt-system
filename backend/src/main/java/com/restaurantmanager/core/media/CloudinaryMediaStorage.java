package com.restaurantmanager.core.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Cloudinary signed upload / destroy via the REST API (media.provider=cloudinary). */
public class CloudinaryMediaStorage implements MediaStorage {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaStorage.class);

    private final MediaProps.Cloudinary props;
    private final RestClient restClient;

    public CloudinaryMediaStorage(MediaProps props) {
        this(props, RestClient.create());
    }

    CloudinaryMediaStorage(MediaProps props, RestClient restClient) {
        this.props = props.getCloudinary();
        this.restClient = restClient;
        if (isBlank(this.props.getCloudName()) || isBlank(this.props.getApiKey()) || isBlank(this.props.getApiSecret())) {
            throw new IllegalStateException("media.provider=cloudinary requires cloud-name, api-key and api-secret");
        }
    }

    @Override
    public StoredMedia store(byte[] content, String contentType, String extension, String baseUrl) {
        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> signed = new TreeMap<>();
        signed.put("folder", props.getFolder());
        signed.put("timestamp", String.valueOf(timestamp));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "upload." + extension;
            }
        });
        signed.forEach(form::add);
        form.add("api_key", props.getApiKey());
        form.add("signature", sign(signed));
        try {
            JsonNode body = restClient.post()
                    .uri(props.getApiBaseUrl() + "/" + props.getCloudName() + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("secure_url").isMissingNode()) {
                throw new ApiException(502, "Image upload failed");
            }
            return new StoredMedia(body.path("secure_url").asText(), body.path("public_id").asText());
        } catch (RestClientResponseException ex) {
            log.warn("Cloudinary upload failed: {} {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new ApiException(502, "Image upload failed");
        }
    }

    @Override
    public void delete(String publicId) {
        if (isBlank(publicId) || publicId.startsWith("local:")) {
            return;
        }
        long timestamp = System.currentTimeMillis() / 1000;
        Map<String, String> signed = new TreeMap<>();
        signed.put("public_id", publicId);
        signed.put("timestamp", String.valueOf(timestamp));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        signed.forEach(form::add);
        form.add("api_key", props.getApiKey());
        form.add("signature", sign(signed));
        try {
            restClient.post()
                    .uri(props.getApiBaseUrl() + "/" + props.getCloudName() + "/image/destroy")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Cloudinary delete of {} failed: {}", publicId, ex.getMessage());
        }
    }

    /** Cloudinary signature: SHA-1 of the alphabetically sorted params joined with '&', followed by the API secret. */
    String sign(Map<String, String> params) {
        String toSign = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + props.getApiSecret();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
