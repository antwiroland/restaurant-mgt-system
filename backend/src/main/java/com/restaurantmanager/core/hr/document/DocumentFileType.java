package com.restaurantmanager.core.hr.document;

import java.util.Locale;
import java.util.Optional;

/** Allowed HR upload formats, identified by file signature (magic bytes) rather than the client-supplied content type. */
public enum DocumentFileType {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final String contentType;
    private final String extension;

    DocumentFileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<DocumentFileType> detect(byte[] content, String fileName) {
        if (content == null || content.length < 4) {
            return Optional.empty();
        }
        if (content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F') {
            return Optional.of(PDF);
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return Optional.of(PNG);
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        // DOCX is a ZIP container; only accept it when the name also says .docx.
        boolean zip = content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4;
        if (zip && fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return Optional.of(DOCX);
        }
        return Optional.empty();
    }
}
