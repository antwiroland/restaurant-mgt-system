package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.hr.contract.SalaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One employee's pay in a run. Figures are a snapshot taken at calculation; lines are in {@link PayslipLineEntity}. */
@Entity
@Table(name = "hr_payslips", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "employee_id"}))
public class PayslipEntity {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false) private UUID runId;
    @Column(name = "employee_id", nullable = false) private UUID employeeId;
    @Column(name = "contract_id") private UUID contractId;
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", length = 20) private SalaryType salaryType;
    @Column(precision = 14, scale = 2) private BigDecimal rate;
    @Column(name = "hourly_rate", precision = 14, scale = 4) private BigDecimal hourlyRate;
    @Column(name = "paid_minutes", nullable = false) private int paidMinutes;
    @Column(name = "overtime_minutes", nullable = false) private int overtimeMinutes;
    @Column(name = "worked_days", nullable = false) private int workedDays;
    @Column(name = "unpaid_leave_days", nullable = false, precision = 6, scale = 2) private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal basic = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal gross = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal taxable = BigDecimal.ZERO;
    @Column(name = "employee_deductions", nullable = false, precision = 14, scale = 2) private BigDecimal employeeDeductions = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal net = BigDecimal.ZERO;
    @Column(name = "employer_contributions", nullable = false, precision = 14, scale = 2) private BigDecimal employerContributions = BigDecimal.ZERO;
    @Column(length = 500) private String issues;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20) private PaymentMethod paymentMethod;
    @Column(name = "payment_reference", length = 120) private String paymentReference;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "paid_by") private UUID paidBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID v) { this.runId = v; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID v) { this.employeeId = v; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID v) { this.contractId = v; }
    public SalaryType getSalaryType() { return salaryType; }
    public void setSalaryType(SalaryType v) { this.salaryType = v; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal v) { this.rate = v; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal v) { this.hourlyRate = v; }
    public int getPaidMinutes() { return paidMinutes; }
    public void setPaidMinutes(int v) { this.paidMinutes = v; }
    public int getOvertimeMinutes() { return overtimeMinutes; }
    public void setOvertimeMinutes(int v) { this.overtimeMinutes = v; }
    public int getWorkedDays() { return workedDays; }
    public void setWorkedDays(int v) { this.workedDays = v; }
    public BigDecimal getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public void setUnpaidLeaveDays(BigDecimal v) { this.unpaidLeaveDays = v; }
    public BigDecimal getBasic() { return basic; }
    public void setBasic(BigDecimal v) { this.basic = v; }
    public BigDecimal getGross() { return gross; }
    public void setGross(BigDecimal v) { this.gross = v; }
    public BigDecimal getTaxable() { return taxable; }
    public void setTaxable(BigDecimal v) { this.taxable = v; }
    public BigDecimal getEmployeeDeductions() { return employeeDeductions; }
    public void setEmployeeDeductions(BigDecimal v) { this.employeeDeductions = v; }
    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal v) { this.net = v; }
    public BigDecimal getEmployerContributions() { return employerContributions; }
    public void setEmployerContributions(BigDecimal v) { this.employerContributions = v; }
    public String getIssues() { return issues; }
    public void setIssues(String v) { this.issues = v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod v) { this.paymentMethod = v; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String v) { this.paymentReference = v; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public UUID getPaidBy() { return paidBy; }
    public void setPaidBy(UUID v) { this.paidBy = v; }

    /** The employee's SSNIT number and TIN (or Ghana Card) when the payslip was calculated, for the returns. */
    @Column(name = "ssnit_number", length = 20) private String ssnitNumber;
    @Column(name = "tin", length = 20) private String tin;

    public String getSsnitNumber() { return ssnitNumber; }
    public void setSsnitNumber(String ssnitNumber) { this.ssnitNumber = ssnitNumber; }
    public String getTin() { return tin; }
    public void setTin(String tin) { this.tin = tin; }
}
