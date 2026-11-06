package com.restaurantmanager.core.payment.split;

import com.restaurantmanager.core.security.UserPrincipal;
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
@RequestMapping("/orders/{orderId}/splits")
public class SplitController {
    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    /** Splits the bill by items (group orders) or equally; replaces earlier splits until payment starts. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public List<SplitService.SplitView> split(@PathVariable UUID orderId,
                                              @Valid @RequestBody SplitService.SplitRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return splitService.createSplits(orderId, request, principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public List<SplitService.SplitView> list(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return splitService.list(orderId, principal);
    }
}
