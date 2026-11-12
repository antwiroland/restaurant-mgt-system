package com.restaurantmanager.core.hr.leave;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER')")
public class LeaveController {
    private final LeaveService leaveService;
    private final LeaveSetupService setup;

    public LeaveController(LeaveService leaveService, LeaveSetupService setup) {
        this.leaveService = leaveService;
        this.setup = setup;
    }

    // ---------------------------------------------------------------- configuration

    @GetMapping("/leave-types")
    public List<LeaveSetupService.TypeView> types(@AuthenticationPrincipal UserPrincipal principal) {
        return setup.types(principal);
    }

    @PostMapping("/leave-types")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveSetupService.TypeView createType(@Valid @RequestBody LeaveSetupService.TypeRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return setup.createType(principal, request);
    }

    @PutMapping("/leave-types/{id}")
    public LeaveSetupService.TypeView updateType(@PathVariable UUID id, @Valid @RequestBody LeaveSetupService.TypeRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return setup.updateType(principal, id, request);
    }

    @GetMapping("/leave-policies")
    public List<LeaveSetupService.PolicyView> policies(@RequestParam(required = false) UUID leaveTypeId,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return setup.policies(principal, leaveTypeId);
    }

    @PostMapping("/leave-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveSetupService.PolicyView addPolicy(@Valid @RequestBody LeaveSetupService.PolicyRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return setup.addPolicy(principal, request);
    }

    // ---------------------------------------------------------------- balances

    @GetMapping("/employees/{id}/leave-balances")
    public List<LeaveService.BalanceView> balances(@PathVariable UUID id, @RequestParam(required = false) Integer year,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.balances(principal, id, year);
    }

    @PostMapping("/employees/{id}/leave-balances/adjust")
    public LeaveService.BalanceView adjust(@PathVariable UUID id, @Valid @RequestBody LeaveService.AdjustRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.adjust(principal, id, request);
    }

    @GetMapping("/leave-balances/{id}/transactions")
    public List<LeaveService.TransactionView> transactions(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.transactions(principal, id);
    }

    // ---------------------------------------------------------------- requests (managers)

    @GetMapping("/leave-requests")
    public List<LeaveService.RequestView> list(@RequestParam(required = false) LeaveStatus status,
                                               @RequestParam(required = false) UUID branchId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "false") boolean awaitingMe,
                                               @RequestParam(required = false) UUID employeeId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.list(principal, status, branchId, from, to, awaitingMe, employeeId);
    }

    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveService.RequestView requestFor(@Valid @RequestBody LeaveService.OnBehalfInput request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.requestFor(principal, request.employeeId(), request.toInput(), request.recordApproved());
    }

    @PostMapping("/leave-requests/quote")
    public LeaveService.Quote quote(@Valid @RequestBody LeaveService.QuoteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        if (request.employeeId() == null) {
            throw new ApiException(400, "employeeId is required");
        }
        return leaveService.quote(principal, request);
    }

    @GetMapping("/leave-requests/{id}")
    public LeaveService.RequestView get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.get(principal, id);
    }

    @PostMapping("/leave-requests/{id}/decision")
    public LeaveService.RequestView decide(@PathVariable UUID id, @Valid @RequestBody LeaveService.Decision decision,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.decide(principal, id, decision);
    }

    @PostMapping("/leave-requests/{id}/cancel")
    public LeaveService.RequestView cancelFor(@PathVariable UUID id, @Valid @RequestBody LeaveService.CancelRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.cancelFor(principal, id, request);
    }

    @PostMapping(value = "/leave-requests/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LeaveService.RequestView attachFor(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.attachDocument(principal, id, bytes(file), file.getOriginalFilename(), false);
    }

    @GetMapping("/leave-calendar")
    public List<LeaveService.RequestView> calendar(@RequestParam UUID branchId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.calendar(principal, branchId, from, to);
    }

    // ---------------------------------------------------------------- self-service

    @GetMapping("/me/leave-balances")
    public List<LeaveService.BalanceView> myBalances(@RequestParam(required = false) Integer year, @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.myBalances(principal, year);
    }

    @GetMapping("/me/leave-requests")
    public List<LeaveService.RequestView> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.mine(principal);
    }

    @PostMapping("/me/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveService.RequestView requestMine(@Valid @RequestBody LeaveService.RequestInput request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.requestMine(principal, request);
    }

    @PostMapping("/me/leave-requests/quote")
    public LeaveService.Quote quoteMine(@Valid @RequestBody LeaveService.QuoteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.quote(principal, new LeaveService.QuoteRequest(null, request.leaveTypeId(), request.startDate(), request.endDate(),
                request.startHalf(), request.endHalf()));
    }

    @PostMapping("/me/leave-requests/{id}/cancel")
    public LeaveService.RequestView cancelMine(@PathVariable UUID id, @Valid @RequestBody(required = false) LeaveService.CancelRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.cancelMine(principal, id, request);
    }

    @PostMapping(value = "/me/leave-requests/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LeaveService.RequestView attachMine(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return leaveService.attachDocument(principal, id, bytes(file), file.getOriginalFilename(), true);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
    }
}
