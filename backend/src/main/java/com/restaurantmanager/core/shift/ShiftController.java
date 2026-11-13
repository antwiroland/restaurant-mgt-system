package com.restaurantmanager.core.shift;

import com.restaurantmanager.core.common.Pagination;
import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.shift.dto.ShiftCloseRequest;
import com.restaurantmanager.core.shift.dto.ShiftOpenRequest;
import com.restaurantmanager.core.shift.dto.ShiftResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shifts")
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ShiftResponse open(@AuthenticationPrincipal UserPrincipal principal,
                              @Valid @RequestBody ShiftOpenRequest request) {
        return shiftService.openShift(principal, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ShiftResponse close(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID id,
                               @Valid @RequestBody ShiftCloseRequest request) {
        return shiftService.closeShift(principal, id, request);
    }

    /** Record cash put into or taken out of an open shift's drawer (float top-up, petty cash, bank drop). */
    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public com.restaurantmanager.core.shift.dto.ShiftDetailResponse addMovement(@AuthenticationPrincipal UserPrincipal principal,
                                                                                @PathVariable UUID id,
                                                                                @Valid @RequestBody com.restaurantmanager.core.shift.dto.CashMovementRequest request) {
        return shiftService.addMovement(principal, id, request);
    }

    /** Shifts opened in a period (default the last 30 days), newest first, with their figures. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<ShiftResponse> history(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) java.time.Instant from,
                                       @RequestParam(required = false) java.time.Instant to,
                                       @RequestParam(required = false) UUID branchId,
                                       @RequestParam(required = false) UUID cashierId) {
        return shiftService.history(principal, from, to, branchId, cashierId);
    }

    /** One shift with its drawer breakdown: cash sales, cash refunds, pay-ins, pay-outs and expected cash. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public com.restaurantmanager.core.shift.dto.ShiftDetailResponse detail(@AuthenticationPrincipal UserPrincipal principal,
                                                                           @PathVariable UUID id) {
        return shiftService.detail(principal, id);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<List<ShiftResponse>> active(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        List<ShiftResponse> all = shiftService.activeShifts(principal);
        Pagination.Params params = Pagination.from(page, size);
        List<ShiftResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }
}
