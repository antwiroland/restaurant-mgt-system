package com.restaurantmanager.core.hr.timesheet;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class TimesheetController {
    private final TimesheetService timesheetService;
    private final OvertimeSettingsService settings;

    public TimesheetController(TimesheetService timesheetService, OvertimeSettingsService settings) {
        this.timesheetService = timesheetService;
        this.settings = settings;
    }

    // ---------------------------------------------------------------- timesheets

    @GetMapping("/timesheets")
    public List<TimesheetService.TimesheetSummary> list(@RequestParam UUID branchId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) TimesheetStatus status,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.list(principal, branchId, from, to, status);
    }

    @PostMapping("/timesheets/generate")
    public TimesheetService.GenerateResult generate(@Valid @RequestBody TimesheetService.GenerateRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.generate(principal, request);
    }

    @GetMapping("/timesheets/{id}")
    public TimesheetService.TimesheetDetail get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.get(principal, id);
    }

    @PostMapping("/timesheets/{id}/refresh")
    public TimesheetService.TimesheetDetail refresh(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.refresh(principal, id);
    }

    @PostMapping("/timesheets/{id}/submit")
    public TimesheetService.TimesheetDetail submit(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.submit(principal, id);
    }

    @PostMapping("/timesheets/{id}/review")
    public TimesheetService.TimesheetDetail review(@PathVariable UUID id, @Valid @RequestBody(required = false) TimesheetService.NoteRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.review(principal, id, request);
    }

    @PostMapping("/timesheets/{id}/approve")
    public TimesheetService.TimesheetDetail approve(@PathVariable UUID id, @Valid @RequestBody TimesheetService.ApproveRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.approve(principal, id, request);
    }

    @PostMapping("/timesheets/{id}/reopen")
    public TimesheetService.TimesheetDetail reopen(@PathVariable UUID id, @Valid @RequestBody TimesheetService.ReasonRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.reopen(principal, id, request);
    }

    @PostMapping("/timesheets/{id}/lock")
    public TimesheetService.TimesheetDetail lock(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.lock(principal, id);
    }

    @GetMapping("/me/timesheets")
    public List<TimesheetService.TimesheetSummary> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return timesheetService.mine(principal);
    }

    // ---------------------------------------------------------------- overtime rules, holidays, requests

    @GetMapping("/overtime-rules")
    public List<OvertimeSettingsService.RuleView> rules(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return settings.rules(principal, branchId);
    }

    @PostMapping("/overtime-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeSettingsService.RuleView addRule(@Valid @RequestBody OvertimeSettingsService.RuleRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return settings.addRule(principal, request);
    }

    @GetMapping("/holidays")
    public List<OvertimeSettingsService.HolidayView> holidays(@RequestParam int year, @AuthenticationPrincipal UserPrincipal principal) {
        return settings.holidays(principal, year);
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeSettingsService.HolidayView addHoliday(@Valid @RequestBody OvertimeSettingsService.HolidayRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return settings.addHoliday(principal, request);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        settings.deleteHoliday(principal, id);
    }

    @GetMapping("/overtime-requests")
    public List<OvertimeSettingsService.OvertimeRequestView> overtimeRequests(@RequestParam(required = false) OvertimeRequestStatus status,
                                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return settings.requests(principal, status);
    }

    @PostMapping("/overtime-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeSettingsService.OvertimeRequestView requestOvertime(@Valid @RequestBody OvertimeSettingsService.OvertimeRequestInput request,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return settings.request(principal, request);
    }

    @PostMapping("/overtime-requests/{id}/decision")
    public OvertimeSettingsService.OvertimeRequestView decideOvertime(@PathVariable UUID id, @Valid @RequestBody OvertimeSettingsService.Decision decision,
                                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return settings.decide(principal, id, decision);
    }
}
