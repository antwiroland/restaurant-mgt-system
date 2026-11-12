package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A deduction or employer contribution, effective from a date for one code and scope (organisation or branch). Rows are
 * never edited: a change is a new row; a row with {@code active = false} ends the rule from its date.
 */
@Entity
@Table(name = "hr_payroll_rules")
public class PayrollRuleEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 30) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private PayrollRuleKind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private PayrollRuleMethod method;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private PayrollRuleBase base;
    @Column(name = "rate_percent", precision = 7, scale = 4) private BigDecimal ratePercent;
    @Column(name = "fixed_amount", precision = 14, scale = 2) private BigDecimal fixedAmount;
    @Column(name = "cap_amount", precision = 14, scale = 2) private BigDecimal capAmount;
    @Column(columnDefinition = "TEXT") private String brackets;
    @Column(name = "pre_tax", nullable = false) private boolean preTax;
    @Column(name = "employment_types", length = 200) private String employmentTypes;
    @Column(name = "branch_id") private UUID branchId;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(length = 300) private String note;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public PayrollRuleKind getKind() { return kind; }
    public void setKind(PayrollRuleKind v) { this.kind = v; }
    public PayrollRuleMethod getMethod() { return method; }
    public void setMethod(PayrollRuleMethod v) { this.method = v; }
    public PayrollRuleBase getBase() { return base; }
    public void setBase(PayrollRuleBase v) { this.base = v; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal v) { this.ratePercent = v; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
    public void setFixedAmount(BigDecimal v) { this.fixedAmount = v; }
    public BigDecimal getCapAmount() { return capAmount; }
    public void setCapAmount(BigDecimal v) { this.capAmount = v; }
    public String getBrackets() { return brackets; }
    public void setBrackets(String v) { this.brackets = v; }
    public boolean isPreTax() { return preTax; }
    public void setPreTax(boolean v) { this.preTax = v; }
    public String getEmploymentTypes() { return employmentTypes; }
    public void setEmploymentTypes(String v) { this.employmentTypes = v; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID v) { this.branchId = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { this.effectiveFrom = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}
