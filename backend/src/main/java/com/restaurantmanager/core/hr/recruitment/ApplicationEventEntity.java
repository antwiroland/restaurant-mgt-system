package com.restaurantmanager.core.hr.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only stage history of an application. */
@Entity
@Table(name = "hr_application_events")
public class ApplicationEventEntity {
    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20) private ApplicationStage fromStage;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 20) private ApplicationStage toStage;
    @Column(length = 500) private String note;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public static ApplicationEventEntity of(UUID applicationId, ApplicationStage from, ApplicationStage to, String note, UUID actorId) {
        ApplicationEventEntity e = new ApplicationEventEntity();
        e.applicationId = applicationId;
        e.fromStage = from;
        e.toStage = to;
        e.note = note;
        e.actorId = actorId;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public ApplicationStage getFromStage() { return fromStage; }
    public ApplicationStage getToStage() { return toStage; }
    public String getNote() { return note; }
    public UUID getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
