package com.restaurantmanager.core.hr.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** A person applying for work. Shared by all their applications; personal data can be removed (anonymised). */
@Entity
@Table(name = "hr_candidates")
public class CandidateEntity {
    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 80) private String firstName;
    @Column(name = "last_name", length = 80) private String lastName;
    @Column(length = 20) private String phone;
    @Column(length = 255) private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private CandidateSource source;
    @Column(name = "referred_by_employee_id") private UUID referredByEmployeeId;
    @Column(length = 2000) private String notes;
    @Column(name = "anonymised_at") private Instant anonymisedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String fullName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public CandidateSource getSource() { return source; }
    public void setSource(CandidateSource v) { this.source = v; }
    public UUID getReferredByEmployeeId() { return referredByEmployeeId; }
    public void setReferredByEmployeeId(UUID v) { this.referredByEmployeeId = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public Instant getAnonymisedAt() { return anonymisedAt; }
    public void setAnonymisedAt(Instant v) { this.anonymisedAt = v; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
