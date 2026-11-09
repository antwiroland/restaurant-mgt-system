package com.restaurantmanager.core.hr.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Private on-disk store for confidential HR files. Unlike {@code MediaStorage} nothing here is reachable by URL: files are
 * only streamed through the authorised download endpoint. The directory must be a persistent volume that no web server
 * serves directly (shared storage when running several instances).
 */
@Component
public class HrDocumentStorage {
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    public HrDocumentStorage(@Value("${hr.documents.dir:hr-documents}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create HR document directory " + root, ex);
        }
    }

    /** Stores bytes under a new random key and returns the key. */
    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.write(resolve(key), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store HR document", ex);
        }
        return key;
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read HR document", ex);
        }
    }

    /** Used only to undo a store when the surrounding transaction fails. */
    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | IllegalArgumentException ignored) {
            // best effort clean-up
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid document key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid document key");
        }
        return path;
    }
}
