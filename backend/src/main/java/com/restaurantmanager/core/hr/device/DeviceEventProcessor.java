package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.hr.attendance.AttendanceService;
import com.restaurantmanager.core.hr.attendance.AttendanceSource;
import com.restaurantmanager.core.hr.attendance.BreakType;
import com.restaurantmanager.core.hr.attendance.ClockAction;
import com.restaurantmanager.core.hr.attendance.ClockGateway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Turns one device event into a clock action, or into a review item when that cannot be done safely.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Idempotent: (device serial, device event id) is processed once; a repeated delivery returns the first outcome.
 *       The clock action's request key is derived from the same pair, so even a race cannot record it twice.</li>
 *   <li>The direction comes from the device's own attendance status, else from the terminal's configured direction.
 *       It is never guessed by alternating in/out: a second clock-in is refused, never turned into a clock-out.</li>
 *   <li>The device's timestamp is the time of the action, not when it reached the server.</li>
 *   <li>A scan of the same action within {@link #REPEAT_WINDOW} of an accepted one is a double scan and is ignored.</li>
 * </ul>
 *
 * <p>Deliberately not one transaction: the attendance service records refused actions in its own transaction and
 * throws, which would otherwise roll back the stored event.</p>
 */
@Component
public class DeviceEventProcessor {
    static final Duration REPEAT_WINDOW = Duration.ofMinutes(5);
    static final Duration MAX_CLOCK_AHEAD = Duration.ofMinutes(2);

    private final DeviceEventRepository eventRepository;
    private final AttendanceDeviceRepository deviceRepository;
    private final EmployeeDeviceIdRepository deviceIdRepository;
    private final ClockGateway clockGateway;
    private final TransactionTemplate tx;

    public DeviceEventProcessor(DeviceEventRepository eventRepository, AttendanceDeviceRepository deviceRepository,
                                EmployeeDeviceIdRepository deviceIdRepository, ClockGateway clockGateway,
                                PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.deviceIdRepository = deviceIdRepository;
        this.clockGateway = clockGateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** An event as the gateway sends it. Extra device fields travel in {@code raw}. */
    public record IncomingEvent(String deviceSerialNumber, String deviceEventId, String employeeDeviceId, String eventType,
                                String authenticationMethod, Instant deviceTimestamp, Instant gatewayReceivedAt, String raw) {}

    public record Outcome(String deviceSerialNumber, String deviceEventId, DeviceEventEntity.Status status, String message,
                          boolean duplicate) {}

    public Outcome process(UUID gatewayId, IncomingEvent in) {
        var existing = eventRepository.findByDeviceSerialNumberAndDeviceEventId(in.deviceSerialNumber(), in.deviceEventId());
        if (existing.isPresent()) {
            return outcome(existing.get(), true);
        }
        DeviceEventEntity event = new DeviceEventEntity();
        event.setGatewayId(gatewayId);
        event.setDeviceSerialNumber(in.deviceSerialNumber());
        event.setDeviceEventId(in.deviceEventId());
        event.setDeviceUserId(in.employeeDeviceId());
        event.setReportedType(reportedType(in.eventType()));
        event.setAuthenticationMethod(in.authenticationMethod());
        event.setDeviceTimestamp(in.deviceTimestamp());
        event.setGatewayReceivedAt(in.gatewayReceivedAt());
        event.setRawEvent(in.raw());

        AttendanceDeviceEntity device = deviceRepository.findBySerialNumberIgnoreCase(in.deviceSerialNumber()).orElse(null);
        if (device != null) {
            event.setDeviceId(device.getId());
            touchDevice(device, in.deviceTimestamp());
        }
        decide(event, device, "auto");
        try {
            return outcome(tx.execute(status -> eventRepository.saveAndFlush(event)), false);
        } catch (DataIntegrityViolationException race) {
            // Delivered twice at the same moment: the other copy was stored first.
            return eventRepository.findByDeviceSerialNumberAndDeviceEventId(in.deviceSerialNumber(), in.deviceEventId())
                    .map(e -> outcome(e, true)).orElseThrow(() -> race);
        }
    }

    /** Runs the decision again (e.g. after the employee was mapped or the device registered). */
    public DeviceEventEntity reprocess(DeviceEventEntity event) {
        AttendanceDeviceEntity device = deviceRepository.findBySerialNumberIgnoreCase(event.getDeviceSerialNumber()).orElse(null);
        event.setDeviceId(device == null ? null : device.getId());
        event.setEmployeeId(null);
        event.setAppliedAction(null);
        // A fresh request key: the first attempt's key would only replay its old refusal.
        decide(event, device, "retry-" + Instant.now().getEpochSecond());
        return tx.execute(status -> eventRepository.saveAndFlush(event));
    }

    /** A reviewer chose the action. Recorded with the device's time and the reviewer as the person who decided. */
    public DeviceEventEntity applyAs(DeviceEventEntity event, ClockAction action, UUID reviewerId, String note) {
        if (event.getEmployeeId() == null) {
            throw new ApiException(409, "Map device user " + event.getDeviceUserId() + " to an employee first");
        }
        AttendanceDeviceEntity device = event.getDeviceId() == null ? null : deviceRepository.findById(event.getDeviceId()).orElse(null);
        if (device == null) {
            throw new ApiException(409, "Register device " + event.getDeviceSerialNumber() + " first");
        }
        AttendanceService.ClockResult result = clockGateway.clock(new AttendanceService.ClockCommand(event.getEmployeeId(), action,
                breakType(action), device.getBranchId(), AttendanceSource.DEVICE, terminal(device), requestKey(event, "review"),
                event.getDeviceTimestamp(), reviewerId));
        event.setAppliedAction(action);
        event.setAttendanceEventId(result.eventId());
        event.setStatus(DeviceEventEntity.Status.RESOLVED);
        event.setMessage(truncate(result.message()));
        event.setResolvedBy(reviewerId);
        event.setResolvedAt(Instant.now());
        event.setResolutionNote(truncate(note));
        return tx.execute(status -> eventRepository.saveAndFlush(event));
    }

    // ------------------------------------------------------------------ decision

    private void decide(DeviceEventEntity event, AttendanceDeviceEntity device, String keyKind) {
        if (device == null) {
            set(event, DeviceEventEntity.Status.NEEDS_REVIEW, "Device " + event.getDeviceSerialNumber() + " is not registered");
            return;
        }
        if (!device.isActive()) {
            set(event, DeviceEventEntity.Status.NEEDS_REVIEW, "Device " + device.getName() + " is switched off in the system");
            return;
        }
        var mapping = deviceIdRepository.findFirstByDeviceIdAndDeviceUserId(device.getId(), event.getDeviceUserId())
                .or(() -> deviceIdRepository.findFirstByDeviceIdIsNullAndDeviceUserId(event.getDeviceUserId()));
        if (mapping.isEmpty()) {
            set(event, DeviceEventEntity.Status.UNMATCHED, "No employee is linked to device user " + event.getDeviceUserId());
            return;
        }
        event.setEmployeeId(mapping.get().getEmployeeId());

        ClockAction action = action(event.getReportedType(), device.getDirectionMode());
        if (action == null) {
            set(event, DeviceEventEntity.Status.NEEDS_REVIEW,
                    "The device did not say whether this was a clock-in, break or clock-out; choose the action");
            return;
        }
        event.setAppliedAction(action);

        if (event.getDeviceTimestamp().isAfter(Instant.now().plus(MAX_CLOCK_AHEAD))) {
            set(event, DeviceEventEntity.Status.NEEDS_REVIEW, "The device clock is ahead of the server; check the device time");
            return;
        }
        boolean repeat = !eventRepository.recentSameAction(event.getEmployeeId(), action,
                event.getDeviceTimestamp().minus(REPEAT_WINDOW), event.getDeviceTimestamp(), PageRequest.of(0, 1)).isEmpty();
        if (repeat) {
            set(event, DeviceEventEntity.Status.REPEAT_SCAN, "Scanned again within " + REPEAT_WINDOW.toMinutes() + " minutes; ignored");
            return;
        }
        try {
            AttendanceService.ClockResult result = clockGateway.clock(new AttendanceService.ClockCommand(event.getEmployeeId(), action,
                    breakType(action), device.getBranchId(), AttendanceSource.DEVICE, terminal(device), requestKey(event, keyKind),
                    event.getDeviceTimestamp(), null));
            event.setAttendanceEventId(result.eventId());
            set(event, DeviceEventEntity.Status.ACCEPTED, result.message());
        } catch (ApiException refused) {
            set(event, DeviceEventEntity.Status.REJECTED, refused.getMessage());
        }
    }

    static DeviceEventEntity.ReportedType reportedType(String raw) {
        if (raw == null) {
            return DeviceEventEntity.ReportedType.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')) {
            case "CLOCK_IN", "CHECK_IN", "CHECKIN", "IN", "OVERTIME_IN", "OVERTIMEIN" -> DeviceEventEntity.ReportedType.CLOCK_IN;
            case "CLOCK_OUT", "CHECK_OUT", "CHECKOUT", "OUT", "OVERTIME_OUT", "OVERTIMEOUT" -> DeviceEventEntity.ReportedType.CLOCK_OUT;
            case "BREAK_OUT", "BREAKOUT", "BREAK_START" -> DeviceEventEntity.ReportedType.BREAK_OUT;
            case "BREAK_IN", "BREAKIN", "BREAK_END" -> DeviceEventEntity.ReportedType.BREAK_IN;
            default -> DeviceEventEntity.ReportedType.UNKNOWN;
        };
    }

    /** The device's own report wins; otherwise the terminal's fixed direction; otherwise nothing (review). */
    static ClockAction action(DeviceEventEntity.ReportedType reported, AttendanceDeviceEntity.DirectionMode mode) {
        switch (reported) {
            case CLOCK_IN: return ClockAction.CLOCK_IN;
            case CLOCK_OUT: return ClockAction.CLOCK_OUT;
            case BREAK_OUT: return ClockAction.BREAK_START;
            case BREAK_IN: return ClockAction.BREAK_END;
            default: break;
        }
        return switch (mode) {
            case CLOCK_IN -> ClockAction.CLOCK_IN;
            case CLOCK_OUT -> ClockAction.CLOCK_OUT;
            case BREAK_OUT -> ClockAction.BREAK_START;
            case BREAK_IN -> ClockAction.BREAK_END;
            case DEVICE_REPORTED -> null;
        };
    }

    private static BreakType breakType(ClockAction action) {
        // Device breaks are meal breaks; whether they are paid follows the attendance policy.
        return action == ClockAction.BREAK_START ? BreakType.MEAL : null;
    }

    private static String terminal(AttendanceDeviceEntity device) {
        String t = "device:" + device.getSerialNumber();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    /** A stable, short request key per event (serial + event id can be long). */
    private static String requestKey(DeviceEventEntity event, String kind) {
        UUID stable = UUID.nameUUIDFromBytes((event.getDeviceSerialNumber() + "|" + event.getDeviceEventId()).getBytes(StandardCharsets.UTF_8));
        return "dev-" + kind + ":" + stable;
    }

    private void touchDevice(AttendanceDeviceEntity device, Instant at) {
        if (device.getLastEventAt() == null || at.isAfter(device.getLastEventAt())) {
            tx.executeWithoutResult(status -> deviceRepository.findById(device.getId()).ifPresent(d -> {
                d.setLastEventAt(at.isAfter(Instant.now()) ? Instant.now() : at);
                deviceRepository.save(d);
            }));
        }
    }

    private static void set(DeviceEventEntity event, DeviceEventEntity.Status status, String message) {
        event.setStatus(status);
        event.setMessage(truncate(message));
    }

    private static String truncate(String text) {
        return text == null ? null : text.length() > 300 ? text.substring(0, 300) : text;
    }

    private static Outcome outcome(DeviceEventEntity e, boolean duplicate) {
        return new Outcome(e.getDeviceSerialNumber(), e.getDeviceEventId(), e.getStatus(), e.getMessage(), duplicate);
    }
}
