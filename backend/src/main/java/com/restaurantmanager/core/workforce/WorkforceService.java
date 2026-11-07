package com.restaurantmanager.core.workforce;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Staff rota, time clock and timesheets. */
@Service
public class WorkforceService {
    static final long LATE_GRACE_MINUTES = 5;
    static final long DAILY_OVERTIME_THRESHOLD_MINUTES = 8 * 60;
    private static final Duration LINK_WINDOW = Duration.ofMinutes(90);

    private final WorkScheduleRepository scheduleRepository;
    private final TimeEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final ZoneId zone;

    public WorkforceService(WorkScheduleRepository scheduleRepository, TimeEntryRepository entryRepository,
                            UserRepository userRepository,
                            @org.springframework.beans.factory.annotation.Value("${app.time-zone:Africa/Accra}") String timeZone) {
        this.scheduleRepository = scheduleRepository;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.zone = ZoneId.of(timeZone);
    }

    // ------------------------------------------------------------ rota

    /** Active staff who can be put on the rota (everyone except customers), for managers building schedules. */
    @Transactional(readOnly = true)
    public List<StaffMember> staff() {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() != Role.CUSTOMER)
                .sorted(Comparator.comparing(UserEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new StaffMember(u.getId(), u.getName(), u.getRole().name(),
                        u.getBranch() == null ? null : u.getBranch().getId()))
                .toList();
    }

