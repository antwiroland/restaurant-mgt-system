package com.restaurantmanager.core.hr.schedule;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HR scheduling API. The legacy /scheduling and /timeclock endpoints keep working alongside it. */
@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class ScheduleController {
    private final ScheduleService scheduleService;
    private final SchedulingSetupService setupService;
    private final AvailabilityService availabilityService;
    private final SwapService swapService;

    public ScheduleController(ScheduleService scheduleService, SchedulingSetupService setupService,
                              AvailabilityService availabilityService, SwapService swapService) {
        this.scheduleService = scheduleService;
        this.setupService = setupService;
        this.availabilityService = availabilityService;
        this.swapService = swapService;
    }

    // ---------------------------------------------------------------- board & shifts

    @GetMapping("/schedules")
    public ScheduleService.BoardView board(@RequestParam UUID branchId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.board(principal, branchId, from, to);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleService.SaveResult create(@Valid @RequestBody ScheduleService.ShiftRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.save(principal, null, request);
    }

    @PutMapping("/schedules/{id}")
    public ScheduleService.SaveResult update(@PathVariable UUID id, @Valid @RequestBody ScheduleService.ShiftRequest request,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.save(principal, id, request);
    }

    @PostMapping("/schedules/{id}/cancel")
    public ScheduleService.ShiftView cancel(@PathVariable UUID id, @Valid @RequestBody ScheduleService.CancelRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.cancel(principal, id, request);
    }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        scheduleService.deleteDraft(principal, id);
    }

    @PostMapping("/schedules/publish")
    public ScheduleService.PublishResult publish(@Valid @RequestBody ScheduleService.PublishRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.publish(principal, request);
    }

    // ---------------------------------------------------------------- templates & policy

    @GetMapping("/shift-templates")
    public List<SchedulingSetupService.TemplateView> templates(@RequestParam(required = false) UUID branchId,
                                                               @RequestParam(defaultValue = "false") boolean includeArchived,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.templates(principal, branchId, includeArchived);
    }

    @PostMapping("/shift-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingSetupService.TemplateView createTemplate(@Valid @RequestBody SchedulingSetupService.TemplateRequest request,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.saveTemplate(principal, null, request);
    }

    @PutMapping("/shift-templates/{id}")
    public SchedulingSetupService.TemplateView updateTemplate(@PathVariable UUID id, @Valid @RequestBody SchedulingSetupService.TemplateRequest request,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.saveTemplate(principal, id, request);
    }

    @PostMapping("/shift-templates/{id}/archive")
    public SchedulingSetupService.TemplateView archiveTemplate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.setTemplateActive(principal, id, false);
    }

    @PostMapping("/shift-templates/{id}/restore")
    public SchedulingSetupService.TemplateView restoreTemplate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.setTemplateActive(principal, id, true);
    }

    @GetMapping("/scheduling-policy")
    public ScheduleRules.Policy policy(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.policy(principal, branchId);
    }

    @PutMapping("/scheduling-policy")
    public ScheduleRules.Policy savePolicy(@Valid @RequestBody SchedulingSetupService.PolicyRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return setupService.savePolicy(principal, request);
    }

    // ---------------------------------------------------------------- availability

    @GetMapping("/employees/{employeeId}/availability")
    public List<AvailabilityService.AvailabilityView> availability(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        return availabilityService.forEmployee(principal, employeeId);
    }

    @PostMapping("/employees/{employeeId}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityService.AvailabilityView addAvailability(@PathVariable UUID employeeId,
                                                                @Valid @RequestBody AvailabilityService.AvailabilityRequest request,
                                                                @AuthenticationPrincipal UserPrincipal principal) {
        return availabilityService.addForEmployee(principal, employeeId, request);
    }

    @DeleteMapping("/availability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.deleteForEmployee(principal, id);
    }

    // ---------------------------------------------------------------- swaps (managers)

    @GetMapping("/shift-swaps")
    public List<SwapService.SwapView> swaps(@RequestParam(required = false) SwapStatus status, @AuthenticationPrincipal UserPrincipal principal) {
        return swapService.list(principal, status);
    }

    @PostMapping("/shift-swaps/{id}/decision")
    public SwapService.SwapView decide(@PathVariable UUID id, @Valid @RequestBody SwapService.DecisionRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return swapService.decide(principal, id, request);
    }

    // ---------------------------------------------------------------- self-service

    @GetMapping("/me/shifts")
    public List<ScheduleService.ShiftView> myShifts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return scheduleService.myShifts(principal, from, to);
    }

    @GetMapping("/me/availability")
    public List<AvailabilityService.AvailabilityView> myAvailability(@AuthenticationPrincipal UserPrincipal principal) {
        return availabilityService.mine(principal);
    }

    @PostMapping("/me/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityService.AvailabilityView addMyAvailability(@Valid @RequestBody AvailabilityService.AvailabilityRequest request,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return availabilityService.addMine(principal, request);
    }

    @DeleteMapping("/me/availability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAvailability(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.deleteMine(principal, id);
    }

    @GetMapping("/me/swaps")
    public List<SwapService.SwapView> mySwaps(@AuthenticationPrincipal UserPrincipal principal) {
        return swapService.mine(principal);
    }

    @GetMapping("/me/swap-options")
    public List<SwapService.SwapOption> swapOptions(@RequestParam UUID scheduleId, @AuthenticationPrincipal UserPrincipal principal) {
        return swapService.options(principal, scheduleId);
    }

    @PostMapping("/me/swaps")
    @ResponseStatus(HttpStatus.CREATED)
    public SwapService.SwapView requestSwap(@Valid @RequestBody SwapService.SwapRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return swapService.request(principal, request);
    }

    @PostMapping("/me/swaps/{id}/cancel")
    public SwapService.SwapView cancelSwap(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return swapService.cancel(principal, id);
    }
}
