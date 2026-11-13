package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.hr.attendance.ClockAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * An event exactly as a gateway delivered it, and what became of it. Never changed by a correction: a review decision is
 * recorded beside it (status RESOLVED/DISMISSED, who, when, note) and the clock action it produced is linked.
 */
@Entity
@Table(name = "hr_device_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_hr_device_events_identity", columnNames = {"device_serial_number", "device_event_id"}))
public class DeviceEventEntity {
    /** What the device reported. */
    public enum ReportedType { CLOCK_IN, BREAK_OUT, BREAK_IN, CLOCK_OUT, UNKNOWN }

    public enum Status {
        /** Recorded as a clock action. */
        ACCEPTED,
        /** The same action a few minutes after an accepted one (the person scanned twice): ignored. */
        REPEAT_SCAN,
        /** Direction unknown, device unknown or device clock wrong: a person must decide. */
        NEEDS_REVIEW,
        /** No employee is mapped to this device user id. */
        UNMATCHED,
        /** The attendance rules refused it (e.g. already clocked in, not assigned to this branch). */
        REJECTED,
        /** A manager applied it after review. */
        RESOLVED,
        /** A manager decided it should not count. */
        DISMISSED
    }

    @Id
    private UUID id;

    @Column(name = "gateway_id")
    private UUID gatewayId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "device_serial_number", nullable = false, length = 80)
    private String deviceSerialNumber;

    @Column(name = "device_event_id", nullable = false, length = 80)
    private String deviceEventId;

    @Column(name = "device_user_id", nullable = false, length = 40)
    private String deviceUserId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_type", nullable = false, length = 20)
    private ReportedType reportedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_action", length = 20)
    private ClockAction appliedAction;

    @Column(name = "authentication_method", length = 20)
    private String authenticationMethod;

    @Column(name = "device_timestamp", nullable = false)
    private Instant deviceTimestamp;

    @Column(name = "gateway_received_at")
    private Instant gatewayReceivedAt;

    @Column(name = "backend_received_at", nullable = false)
    private Instant backendReceivedAt;

    @Column(name = "raw_event", columnDefinition = "TEXT")
    private String rawEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 300)
    private String message;

    @Column(name = "attendance_event_id")
    private UUID attendanceEventId;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (backendReceivedAt == null) backendReceivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getGatewayId() { return gatewayId; }
    public void setGatewayId(UUID gatewayId) { this.gatewayId = gatewayId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getDeviceSerialNumber() { return deviceSerialNumber; }
    public void setDeviceSerialNumber(String deviceSerialNumber) { this.deviceSerialNumber = deviceSerialNumber; }
    public String getDeviceEventId() { return deviceEventId; }
    public void setDeviceEventId(String deviceEventId) { this.deviceEventId = deviceEventId; }
    public String getDeviceUserId() { return deviceUserId; }
    public void setDeviceUserId(String deviceUserId) { this.deviceUserId = deviceUserId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public ReportedType getReportedType() { return reportedType; }
    public void setReportedType(ReportedType reportedType) { this.reportedType = reportedType; }
    public ClockAction getAppliedAction() { return appliedAction; }
    public void setAppliedAction(ClockAction appliedAction) { this.appliedAction = appliedAction; }
    public String getAuthenticationMethod() { return authenticationMethod; }
    public void setAuthenticationMethod(String authenticationMethod) { this.authenticationMethod = authenticationMethod; }
    public Instant getDeviceTimestamp() { return deviceTimestamp; }
    public void setDeviceTimestamp(Instant deviceTimestamp) { this.deviceTimestamp = deviceTimestamp; }
    public Instant getGatewayReceivedAt() { return gatewayReceivedAt; }
    public void setGatewayReceivedAt(Instant gatewayReceivedAt) { this.gatewayReceivedAt = gatewayReceivedAt; }
    public Instant getBackendReceivedAt() { return backendReceivedAt; }
    public String getRawEvent() { return rawEvent; }
    public void setRawEvent(String rawEvent) { this.rawEvent = rawEvent; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public UUID getAttendanceEventId() { return attendanceEventId; }
    public void setAttendanceEventId(UUID attendanceEventId) { this.attendanceEventId = attendanceEventId; }
    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
}
