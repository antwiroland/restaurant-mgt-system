package com.restaurantmanager.core.hr.tips;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/** One employee's share of a pool. Paid through payroll at the employee's home branch. */
@Entity
@Table(name = "hr_tip_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"pool_id", "employee_id"}))
public class TipAllocationEntity {
    @Id
    private UUID id;

    @Column(name = "pool_id", nullable = false) private UUID poolId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(nullable = false) private int minutes;
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal points = BigDecimal.ONE;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "payroll_run_id") private UUID payrollRunId;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public UUID getPoolId() { return poolId; }
    public void setPoolId(UUID v) { this.poolId = v; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public int getMinutes() { return minutes; }
    public void setMinutes(int v) { this.minutes = v; }
    public BigDecimal getPoints() { return points; }
    public void setPoints(BigDecimal v) { this.points = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public UUID getPayrollRunId() { return payrollRunId; }
    public void setPayrollRunId(UUID v) { this.payrollRunId = v; }
}
