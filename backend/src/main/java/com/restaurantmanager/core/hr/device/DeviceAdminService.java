package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.attendance.ClockAction;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin side of the device integration: gateways and their keys, devices, which device user is which employee, the review
 * queue, and the health overview. Configuration needs {@code hr.settings.manage}; managers see and resolve their branches'
 * events with {@code hr.attendance.view} / {@code hr.attendance.correct}.
 */
@Service
public class DeviceAdminService {
    /** A gateway that has not reported for this long is shown as offline. */
    static final Duration GATEWAY_OFFLINE_AFTER = Duration.ofMinutes(3);
    static final int CLOCK_DRIFT_WARN_SECONDS = 120;
    static final int PENDING_WARN = 50;
    static final Set<DeviceEventEntity.Status> REVIEW = EnumSet.of(DeviceEventEntity.Status.NEEDS_REVIEW,
            DeviceEventEntity.Status.UNMATCHED, DeviceEventEntity.Status.REJECTED);

    private final AttendanceGatewayRepository gatewayRepository;
    private final AttendanceDeviceRepository deviceRepository;
    private final EmployeeDeviceIdRepository deviceIdRepository;
    private final DeviceEventRepository eventRepository;
    private final DeviceEventProcessor processor;
    private final HrAccessService accessService;
    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final HrAuditService audit;
    private final ZoneId zone;

    public DeviceAdminService(AttendanceGatewayRepository gatewayRepository, AttendanceDeviceRepository deviceRepository,
                              EmployeeDeviceIdRepository deviceIdRepository, DeviceEventRepository eventRepository,
                              DeviceEventProcessor processor, HrAccessService accessService, EmployeeService employeeService,
                              EmployeeRepository employeeRepository, BranchRepository branchRepository, HrAuditService audit,
                              @org.springframework.beans.factory.annotation.Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.gatewayRepository = gatewayRepository;
        this.deviceRepository = deviceRepository;
        this.deviceIdRepository = deviceIdRepository;
        this.eventRepository = eventRepository;
        this.processor = processor;
        this.accessService = accessService;
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.branchRepository = branchRepository;
        this.audit = audit;
        this.zone = ZoneId.of(timeZone);
    }

    // ================================================================== gateways

