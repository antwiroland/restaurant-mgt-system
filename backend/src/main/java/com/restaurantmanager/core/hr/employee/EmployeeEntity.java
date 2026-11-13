package com.restaurantmanager.core.hr.employee;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.hr.org.DepartmentEntity;
import com.restaurantmanager.core.hr.org.PositionEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "hr_employees")
public class EmployeeEntity {
    @Id
    private UUID id;

    @Column(name = "employee_number", nullable = false, unique = true, length = 30)
    private String employeeNumber;

    /** Optional link to the login account in {@code users}. */
    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String address;

    /** How this employee is paid (payroll payment files); see V39. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "payout_method", length = 20)
    private com.restaurantmanager.core.hr.payroll.PaymentMethod payoutMethod;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "bank_account_name", length = 160)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 40)
    private String bankAccountNumber;

    @Column(name = "momo_network", length = 30)
    private String momoNetwork;

    @Column(name = "momo_number", length = 20)
    private String momoNumber;

    @Column(name = "payout_updated_at")
    private java.time.Instant payoutUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private PositionEntity position;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_reason", length = 500)
    private String exitReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private EmployeeEntity reportingManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_branch_id")
    private BranchEntity primaryBranch;

    @Column(length = 2000)
    private String qualifications;

    @Column(length = 2000)
    private String experience;

    @Column(name = "created_by")
    private UUID createdBy;

    /** BCrypt hash of the employee's clock PIN (POS clock-in only; grants no other access). Never exposed. */
    @Column(name = "clock_pin_hash")
    private String clockPinHash;

    @Column(name = "clock_pin_fail_count", nullable = false)
    private short clockPinFailCount;

    @Column(name = "clock_pin_locked_until")
    private Instant clockPinLockedUntil;

    @Column(name = "clock_pin_set_at")
    private Instant clockPinSetAt;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<EmergencyContactEntity> emergencyContacts = new ArrayList<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
        return Stream.of(firstName, middleName, lastName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    public UUID primaryBranchId() {
        return primaryBranch == null ? null : primaryBranch.getId();
    }

    public UUID getId() { return id; }
    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public DepartmentEntity getDepartment() { return department; }
    public void setDepartment(DepartmentEntity department) { this.department = department; }
    public PositionEntity getPosition() { return position; }
    public void setPosition(PositionEntity position) { this.position = position; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(EmploymentStatus employmentStatus) { this.employmentStatus = employmentStatus; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public LocalDate getExitDate() { return exitDate; }
    public void setExitDate(LocalDate exitDate) { this.exitDate = exitDate; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }
    public EmployeeEntity getReportingManager() { return reportingManager; }
    public void setReportingManager(EmployeeEntity reportingManager) { this.reportingManager = reportingManager; }
    public BranchEntity getPrimaryBranch() { return primaryBranch; }
    public void setPrimaryBranch(BranchEntity primaryBranch) { this.primaryBranch = primaryBranch; }
    public String getQualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public List<EmergencyContactEntity> getEmergencyContacts() { return emergencyContacts; }
    public String getClockPinHash() { return clockPinHash; }
    public void setClockPinHash(String clockPinHash) { this.clockPinHash = clockPinHash; }
    public short getClockPinFailCount() { return clockPinFailCount; }
    public void setClockPinFailCount(short clockPinFailCount) { this.clockPinFailCount = clockPinFailCount; }
    public Instant getClockPinLockedUntil() { return clockPinLockedUntil; }
    public void setClockPinLockedUntil(Instant clockPinLockedUntil) { this.clockPinLockedUntil = clockPinLockedUntil; }
    public Instant getClockPinSetAt() { return clockPinSetAt; }
    public void setClockPinSetAt(Instant clockPinSetAt) { this.clockPinSetAt = clockPinSetAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public com.restaurantmanager.core.hr.payroll.PaymentMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(com.restaurantmanager.core.hr.payroll.PaymentMethod payoutMethod) { this.payoutMethod = payoutMethod; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    public String getMomoNetwork() { return momoNetwork; }
    public void setMomoNetwork(String momoNetwork) { this.momoNetwork = momoNetwork; }
    public String getMomoNumber() { return momoNumber; }
    public void setMomoNumber(String momoNumber) { this.momoNumber = momoNumber; }
    public java.time.Instant getPayoutUpdatedAt() { return payoutUpdatedAt; }
    public void setPayoutUpdatedAt(java.time.Instant payoutUpdatedAt) { this.payoutUpdatedAt = payoutUpdatedAt; }

    /** SSNIT number (social security). */
    @Column(name = "ssnit_number", length = 20)
    private String ssnitNumber;

    /** Ghana Card number (national ID), e.g. GHA-123456789-0. */
    @Column(name = "national_id", length = 20)
    private String nationalId;

    /** Taxpayer identification number (for individuals usually the Ghana Card number). */
    @Column(name = "tin", length = 20)
    private String tin;

    /** HR switched the login off on a suspension or exit; it is switched back on when the employee returns. */
    @Column(name = "login_disabled_by_hr", nullable = false)
    private boolean loginDisabledByHr;

    public String getSsnitNumber() { return ssnitNumber; }
    public void setSsnitNumber(String ssnitNumber) { this.ssnitNumber = ssnitNumber; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getTin() { return tin; }
    public void setTin(String tin) { this.tin = tin; }
    public boolean isLoginDisabledByHr() { return loginDisabledByHr; }
    public void setLoginDisabledByHr(boolean loginDisabledByHr) { this.loginDisabledByHr = loginDisabledByHr; }

    /** Status is ON_LEAVE because of approved leave; it returns to ACTIVE automatically when the leave ends. */
    @Column(name = "status_from_leave", nullable = false)
    private boolean statusFromLeave;

    public boolean isStatusFromLeave() { return statusFromLeave; }
    public void setStatusFromLeave(boolean statusFromLeave) { this.statusFromLeave = statusFromLeave; }
}
