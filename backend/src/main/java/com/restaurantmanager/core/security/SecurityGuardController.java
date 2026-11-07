package com.restaurantmanager.core.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/security")
public class SecurityGuardController {
    private final SecurityGuardService securityGuardService;

    public SecurityGuardController(SecurityGuardService securityGuardService) {
        this.securityGuardService = securityGuardService;
    }

    @PostMapping("/sanitize-search")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public SecurityStringResponse sanitizeSearch(@Valid @RequestBody SecurityStringRequest request) {
        return new SecurityStringResponse(securityGuardService.sanitizeSearch(request.value()));
    }

    @PostMapping("/escape-notes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public SecurityStringResponse escapeNotes(@Valid @RequestBody SecurityStringRequest request) {
        return new SecurityStringResponse(securityGuardService.escapeReceiptNotes(request.value()));
    }

    @GetMapping("/order-access/{ownerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public OrderAccessResponse canAccess(@PathVariable UUID ownerId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return new OrderAccessResponse(securityGuardService.canCustomerAccessOrder(principal.userId(), ownerId));
    }

    public record SecurityStringRequest(@NotBlank String value) {}

    public record SecurityStringResponse(String value) {}

    public record OrderAccessResponse(boolean allowed) {}
}