    public record GatewayRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Use letters, digits, dot, dash or underscore") String gatewayIdentifier,
                                 UUID branchId) {}

    public record GatewayView(UUID id, String name, String gatewayIdentifier, String keyPrefix, UUID branchId, String branchName,
                              String status, Instant lastHeartbeatAt, Instant lastSyncAt, int pendingEvents,
                              int failedSyncAttempts, String lastError, String softwareVersion, Instant revokedAt, int deviceCount) {}

    /** The full key is returned only here, once. */
    public record GatewayKeyView(GatewayView gateway, String key) {}

    @Transactional(readOnly = true)
    public List<GatewayView> gateways(UserPrincipal principal) {
        accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        return gatewayRepository.findAllByOrderByCreatedAtAsc().stream().map(this::gatewayView).toList();
    }

    @Transactional
    public GatewayKeyView createGateway(UserPrincipal principal, GatewayRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        if (request.branchId() != null) access.assertBranch(request.branchId());
        if (gatewayRepository.findByGatewayIdentifierIgnoreCase(request.gatewayIdentifier().trim()).isPresent()) {
            throw new ApiException(409, "A gateway called " + request.gatewayIdentifier().trim() + " already exists");
        }
        GatewayService.IssuedKey key = GatewayService.newKey();
        AttendanceGatewayEntity gateway = new AttendanceGatewayEntity();
        gateway.setName(request.name().trim());
        gateway.setGatewayIdentifier(request.gatewayIdentifier().trim());
        gateway.setBranchId(request.branchId());
        gateway.setKeyHash(key.hash());
        gateway.setKeyPrefix(key.prefix());
        gateway.setCreatedBy(access.userId());
        gatewayRepository.save(gateway);
        log(access, "GATEWAY_CREATED", gateway.getId(), Map.of("gateway", gateway.getGatewayIdentifier()));
        return new GatewayKeyView(gatewayView(gateway), key.key());
    }

    /** Issues a new key; the old one stops working at once. Also brings a revoked gateway back. */
    @Transactional
    public GatewayKeyView rotateKey(UserPrincipal principal, UUID gatewayId) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        AttendanceGatewayEntity gateway = gateway(gatewayId);
        GatewayService.IssuedKey key = GatewayService.newKey();
        gateway.setKeyHash(key.hash());
        gateway.setKeyPrefix(key.prefix());
        gateway.setRevokedAt(null);
        gatewayRepository.save(gateway);
        log(access, "GATEWAY_KEY_ROTATED", gateway.getId(), Map.of("gateway", gateway.getGatewayIdentifier()));
        return new GatewayKeyView(gatewayView(gateway), key.key());
    }

    @Transactional
    public GatewayView revoke(UserPrincipal principal, UUID gatewayId) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        AttendanceGatewayEntity gateway = gateway(gatewayId);
        if (gateway.getRevokedAt() == null) {
            gateway.setRevokedAt(Instant.now());
            gatewayRepository.save(gateway);
            log(access, "GATEWAY_REVOKED", gateway.getId(), Map.of("gateway", gateway.getGatewayIdentifier()));
        }
        return gatewayView(gateway);
    }

    // ================================================================== devices

    public record DeviceRequest(@NotBlank @Size(max = 120) String name,
                                @NotBlank @Size(max = 80) String serialNumber,
                                @NotNull AttendanceDeviceEntity.Vendor vendor,
                                @Size(max = 80) String model,
                                @Size(max = 80) String ipAddress,
                                @Min(1) @Max(65535) Integer port,
                                Boolean useHttps,
                                UUID gatewayId,
                                @NotNull UUID branchId,
                                @NotNull AttendanceDeviceEntity.DirectionMode directionMode,
                                Boolean active) {}

    public record DeviceView(UUID id, String name, String serialNumber, AttendanceDeviceEntity.Vendor vendor, String model,
                             String ipAddress, Integer port, boolean useHttps, UUID gatewayId, String gatewayName, UUID branchId,
                             String branchName, AttendanceDeviceEntity.DirectionMode directionMode, boolean active, String status,
                             Instant lastConnectedAt, Instant lastEventAt, Integer clockOffsetSeconds, String statusMessage) {}

    @Transactional(readOnly = true)
    public List<DeviceView> devices(UserPrincipal principal) {
        HrAccess access = accessService.requireAny(principal, HrPermission.SETTINGS_MANAGE, HrPermission.ATTENDANCE_VIEW);
        Map<UUID, AttendanceGatewayEntity> gateways = gatewaysById();
        Map<UUID, String> branches = branchNames();
        return deviceRepository.findAllByOrderByNameAsc().stream()
                .filter(d -> access.canSeeBranch(d.getBranchId()))
                .map(d -> deviceView(d, gateways, branches)).toList();
    }

    @Transactional
    public DeviceView saveDevice(UserPrincipal principal, UUID deviceId, DeviceRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        access.assertBranch(request.branchId());
        branchRepository.findById(request.branchId()).orElseThrow(() -> new ApiException(404, "Branch not found"));
        if (request.gatewayId() != null) gateway(request.gatewayId());
        String serial = request.serialNumber().trim();
        var sameSerial = deviceRepository.findBySerialNumberIgnoreCase(serial);
        AttendanceDeviceEntity device;
        if (deviceId == null) {
            if (sameSerial.isPresent()) throw new ApiException(409, "Device " + serial + " is already registered");
            device = new AttendanceDeviceEntity();
        } else {
            device = deviceRepository.findById(deviceId).orElseThrow(() -> new ApiException(404, "Device not found"));
            access.assertBranch(device.getBranchId());
            if (sameSerial.isPresent() && !sameSerial.get().getId().equals(deviceId)) {
                throw new ApiException(409, "Device " + serial + " is already registered");
            }
        }
        device.setName(request.name().trim());
        device.setSerialNumber(serial);
        device.setVendor(request.vendor());
        device.setModel(blank(request.model()));
        device.setIpAddress(blank(request.ipAddress()));
        device.setPort(request.port());
        device.setUseHttps(Boolean.TRUE.equals(request.useHttps()));
        device.setGatewayId(request.gatewayId());
        device.setBranchId(request.branchId());
        device.setDirectionMode(request.directionMode());
        device.setActive(request.active() == null || request.active());
        deviceRepository.save(device);
        log(access, deviceId == null ? "DEVICE_REGISTERED" : "DEVICE_UPDATED", device.getId(),
                Map.of("serial", serial, "direction", request.directionMode().name(), "active", device.isActive()));
        return deviceView(device, gatewaysById(), branchNames());
    }

    // ================================================================== employee ↔ device user

    public record DeviceIdRequest(@NotNull UUID employeeId, UUID deviceId,
                                  @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters and digits only") String deviceUserId) {}

    public record DeviceIdView(UUID id, UUID employeeId, String employeeName, String employeeNumber, UUID deviceId, String deviceName,
                               String deviceUserId) {}

    @Transactional(readOnly = true)
    public List<DeviceIdView> deviceIds(UserPrincipal principal) {
        HrAccess access = accessService.requireAny(principal, HrPermission.SETTINGS_MANAGE, HrPermission.ATTENDANCE_VIEW);
        List<EmployeeDeviceIdEntity> rows = deviceIdRepository.findAllByOrderByDeviceUserIdAsc();
        Map<UUID, EmployeeEntity> employees = employeeRepository.findByIdIn(rows.stream().map(EmployeeDeviceIdEntity::getEmployeeId).toList())
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        Map<UUID, String> devices = deviceRepository.findAll().stream()
                .collect(Collectors.toMap(AttendanceDeviceEntity::getId, AttendanceDeviceEntity::getName));
        return rows.stream()
                .filter(r -> employees.containsKey(r.getEmployeeId()) && access.canSeeBranch(employees.get(r.getEmployeeId()).primaryBranchId()))
                .map(r -> {
                    EmployeeEntity e = employees.get(r.getEmployeeId());
                    return new DeviceIdView(r.getId(), e.getId(), e.fullName(), e.getEmployeeNumber(), r.getDeviceId(),
                            r.getDeviceId() == null ? null : devices.get(r.getDeviceId()), r.getDeviceUserId());
                }).toList();
    }

    /** Links a device user to an employee, then retries that user's unmatched events. */
    @Transactional
    public DeviceIdView addDeviceId(UserPrincipal principal, DeviceIdRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        EmployeeEntity employee = employeeService.loadInScope(request.employeeId(), access);
        String userId = request.deviceUserId().trim();
        if (request.deviceId() != null) {
            deviceRepository.findById(request.deviceId()).orElseThrow(() -> new ApiException(404, "Device not found"));
        }
        var taken = request.deviceId() == null
                ? deviceIdRepository.findFirstByDeviceIdIsNullAndDeviceUserId(userId)
                : deviceIdRepository.findFirstByDeviceIdAndDeviceUserId(request.deviceId(), userId);
        if (taken.isPresent()) {
            throw new ApiException(409, "Device user " + userId + " is already linked to another employee");
        }
        EmployeeDeviceIdEntity row = new EmployeeDeviceIdEntity();
        row.setEmployeeId(employee.getId());
        row.setDeviceId(request.deviceId());
        row.setDeviceUserId(userId);
        row.setCreatedBy(access.userId());
        deviceIdRepository.save(row);
        log(access, "DEVICE_USER_LINKED", employee.getId(), Map.of("deviceUserId", userId,
                "device", request.deviceId() == null ? "all" : request.deviceId().toString()));
        String deviceName = request.deviceId() == null ? null : deviceRepository.findById(request.deviceId()).map(AttendanceDeviceEntity::getName).orElse(null);
        return new DeviceIdView(row.getId(), employee.getId(), employee.fullName(), employee.getEmployeeNumber(), row.getDeviceId(),
                deviceName, userId);
    }

    @Transactional
    public void removeDeviceId(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.SETTINGS_MANAGE);
        EmployeeDeviceIdEntity row = deviceIdRepository.findById(id).orElseThrow(() -> new ApiException(404, "Link not found"));
        employeeService.loadInScope(row.getEmployeeId(), access);
        deviceIdRepository.delete(row);
        log(access, "DEVICE_USER_UNLINKED", row.getEmployeeId(), Map.of("deviceUserId", row.getDeviceUserId()));
    }

    // ================================================================== events and review

    public record EventView(UUID id, String deviceSerialNumber, String deviceName, UUID branchId, String deviceEventId,
                            String deviceUserId, UUID employeeId, String employeeName, DeviceEventEntity.ReportedType reportedType,
                            ClockAction appliedAction, String authenticationMethod, Instant deviceTimestamp,
                            Instant gatewayReceivedAt, Instant backendReceivedAt, DeviceEventEntity.Status status, String message,
                            String resolvedByNote, Instant resolvedAt) {}

    @Transactional(readOnly = true)
    public List<EventView> events(UserPrincipal principal, String filter, LocalDate from, LocalDate to) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_VIEW);
        Set<DeviceEventEntity.Status> statuses = "review".equalsIgnoreCase(filter) || filter == null
                ? REVIEW : EnumSet.allOf(DeviceEventEntity.Status.class);
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(7) : from;
        if (end.isBefore(start) || start.plusDays(62).isBefore(end)) {
            throw new ApiException(400, "Choose a range of at most 62 days");
        }
        List<DeviceEventEntity> rows = eventRepository.findByStatusesBetween(statuses, start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant(), PageRequest.of(0, 500));
        return views(rows, access);
    }

    // No surrounding transaction: a refused clock action must not roll back the stored decision.
    public EventView apply(UserPrincipal principal, UUID eventId, ClockAction action, String note) {
        if (action == null) throw new ApiException(400, "Choose the action to record");
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_CORRECT);
        DeviceEventEntity event = reviewable(eventId, access);
        if (event.getEmployeeId() != null) employeeService.loadInScope(event.getEmployeeId(), access);
        DeviceEventEntity saved = processor.applyAs(event, action, access.userId(), blank(note));
        audit.log(access.userId(), AuditAction.HR_ATTENDANCE_DEVICE_EVENT_RESOLVED, "HR_DEVICE_EVENT", saved.getId(),
                Map.of("decision", "APPLIED", "action", action.name(), "deviceUserId", saved.getDeviceUserId(),
                        "deviceTime", saved.getDeviceTimestamp().toString(), "note", Objects.toString(note, "")));
        return views(List.of(saved), access).get(0);
    }

    // No surrounding transaction: a refused clock action must not roll back the stored decision.
    public EventView retry(UserPrincipal principal, UUID eventId) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_CORRECT);
        DeviceEventEntity saved = processor.reprocess(reviewable(eventId, access));
        return views(List.of(saved), access).get(0);
    }

    @Transactional
    public EventView dismiss(UserPrincipal principal, UUID eventId, String note) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_CORRECT);
        if (note == null || note.isBlank()) throw new ApiException(400, "Say why this scan should not count");
        DeviceEventEntity event = reviewable(eventId, access);
        event.setStatus(DeviceEventEntity.Status.DISMISSED);
        event.setResolvedBy(access.userId());
        event.setResolvedAt(Instant.now());
        event.setResolutionNote(note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        eventRepository.save(event);
        audit.log(access.userId(), AuditAction.HR_ATTENDANCE_DEVICE_EVENT_RESOLVED, "HR_DEVICE_EVENT", event.getId(),
                Map.of("decision", "DISMISSED", "deviceUserId", event.getDeviceUserId(), "note", note.trim()));
        return views(List.of(event), access).get(0);
    }

    private DeviceEventEntity reviewable(UUID id, HrAccess access) {
        DeviceEventEntity event = eventRepository.findById(id).orElseThrow(() -> new ApiException(404, "Event not found"));
        if (!REVIEW.contains(event.getStatus())) {
            throw new ApiException(409, "This scan was already handled (" + event.getStatus().name().toLowerCase().replace('_', ' ') + ")");
        }
        UUID branch = event.getDeviceId() == null ? null : deviceRepository.findById(event.getDeviceId()).map(AttendanceDeviceEntity::getBranchId).orElse(null);
        if (branch != null) access.assertBranch(branch);
        else if (!access.allBranches()) throw new ApiException(403, "Only central HR can handle scans from unregistered devices");
        return event;
    }

    private List<EventView> views(List<DeviceEventEntity> rows, HrAccess access) {
        Map<UUID, AttendanceDeviceEntity> devices = deviceRepository.findAll().stream()
                .collect(Collectors.toMap(AttendanceDeviceEntity::getId, Function.identity()));
        Map<UUID, String> employees = employeeRepository.findByIdIn(rows.stream().map(DeviceEventEntity::getEmployeeId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(EmployeeEntity::getId, EmployeeEntity::fullName));
        List<EventView> out = new ArrayList<>();
        for (DeviceEventEntity e : rows) {
            AttendanceDeviceEntity d = e.getDeviceId() == null ? null : devices.get(e.getDeviceId());
            UUID branch = d == null ? null : d.getBranchId();
            if (branch == null ? !access.allBranches() : !access.canSeeBranch(branch)) continue;
            out.add(new EventView(e.getId(), e.getDeviceSerialNumber(), d == null ? null : d.getName(), branch, e.getDeviceEventId(),
                    e.getDeviceUserId(), e.getEmployeeId(), e.getEmployeeId() == null ? null : employees.get(e.getEmployeeId()),
                    e.getReportedType(), e.getAppliedAction(), e.getAuthenticationMethod(), e.getDeviceTimestamp(),
                    e.getGatewayReceivedAt(), e.getBackendReceivedAt(), e.getStatus(), e.getMessage(), e.getResolutionNote(),
                    e.getResolvedAt()));
        }
        return out;
    }

    // ================================================================== overview

    public record Alert(String severity, String text) {}

    public record Overview(List<GatewayView> gateways, List<DeviceView> devices, Map<String, Long> reviewCounts, long linkedEmployees,
                           List<Alert> alerts) {}

    @Transactional(readOnly = true)
    public Overview overview(UserPrincipal principal) {
        HrAccess access = accessService.require(principal, HrPermission.ATTENDANCE_VIEW);
        List<DeviceView> devices = devices(principal);
        Set<UUID> gatewayIds = devices.stream().map(DeviceView::gatewayId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<GatewayView> gateways = gatewayRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(g -> access.allBranches() || gatewayIds.contains(g.getId()) || access.canSeeBranch(g.getBranchId()))
                .map(this::gatewayView).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<DeviceEventEntity.Status, Long> byStatus = new HashMap<>();
        for (EventView e : views(eventRepository.findByStatusesBetween(REVIEW, Instant.EPOCH, Instant.now().plus(Duration.ofDays(1)),
                PageRequest.of(0, 2000)), access)) {
            byStatus.merge(e.status(), 1L, Long::sum);
        }
        for (DeviceEventEntity.Status s : REVIEW) counts.put(s.name(), byStatus.getOrDefault(s, 0L));

        List<Alert> alerts = new ArrayList<>();
        for (GatewayView g : gateways) {
            if ("OFFLINE".equals(g.status())) {
                alerts.add(new Alert("danger", "Gateway " + g.name() + " has not reported since "
                        + (g.lastHeartbeatAt() == null ? "it was set up" : g.lastHeartbeatAt().atZone(zone).toLocalDateTime().toString().replace('T', ' ').substring(0, 16))));
            }
            if (!"REVOKED".equals(g.status()) && g.pendingEvents() >= PENDING_WARN) {
                alerts.add(new Alert("warning", "Gateway " + g.name() + " has " + g.pendingEvents() + " events waiting to be sent"));
            }
            if (!"REVOKED".equals(g.status()) && g.failedSyncAttempts() > 0 && g.lastError() != null) {
                alerts.add(new Alert("warning", "Gateway " + g.name() + " could not send events: " + g.lastError()));
            }
        }
        for (DeviceView d : devices) {
            if (!d.active()) continue;
            if ("OFFLINE".equals(d.status()) || "ERROR".equals(d.status())) {
                alerts.add(new Alert("danger", "Device " + d.name() + " is not connected" + (d.statusMessage() == null ? "" : ": " + d.statusMessage())));
            }
            if (d.clockOffsetSeconds() != null && Math.abs(d.clockOffsetSeconds()) > CLOCK_DRIFT_WARN_SECONDS) {
                alerts.add(new Alert("warning", "Device " + d.name() + " clock is " + Math.abs(d.clockOffsetSeconds()) / 60 + " min "
                        + (d.clockOffsetSeconds() > 0 ? "fast" : "slow") + "; set its time (NTP)"));
            }
        }
        long unmatched = counts.getOrDefault("UNMATCHED", 0L);
        if (unmatched > 0) alerts.add(new Alert("warning", unmatched + " scan(s) from device users not linked to an employee"));
        long review = counts.getOrDefault("NEEDS_REVIEW", 0L) + counts.getOrDefault("REJECTED", 0L);
        if (review > 0) alerts.add(new Alert("info", review + " scan(s) waiting for review"));
        return new Overview(gateways, devices, counts, deviceIds(principal).size(), alerts);
    }

    // ================================================================== helpers

    private GatewayView gatewayView(AttendanceGatewayEntity g) {
        String status = g.getRevokedAt() != null ? "REVOKED"
                : g.getLastHeartbeatAt() == null ? "NEVER_CONNECTED"
                : g.getLastHeartbeatAt().isBefore(Instant.now().minus(GATEWAY_OFFLINE_AFTER)) ? "OFFLINE" : "ONLINE";
        String branchName = g.getBranchId() == null ? null : branchRepository.findById(g.getBranchId()).map(BranchEntity::getName).orElse(null);
        int devices = deviceRepository.findByGatewayIdOrderByNameAsc(g.getId()).size();
        return new GatewayView(g.getId(), g.getName(), g.getGatewayIdentifier(), g.getKeyPrefix(), g.getBranchId(), branchName, status,
                g.getLastHeartbeatAt(), g.getLastSyncAt(), g.getPendingEvents(), g.getFailedSyncAttempts(), g.getLastError(),
                g.getSoftwareVersion(), g.getRevokedAt(), devices);
    }

    private DeviceView deviceView(AttendanceDeviceEntity d, Map<UUID, AttendanceGatewayEntity> gateways, Map<UUID, String> branches) {
        AttendanceGatewayEntity g = d.getGatewayId() == null ? null : gateways.get(d.getGatewayId());
        // A device behind a silent gateway is not known to be online any more.
        String status = !d.isActive() ? "DISABLED"
                : g == null ? "NO_GATEWAY"
                : g.getRevokedAt() != null || g.getLastHeartbeatAt() == null || g.getLastHeartbeatAt().isBefore(Instant.now().minus(GATEWAY_OFFLINE_AFTER))
                ? "OFFLINE" : d.getConnectionStatus().name();
        return new DeviceView(d.getId(), d.getName(), d.getSerialNumber(), d.getVendor(), d.getModel(), d.getIpAddress(), d.getPort(),
                d.isUseHttps(), d.getGatewayId(), g == null ? null : g.getName(), d.getBranchId(), branches.get(d.getBranchId()),
                d.getDirectionMode(), d.isActive(), status, d.getLastConnectedAt(), d.getLastEventAt(), d.getClockOffsetSeconds(),
                d.getStatusMessage());
    }

    private Map<UUID, AttendanceGatewayEntity> gatewaysById() {
        return gatewayRepository.findAll().stream().collect(Collectors.toMap(AttendanceGatewayEntity::getId, Function.identity()));
    }

    private Map<UUID, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
    }

    private AttendanceGatewayEntity gateway(UUID id) {
        return gatewayRepository.findById(id).orElseThrow(() -> new ApiException(404, "Gateway not found"));
    }

    private void log(HrAccess access, String what, UUID entityId, Map<String, ?> details) {
        Map<String, Object> metadata = new LinkedHashMap<>(details);
        metadata.put("change", what);
        audit.log(access.userId(), AuditAction.HR_ATTENDANCE_DEVICE_CHANGED, "HR_ATTENDANCE_DEVICE", entityId, metadata);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
