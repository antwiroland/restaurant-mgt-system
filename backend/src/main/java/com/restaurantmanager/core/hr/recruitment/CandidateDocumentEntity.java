package com.restaurantmanager.core.hr.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a candidate's CV or certificate. The bytes live in the private HR document store. */
@Entity
@Table(name = "hr_candidate_documents")
public class CandidateDocumentEntity {
    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "storage_key", nullable = false, length = 200) private String storageKey;
    @Column(name = "uploaded_by") private UUID uploadedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCandidateId() { return candidateId; }
    public void setCandidateId(UUID v) { this.candidateId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { this.fileName = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long v) { this.sizeBytes = v; }
    public String getSha256() { return sha256; }
    public void setSha256(String v) { this.sha256 = v; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String v) { this.storageKey = v; }
    public void setUploadedBy(UUID v) { this.uploadedBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
