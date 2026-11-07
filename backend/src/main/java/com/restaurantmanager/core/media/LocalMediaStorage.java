package com.restaurantmanager.core.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Stores files on local disk and serves them from /media/** (development or single-server deployments). */
public class LocalMediaStorage implements MediaStorage {
    private final Path root;
    private final String configuredBaseUrl;

    public LocalMediaStorage(MediaProps props) {
        this.root = Path.of(props.getLocalDir()).toAbsolutePath().normalize();
        this.configuredBaseUrl = props.getLocalPublicBaseUrl();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create media directory " + root, ex);
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public StoredMedia store(byte[] content, String contentType, String extension, String baseUrl) {
        String key = UUID.randomUUID() + "." + extension;
        try {
            Files.write(root.resolve(key), content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store image", ex);
        }
        String base = configuredBaseUrl != null && !configuredBaseUrl.isBlank()
                ? configuredBaseUrl.replaceAll("/+$", "")
                : baseUrl.replaceAll("/+$", "") + "/media";
        return new StoredMedia(base + "/" + key, "local:" + key);
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || !publicId.startsWith("local:")) {
            return;
        }
        Path file = root.resolve(publicId.substring("local:".length())).normalize();
        if (!file.startsWith(root)) {
            return; // never delete outside the media directory
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