    @Transactional
    public ScheduleView saveSchedule(UUID id, ScheduleRequest request, UserPrincipal manager) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(400, "Shift must end after it starts");
        }
        if (Duration.between(request.startsAt(), request.endsAt()).toHours() > 16) {
            throw new ApiException(400, "A scheduled shift cannot be longer than 16 hours");
        }
        UserEntity staff = userRepository.findById(request.userId()).orElseThrow(() -> new ApiException(404, "Staff member not found"));
        if (staff.getRole() == Role.CUSTOMER) {
            throw new ApiException(400, "Customers cannot be scheduled");
        }
        WorkScheduleEntity schedule = id == null ? new WorkScheduleEntity()
                : scheduleRepository.findById(id).orElseThrow(() -> new ApiException(404, "Shift not found"));
        boolean overlaps = scheduleRepository.findOverlapping(request.startsAt(), request.endsAt(), request.userId()).stream()
                .anyMatch(other -> !other.getId().equals(schedule.getId()));
        if (overlaps) {
            throw new ApiException(409, staff.getName() + " already has a shift that overlaps this time");
        }
        schedule.setUserId(staff.getId());
        schedule.setBranchId(request.branchId() != null ? request.branchId() : (staff.getBranch() == null ? null : staff.getBranch().getId()));
        schedule.setStartsAt(request.startsAt());
        schedule.setEndsAt(request.endsAt());
        schedule.setPosition(blankToNull(request.position()));
        schedule.setNotes(blankToNull(request.notes()));
        if (id == null) {
            schedule.setCreatedBy(manager.userId());
        }
        return toView(scheduleRepository.save(schedule), staff);
    }

    @Transactional
    public void deleteSchedule(UUID id) {
        scheduleRepository.delete(scheduleRepository.findById(id).orElseThrow(() -> new ApiException(404, "Shift not found")));
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> schedules(Instant from, Instant to, UUID userId) {
        List<WorkScheduleEntity> rows = scheduleRepository.findOverlapping(from, to, userId);
        Map<UUID, UserEntity> users = users(rows.stream().map(WorkScheduleEntity::getUserId).toList());
        return rows.stream().map(s -> toView(s, users.get(s.getUserId()))).toList();
    }

    // ------------------------------------------------------------ time clock

    @Transactional
    public TimeEntryView clockIn(UserPrincipal principal, String notes) {
        if (entryRepository.findFirstByUserIdAndClockOutAtIsNull(principal.userId()).isPresent()) {
            throw new ApiException(409, "You are already clocked in");
        }
        Instant now = Instant.now();
        TimeEntryEntity entry = new TimeEntryEntity();
        entry.setUserId(principal.userId());
        entry.setBranchId(principal.branchId());
        entry.setClockInAt(now);
        entry.setNotes(blankToNull(notes));
        scheduleRepository.findOverlapping(now.minus(LINK_WINDOW), now.plus(LINK_WINDOW), principal.userId()).stream()
                .min(Comparator.comparing(s -> Duration.between(s.getStartsAt(), now).abs()))
                .ifPresent(s -> entry.setScheduleId(s.getId()));
        return toView(entryRepository.save(entry), now);
    }

    @Transactional
    public TimeEntryView clockOut(UserPrincipal principal, Integer breakMinutes) {
        TimeEntryEntity entry = entryRepository.findFirstByUserIdAndClockOutAtIsNull(principal.userId())
                .orElseThrow(() -> new ApiException(409, "You are not clocked in"));
        Instant now = Instant.now();
        int breaks = breakMinutes == null ? 0 : breakMinutes;
        if (breaks < 0 || breaks > Duration.between(entry.getClockInAt(), now).toMinutes()) {
            throw new ApiException(400, "Break time is longer than the shift");
        }
        entry.setClockOutAt(now);
        entry.setBreakMinutes(breaks);
        return toView(entryRepository.save(entry), now);
    }

    @Transactional(readOnly = true)
    public TimeEntryView status(UserPrincipal principal) {
        return entryRepository.findFirstByUserIdAndClockOutAtIsNull(principal.userId())
                .map(e -> toView(e, Instant.now()))
                .orElse(null);
    }

    @Transactional
    public TimeEntryView editEntry(UUID id, EntryEditRequest request, UserPrincipal manager) {
        TimeEntryEntity entry = entryRepository.findById(id).orElseThrow(() -> new ApiException(404, "Time entry not found"));
        Instant in = request.clockInAt() != null ? request.clockInAt() : entry.getClockInAt();
        Instant out = request.clockOutAt() != null ? request.clockOutAt() : entry.getClockOutAt();
        if (out != null && !out.isAfter(in)) {
            throw new ApiException(400, "Clock-out must be after clock-in");
        }
        int breaks = request.breakMinutes() != null ? request.breakMinutes() : entry.getBreakMinutes();
        if (breaks < 0 || (out != null && breaks > Duration.between(in, out).toMinutes())) {
            throw new ApiException(400, "Break time is longer than the shift");
        }
        entry.setClockInAt(in);
        entry.setClockOutAt(out);
        entry.setBreakMinutes(breaks);
        entry.setEditedBy(manager.userId());
        entry.setEditReason(request.reason().trim());
        return toView(entryRepository.save(entry), Instant.now());
    }

    // ------------------------------------------------------------ timesheets

    @Transactional(readOnly = true)
    public List<TimesheetRow> timesheet(LocalDate from, LocalDate to, UUID userId) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<TimeEntryEntity> entries = entryRepository.findInRange(start, end, userId);
        List<WorkScheduleEntity> schedules = scheduleRepository.findOverlapping(start, end, userId);
        Map<UUID, WorkScheduleEntity> scheduleById = schedules.stream().collect(Collectors.toMap(WorkScheduleEntity::getId, s -> s));

        Map<UUID, List<TimeEntryEntity>> entriesByUser = entries.stream().collect(Collectors.groupingBy(TimeEntryEntity::getUserId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<WorkScheduleEntity>> schedulesByUser = schedules.stream().collect(Collectors.groupingBy(WorkScheduleEntity::getUserId));
        List<UUID> userIds = new ArrayList<>(entriesByUser.keySet());
        schedulesByUser.keySet().stream().filter(id -> !entriesByUser.containsKey(id)).forEach(userIds::add);
        Map<UUID, UserEntity> users = users(userIds);
        Instant now = Instant.now();

        List<TimesheetRow> rows = new ArrayList<>();
        for (UUID id : userIds) {
            List<TimeEntryEntity> userEntries = entriesByUser.getOrDefault(id, List.of());
            List<WorkScheduleEntity> userSchedules = schedulesByUser.getOrDefault(id, List.of());
            long worked = userEntries.stream().mapToLong(e -> e.workedMinutes(now)).sum();
            long scheduled = userSchedules.stream().mapToLong(WorkScheduleEntity::minutes).sum();
            long late = userEntries.stream()
                    .filter(e -> e.getScheduleId() != null && scheduleById.containsKey(e.getScheduleId()))
                    .filter(e -> lateMinutes(e, scheduleById.get(e.getScheduleId())) > 0)
                    .count();
            Map<LocalDate, Long> perDay = new HashMap<>();
            userEntries.forEach(e -> perDay.merge(e.getClockInAt().atZone(zone).toLocalDate(), e.workedMinutes(now), Long::sum));
            long overtime = perDay.values().stream().mapToLong(m -> Math.max(0, m - DAILY_OVERTIME_THRESHOLD_MINUTES)).sum();
            long missed = userSchedules.stream()
                    .filter(s -> s.getEndsAt().isBefore(now))
                    .filter(s -> userEntries.stream().noneMatch(e -> s.getId().equals(e.getScheduleId())))
                    .count();
            UserEntity user = users.get(id);
            rows.add(new TimesheetRow(id, user == null ? null : user.getName(), user == null ? null : user.getRole().name(),
                    worked, scheduled, overtime, late, missed, userEntries.size(),
                    userEntries.stream().map(e -> toView(e, now, scheduleById.get(e.getScheduleId()))).toList()));
        }
        rows.sort(Comparator.comparing(r -> r.name() == null ? "" : r.name()));
        return rows;
    }

    static long lateMinutes(TimeEntryEntity entry, WorkScheduleEntity schedule) {
        long late = Duration.between(schedule.getStartsAt(), entry.getClockInAt()).toMinutes();
        return late > LATE_GRACE_MINUTES ? late : 0;
    }

    // ------------------------------------------------------------ helpers

    private Map<UUID, UserEntity> users(List<UUID> ids) {
        return userRepository.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));
    }

    private ScheduleView toView(WorkScheduleEntity s, UserEntity user) {
        return new ScheduleView(s.getId(), s.getUserId(), user == null ? null : user.getName(), s.getBranchId(),
                s.getStartsAt(), s.getEndsAt(), s.minutes(), s.getPosition(), s.getNotes());
    }

    private TimeEntryView toView(TimeEntryEntity e, Instant now) {
        return toView(e, now, e.getScheduleId() == null ? null : scheduleRepository.findById(e.getScheduleId()).orElse(null));
    }

    private TimeEntryView toView(TimeEntryEntity e, Instant now, WorkScheduleEntity schedule) {
        return new TimeEntryView(e.getId(), e.getUserId(), e.getScheduleId(), e.getClockInAt(), e.getClockOutAt(), e.getBreakMinutes(),
                e.workedMinutes(now), schedule == null ? 0 : lateMinutes(e, schedule), e.getNotes(), e.getEditReason(),
                e.getClockOutAt() == null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------ DTOs

    public record StaffMember(UUID id, String name, String role, UUID branchId) {}

    public record ScheduleRequest(@jakarta.validation.constraints.NotNull UUID userId, UUID branchId,
                                  @jakarta.validation.constraints.NotNull Instant startsAt,
                                  @jakarta.validation.constraints.NotNull Instant endsAt,
                                  @jakarta.validation.constraints.Size(max = 60) String position,
                                  @jakarta.validation.constraints.Size(max = 500) String notes) {}

    public record ScheduleView(UUID id, UUID userId, String userName, UUID branchId, Instant startsAt, Instant endsAt,
                               long minutes, String position, String notes) {}

    public record EntryEditRequest(Instant clockInAt, Instant clockOutAt, Integer breakMinutes,
                                   @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) {}

    public record TimeEntryView(UUID id, UUID userId, UUID scheduleId, Instant clockInAt, Instant clockOutAt, int breakMinutes,
                                long workedMinutes, long lateMinutes, String notes, String editReason, boolean open) {}

    public record TimesheetRow(UUID userId, String name, String role, long workedMinutes, long scheduledMinutes,
                               long overtimeMinutes, long lateArrivals, long missedShifts, int entryCount,
                               List<TimeEntryView> entries) {}
}
