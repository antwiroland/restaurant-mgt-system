package com.restaurantmanager.core.phase8.promo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/phase8/promo-codes")
public class PromoCodeManagementController {
    private final PromoCodeManagementService promoCodeManagementService;

    public PromoCodeManagementController(PromoCodeManagementService promoCodeManagementService) {
        this.promoCodeManagementService = promoCodeManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<PromoCodeManagementService.PromoCodeResponse> listPromoCodes() {
        return promoCodeManagementService.listPromoCodes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PromoCodeManagementService.PromoCodeResponse createPromoCode(
            @Valid @RequestBody PromoCodeManagementService.PromoCodeUpsertRequest request
    ) {
        return promoCodeManagementService.createPromoCode(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PromoCodeManagementService.PromoCodeResponse updatePromoCode(
            @PathVariable UUID id,
            @Valid @RequestBody PromoCodeManagementService.PromoCodeUpsertRequest request
    ) {
        return promoCodeManagementService.updatePromoCode(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PromoCodeManagementService.PromoCodeResponse updatePromoCodeStatus(
            @PathVariable UUID id,
            @RequestBody PromoCodeManagementService.PromoCodeStatusRequest request
    ) {
        return promoCodeManagementService.updatePromoCodeStatus(id, request.active());
    }
}
