package com.restaurantmanager.core.shift;

import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.shift.dto.ShiftCloseRequest;
import com.restaurantmanager.core.shift.dto.ShiftOpenRequest;
import com.restaurantmanager.core.shift.dto.ShiftResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<ShiftResponse> active() {
        return shiftService.activeShifts();
    }
}
