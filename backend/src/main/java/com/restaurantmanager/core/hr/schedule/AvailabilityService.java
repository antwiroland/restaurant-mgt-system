package com.restaurantmanager.core.hr.schedule;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.hr.access.HrAccessService;
import com.restaurantmanager.core.hr.access.HrAccessService.HrAccess;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.common.HrAuditService;
import com.restaurantmanager.core.hr.employee.EmployeeEntity;
import com.restaurantmanager.core.hr.employee.EmployeeRepository;
import com.restaurantmanager.core.hr.employee.EmployeeService;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Employee availability: staff maintain their own; schedulers can view and record it for their branches. */
@Service
public class AvailabilityService {
    private final AvailabilityRepository repository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final HrAccessService accessService;
    private final HrAuditService audit;

    public AvailabilityService(AvailabilityRepository repository, EmployeeRepository employeeRepository, EmployeeService employeeService,
                               HrAccessService accessService, HrAuditService audit) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.accessService = accessService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> mine(UserPrincipal principal) {
        return list(me(principal).getId());
    }

    @Transactional
    public AvailabilityView addMine(UserPrincipal principal, AvailabilityRequest request) {
        return add(me(principal).getId(), request, principal.userId());
    }

    @Transactional
    public void deleteMine(UserPrincipal principal, UUID id) {
        EmployeeEntity me = me(principal);
        AvailabilityEntity entry = repository.findById(id).filter(a -> a.getEmployeeId().equals(me.getId()))
                .orElseThrow(() -> new ApiException(404, "Availability entry not found"));
        delete(entry, principal.userId());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> forEmployee(UserPrincipal principal, UUID employeeId) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_VIEW);
        employeeService.loadVisible(employeeId, access);
        return list(employeeId);
    }

    @Transactional
    public AvailabilityView addForEmployee(UserPrincipal principal, UUID employeeId, AvailabilityRequest request) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        employeeService.loadVisible(employeeId, access);
        return add(employeeId, request, access.userId());
    }

    @Transactional
    public void deleteForEmployee(UserPrincipal principal, UUID id) {
        HrAccess access = accessService.require(principal, HrPermission.SCHEDULE_MANAGE);
        AvailabilityEntity entry = repository.findById(id).orElseThrow(() -> new ApiException(404, "Availability entry not found"));
        try {
            employeeService.loadVisible(entry.getEmployeeId(), access);
        } catch (ApiException ex) {
            throw new ApiException(404, "Availability entry not found");
        }
        delete(entry, access.userId());
    }

    private List<AvailabilityView> list(UUID employeeId) {
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(AvailabilityService::toView).toList();
    }

    private AvailabilityView add(UUID employeeId, AvailabilityRequest r, UUID actorId) {
        if ((r.dayOfWeek() == null || r.dayOfWeek().isBlank()) == (r.specificDate() == null)) {
            throw new ApiException(400, "Choose either a weekday (repeats weekly) or a specific date");
        }
        if ((r.startTime() == null) != (r.endTime() == null)) {
            throw new ApiException(400, "Give both a start and end time, or neither for the whole day");
        }
        if (r.startTime() != null && r.startTime().equals(r.endTime())) {
            throw new ApiException(400, "Start and end time cannot be the same");
        }
        if (r.effectiveFrom() != null && r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom())) {
            throw new ApiException(400, "The end date is before the start date");
        }
        AvailabilityEntity entry = new AvailabilityEntity();
        entry.setEmployeeId(employeeId);
        entry.setKind(r.kind());
        if (r.specificDate() != null) {
            entry.setSpecificDate(r.specificDate());
        } else {
            String day = r.dayOfWeek().trim().toUpperCase(Locale.ROOT);
            try {
                ShiftTemplateEntity.parseDay(day);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(400, "Unknown day " + r.dayOfWeek());
            }
            entry.setDayOfWeek(day);
            entry.setEffectiveFrom(r.effectiveFrom());
            entry.setEffectiveTo(r.effectiveTo());
        }
        entry.setStartTime(r.startTime());
        entry.setEndTime(r.endTime());
        entry.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        entry.setCreatedBy(actorId);
        AvailabilityEntity saved = repository.save(entry);
        audit.log(actorId, AuditAction.HR_AVAILABILITY_CHANGED, "Employee", employeeId,
                Map.of("operation", "added", "kind", saved.getKind().name(), "availabilityId", saved.getId().toString()));
        return toView(saved);
    }

    private void delete(AvailabilityEntity entry, UUID actorId) {
        repository.delete(entry);
        audit.log(actorId, AuditAction.HR_AVAILABILITY_CHANGED, "Employee", entry.getEmployeeId(),
                Map.of("operation", "removed", "availabilityId", entry.getId().toString()));
    }

    private EmployeeEntity me(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.userId())
                .orElseThrow(() -> new ApiException(404, "No employee record is linked to your account"));
    }

    private static AvailabilityView toView(AvailabilityEntity a) {
        return new AvailabilityView(a.getId(), a.getEmployeeId(), a.getKind(), a.getDayOfWeek(), a.getSpecificDate(), a.getStartTime(),
                a.getEndTime(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getNote(), a.getCreatedAt());
    }

    public record AvailabilityRequest(@NotNull AvailabilityKind kind, String dayOfWeek, LocalDate specificDate, LocalTime startTime,
                                      LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo, @Size(max = 300) String note) {}

    public record AvailabilityView(UUID id, UUID employeeId, AvailabilityKind kind, String dayOfWeek, LocalDate specificDate,
                                   LocalTime startTime, LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo, String note,
                                   Instant createdAt) {}
}
