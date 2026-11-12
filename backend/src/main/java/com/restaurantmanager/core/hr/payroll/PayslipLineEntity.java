package com.restaurantmanager.core.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** A frozen payslip line: an earning, an employee deduction, or an employer contribution (not taken from net pay). */
@Entity
@Table(name = "hr_payslip_lines")
public class PayslipLineEntity {
    @Id
    private UUID id;

    @Column(name = "payslip_id", nullable = false) private UUID payslipId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private PayslipLineKind kind;
    @Column(nullable = false, length = 40) private String code;
    @Column(nullable = false, length = 200) private String description;
    @Column(precision = 12, scale = 2) private BigDecimal quantity;
    @Column(precision = 14, scale = 4) private BigDecimal rate;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private boolean taxable = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public static PayslipLineEntity of(UUID payslipId, PayrollCalculator.Line line, int order) {
        PayslipLineEntity e = new PayslipLineEntity();
        e.payslipId = payslipId;
        e.kind = line.kind();
        e.code = line.code();
        e.description = line.description();
        e.quantity = line.quantity();
        e.rate = line.rate();
        e.amount = line.amount();
        e.taxable = line.taxable();
        e.sortOrder = order;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getPayslipId() { return payslipId; }
    public PayslipLineKind getKind() { return kind; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getAmount() { return amount; }
    public boolean isTaxable() { return taxable; }
    public int getSortOrder() { return sortOrder; }
}
