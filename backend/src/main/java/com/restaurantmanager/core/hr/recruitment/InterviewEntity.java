package com.restaurantmanager.core.hr.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An interview or trial shift for an application, with its outcome. */
@Entity
@Table(name = "hr_interviews")
public class InterviewEntity {
    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private InterviewKind kind;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes = 30;
    @Column(name = "interviewer_id") private UUID interviewerId;
    @Column(length = 200) private String location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private InterviewOutcome outcome = InterviewOutcome.SCHEDULED;
    @Column private Integer score;
    @Column(length = 2000) private String feedback;
    @Column(name = "created_by") private UUID createdBy;
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

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID v) { this.applicationId = v; }
    public InterviewKind getKind() { return kind; }
    public void setKind(InterviewKind v) { this.kind = v; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant v) { this.scheduledAt = v; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int v) { this.durationMinutes = v; }
    public UUID getInterviewerId() { return interviewerId; }
    public void setInterviewerId(UUID v) { this.interviewerId = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { this.location = v; }
    public InterviewOutcome getOutcome() { return outcome; }
    public void setOutcome(InterviewOutcome v) { this.outcome = v; }
    public Integer getScore() { return score; }
    public void setScore(Integer v) { this.score = v; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String v) { this.feedback = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
