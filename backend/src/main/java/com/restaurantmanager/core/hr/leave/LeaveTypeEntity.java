package com.restaurantmanager.core.hr.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** A kind of leave (annual, sick, ...) with its counting basis and the ordered approval chain new requests follow. */
@Entity
@Table(name = "hr_leave_types")
public class LeaveTypeEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 30) private String code;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private boolean paid;
    @Column(name = "deducts_balance", nullable = false) private boolean deductsBalance;
    @Column(name = "requires_document", nullable = false) private boolean requiresDocument;
    @Column(name = "allow_negative", nullable = false) private boolean allowNegative;
    @Enumerated(EnumType.STRING)
    @Column(name = "count_basis", nullable = false, length = 20) private CountBasis countBasis;
    @Column(name = "approval_steps", nullable = false, length = 80) private String approvalSteps;
    @Column(name = "max_days_per_request", precision = 6, scale = 2) private BigDecimal maxDaysPerRequest;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "display_order", nullable = false) private int displayOrder;
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

    public List<ApprovalStep> steps() {
        return parseSteps(approvalSteps);
    }

    public static List<ApprovalStep> parseSteps(String csv) {
        List<ApprovalStep> steps = new ArrayList<>();
        if (csv != null) {
            for (String s : csv.split(",")) {
                if (!s.isBlank()) {
                    steps.add(ApprovalStep.valueOf(s.trim()));
                }
            }
        }
        return steps;
    }

    public static String formatSteps(List<ApprovalStep> steps) {
        return steps.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public boolean isDeductsBalance() { return deductsBalance; }
    public void setDeductsBalance(boolean deductsBalance) { this.deductsBalance = deductsBalance; }
    public boolean isRequiresDocument() { return requiresDocument; }
    public void setRequiresDocument(boolean requiresDocument) { this.requiresDocument = requiresDocument; }
    public boolean isAllowNegative() { return allowNegative; }
    public void setAllowNegative(boolean allowNegative) { this.allowNegative = allowNegative; }
    public CountBasis getCountBasis() { return countBasis; }
    public void setCountBasis(CountBasis countBasis) { this.countBasis = countBasis; }
    public String getApprovalSteps() { return approvalSteps; }
    public void setApprovalSteps(String approvalSteps) { this.approvalSteps = approvalSteps; }
    public BigDecimal getMaxDaysPerRequest() { return maxDaysPerRequest; }
    public void setMaxDaysPerRequest(BigDecimal maxDaysPerRequest) { this.maxDaysPerRequest = maxDaysPerRequest; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    /** Unused days are paid out in the final pay of someone who leaves (annual leave). */
    @Column(name = "pay_out_on_exit", nullable = false) private boolean payOutOnExit;

    public boolean isPayOutOnExit() { return payOutOnExit; }
    public void setPayOutOnExit(boolean payOutOnExit) { this.payOutOnExit = payOutOnExit; }

    /** Most days one person may take in a calendar year (null: no yearly limit beyond the balance). */
    @Column(name = "max_days_per_year", precision = 6, scale = 2) private BigDecimal maxDaysPerYear;

    public BigDecimal getMaxDaysPerYear() { return maxDaysPerYear; }
    public void setMaxDaysPerYear(BigDecimal maxDaysPerYear) { this.maxDaysPerYear = maxDaysPerYear; }
}
