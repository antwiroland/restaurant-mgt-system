package com.restaurantmanager.core.billing;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/billing")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** Public: lets checkout screens explain service charge, taxes and tipping. */
    @GetMapping("/settings")
    public BillingService.ConfigView config() {
        return billingService.config();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingService.ConfigView update(@Valid @RequestBody BillingService.SettingsRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return billingService.updateSettings(request, principal.userId());
    }

    @PostMapping("/taxes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BillingService.TaxRateView createTax(@Valid @RequestBody BillingService.TaxRateRequest request) {
        return billingService.saveTax(null, request);
    }

    @PutMapping("/taxes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingService.TaxRateView updateTax(@PathVariable UUID id, @Valid @RequestBody BillingService.TaxRateRequest request) {
        return billingService.saveTax(id, request);
    }

    @DeleteMapping("/taxes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteTax(@PathVariable UUID id) {
        billingService.deleteTax(id);
    }
}
