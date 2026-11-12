package com.restaurantmanager.core.hr.attendance;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Attendance API: POS staff clock, manager views and corrections, self-service. The legacy /timeclock API stays available. */
@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class AttendanceController {
    private final PosClockService posService;
    private final AttendanceManagementService managementService;
    private final AttendancePolicyService policyService;

    public AttendanceController(PosClockService posService, AttendanceManagementService managementService, AttendancePolicyService policyService) {
        this.posService = posService;
        this.managementService = managementService;
        this.policyService = policyService;
    }

    // ---------------------------------------------------------------- POS terminal

    @GetMapping("/pos/roster")
    public List<PosClockService.RosterEntry> roster(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return posService.roster(principal, branchId);
    }

    @PostMapping("/pos/clock")
    public AttendanceService.ClockResult posClock(@Valid @RequestBody PosClockService.PosClockRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return posService.clock(principal, request);
    }

    @PostMapping("/pos/me")
    public PosClockService.PosSummary posMe(@Valid @RequestBody PosClockService.PosIdentifyRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return posService.summary(principal, request);
    }

    // ---------------------------------------------------------------- managers

    @GetMapping("/attendance/day")
    public AttendanceManagementService.DayView day(@RequestParam UUID branchId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.day(principal, branchId, date);
    }

    @GetMapping("/attendance/entries")
    public List<AttendanceService.EntryView> entries(@RequestParam UUID branchId,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) UUID employeeId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.entries(principal, branchId, from, to, employeeId);
    }

    @PostMapping("/attendance/clock")
    public AttendanceService.ClockResult clockFor(@Valid @RequestBody AttendanceManagementService.ManagerClockRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.clockFor(principal, request);
    }

    @GetMapping("/attendance/corrections")
    public List<AttendanceManagementService.CorrectionView> corrections(@RequestParam(required = false) CorrectionStatus status,
                                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.corrections(principal, status);
    }

    @PostMapping("/attendance/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceManagementService.CorrectionView requestCorrection(@Valid @RequestBody AttendanceManagementService.CorrectionRequest request,
                                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.requestCorrection(principal, request);
    }

    @PostMapping("/attendance/corrections/{id}/decision")
    public AttendanceManagementService.CorrectionView decide(@PathVariable UUID id, @Valid @RequestBody AttendanceManagementService.CorrectionDecision decision,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.decide(principal, id, decision);
    }

    @GetMapping("/attendance-policy")
    public AttendanceCalculator.Policy policy(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return policyService.get(principal, branchId);
    }

    @PutMapping("/attendance-policy")
    public AttendanceCalculator.Policy savePolicy(@Valid @RequestBody AttendancePolicyService.PolicyRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return policyService.save(principal, request);
    }

    @GetMapping("/employees/{employeeId}/clock-pin")
    public AttendanceManagementService.PinStatus pinStatus(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.pinStatus(principal, employeeId);
    }

    @PutMapping("/employees/{employeeId}/clock-pin")
    public AttendanceManagementService.PinStatus setPin(@PathVariable UUID employeeId, @Valid @RequestBody AttendanceManagementService.PinRequest request,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.setPin(principal, employeeId, request.pin());
    }

    @DeleteMapping("/employees/{employeeId}/clock-pin")
    public AttendanceManagementService.PinStatus clearPin(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.clearPin(principal, employeeId);
    }

    // ---------------------------------------------------------------- self-service

    @GetMapping("/me/attendance/status")
    public ResponseEntity<AttendanceService.EntryView> myStatus(@AuthenticationPrincipal UserPrincipal principal) {
        AttendanceService.EntryView view = managementService.myStatus(principal);
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    @GetMapping("/me/attendance")
    public List<AttendanceService.EntryView> myHistory(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.myHistory(principal, from, to);
    }

    @PostMapping("/me/clock")
    public AttendanceService.ClockResult selfClock(@Valid @RequestBody AttendanceManagementService.SelfClockRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.selfClock(principal, request);
    }

    @GetMapping("/me/clock-pin")
    public AttendanceManagementService.PinStatus myPin(@AuthenticationPrincipal UserPrincipal principal) {
        return managementService.myPin(principal);
    }

    @PutMapping("/me/clock-pin")
    public AttendanceManagementService.PinStatus setMyPin(@Valid @RequestBody AttendanceManagementService.MyPinRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return managementService.setMyPin(principal, request);
    }
}
