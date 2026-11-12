package com.restaurantmanager.core.table.payment;

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

/** A table whose guests left without paying, closed out by a manager. Names are snapshots for the report. */
@Entity
@Table(name = "table_walkouts")
public class WalkoutEntity {
    public enum Reason { WALKED_OUT, REFUSED_TO_PAY, OTHER }

    @Id
    private UUID id;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "table_number", nullable = false, length = 40)
    private String tableNumber;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "branch_name", length = 160)
    private String branchName;

    @Column(name = "amount_owed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountOwed;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(length = 500)
    private String note;

    @Column(name = "order_taken_by", length = 500)
    private String orderTakenBy;

    @Column(name = "guest_phones", length = 200)
    private String guestPhones;

    @Column(name = "first_order_at")
    private Instant firstOrderAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_by_name", length = 120)
    private String recordedByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTableId() { return tableId; }
    public void setTableId(UUID tableId) { this.tableId = tableId; }
    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public BigDecimal getAmountOwed() { return amountOwed; }
    public void setAmountOwed(BigDecimal amountOwed) { this.amountOwed = amountOwed; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getOrderTakenBy() { return orderTakenBy; }
    public void setOrderTakenBy(String orderTakenBy) { this.orderTakenBy = orderTakenBy; }
    public String getGuestPhones() { return guestPhones; }
    public void setGuestPhones(String guestPhones) { this.guestPhones = guestPhones; }
    public Instant getFirstOrderAt() { return firstOrderAt; }
    public void setFirstOrderAt(Instant firstOrderAt) { this.firstOrderAt = firstOrderAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
    public String getRecordedByName() { return recordedByName; }
    public void setRecordedByName(String recordedByName) { this.recordedByName = recordedByName; }
    public Instant getCreatedAt() { return createdAt; }
}
