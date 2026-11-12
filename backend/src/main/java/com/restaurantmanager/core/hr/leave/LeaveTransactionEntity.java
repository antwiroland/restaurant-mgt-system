package com.restaurantmanager.core.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only ledger line behind a {@link LeaveBalanceEntity}. */
@Entity
@Table(name = "hr_leave_transactions")
public class LeaveTransactionEntity {
    @Id
    private UUID id;

    @Column(name = "balance_id", nullable = false) private UUID balanceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private LeaveTxKind kind;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal days;
    @Column(name = "request_id") private UUID requestId;
    @Column(length = 300) private String note;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public static LeaveTransactionEntity of(UUID balanceId, LeaveTxKind kind, BigDecimal days, UUID requestId, String note, UUID actorId) {
        LeaveTransactionEntity tx = new LeaveTransactionEntity();
        tx.balanceId = balanceId;
        tx.kind = kind;
        tx.days = days;
        tx.requestId = requestId;
        tx.note = note;
        tx.actorId = actorId;
        return tx;
    }

    public UUID getId() { return id; }
    public UUID getBalanceId() { return balanceId; }
    public LeaveTxKind getKind() { return kind; }
    public BigDecimal getDays() { return days; }
    public UUID getRequestId() { return requestId; }
    public String getNote() { return note; }
    public UUID getActorId() { return actorId; }
    public Instant getCreatedAt() { return createdAt; }
}
