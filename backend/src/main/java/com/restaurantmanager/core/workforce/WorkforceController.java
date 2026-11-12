package com.restaurantmanager.core.workforce;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class WorkforceController {
    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER')";

    private final WorkforceService workforceService;

    public WorkforceController(WorkforceService workforceService) {
        this.workforceService = workforceService;
    }

    @GetMapping("/scheduling/shifts")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<WorkforceService.ScheduleView> shifts(@RequestParam Instant from, @RequestParam Instant to,
                                                      @RequestParam(required = false) UUID userId) {
        return workforceService.schedules(from, to, userId);
    }

    @GetMapping("/scheduling/staff")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR')")
    public List<WorkforceService.StaffMember> staff() {
        return workforceService.staff();
    }

    @GetMapping("/scheduling/my-shifts")
    @PreAuthorize(STAFF)
    public List<WorkforceService.ScheduleView> myShifts(@RequestParam Instant from, @RequestParam Instant to,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.schedules(from, to, principal.userId());
    }

    @PostMapping("/scheduling/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkforceService.ScheduleView create(@Valid @RequestBody WorkforceService.ScheduleRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.saveSchedule(null, request, principal);
    }

    @PutMapping("/scheduling/shifts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkforceService.ScheduleView update(@PathVariable UUID id, @Valid @RequestBody WorkforceService.ScheduleRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.saveSchedule(id, request, principal);
    }

    @DeleteMapping("/scheduling/shifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable UUID id) {
        workforceService.deleteSchedule(id);
    }

    @PostMapping("/timeclock/clock-in")
    @PreAuthorize(STAFF)
    public WorkforceService.TimeEntryView clockIn(@RequestBody(required = false) ClockRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.clockIn(principal, request == null ? null : request.notes());
    }

    @PostMapping("/timeclock/clock-out")
    @PreAuthorize(STAFF)
    public WorkforceService.TimeEntryView clockOut(@RequestBody(required = false) ClockRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.clockOut(principal, request == null ? null : request.breakMinutes());
    }

    @GetMapping("/timeclock/status")
    @PreAuthorize(STAFF)
    public ResponseEntity<WorkforceService.TimeEntryView> status(@AuthenticationPrincipal UserPrincipal principal) {
        WorkforceService.TimeEntryView entry = workforceService.status(principal);
        return entry == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(entry);
    }

    @PutMapping("/timeclock/entries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkforceService.TimeEntryView edit(@PathVariable UUID id, @Valid @RequestBody WorkforceService.EntryEditRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.editEntry(id, request, principal);
    }

    @GetMapping("/timeclock/timesheet")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<WorkforceService.TimesheetRow> timesheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId) {
        return workforceService.timesheet(from, to, userId);
    }

    @GetMapping("/timeclock/my-timesheet")
    @PreAuthorize(STAFF)
    public List<WorkforceService.TimesheetRow> myTimesheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return workforceService.timesheet(from, to, principal.userId());
    }

    public record ClockRequest(String notes, Integer breakMinutes) {}
}
