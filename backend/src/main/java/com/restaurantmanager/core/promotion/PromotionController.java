package com.restaurantmanager.core.promotion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/promotions")
public class PromotionController {
    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PromoApplyResponse applyPromo(@Valid @RequestBody PromoApplyRequest request) {
        BigDecimal discount = promotionService.applyPromo(
                request.code(), request.discountType(), request.discountValue(), request.minOrderAmount(),
                request.expiresAt(), request.usageLimit(), request.usedCount(), request.active(),
                request.subtotal(), Instant.now());
        return new PromoApplyResponse(discount, request.subtotal().subtract(discount));
    }

    @GetMapping("/codes/validate/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public PromoValidationResponse validatePromo(@PathVariable String code,
                                                 @RequestParam @NotNull BigDecimal subtotal) {
        PromoCode promo = promotionService.validatePromo(code, subtotal, Instant.now());
        return new PromoValidationResponse(promo.getCode(), promo.getDiscountType(), promo.getDiscountValue(), true);
    }

    @PostMapping("/offers/free-items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public FreeItemsResponse freeItems(@Valid @RequestBody FreeItemsRequest request) {
        return new FreeItemsResponse(promotionService.freeItemsFor(request.purchasedQuantity(), request.buyQty(), request.getQty()));
    }

    public record PromoApplyRequest(
            @NotBlank String code,
            @NotNull DiscountType discountType,
            @NotNull BigDecimal discountValue,
            @NotNull BigDecimal minOrderAmount,
            @NotNull Instant expiresAt,
            @Min(1) int usageLimit,
            @Min(0) int usedCount,
            boolean active,
            @NotNull BigDecimal subtotal
    ) {}

    public record PromoApplyResponse(BigDecimal discount, BigDecimal newTotal) {}

    public record PromoValidationResponse(String code, DiscountType discountType, BigDecimal discountValue, boolean valid) {}

    public record FreeItemsRequest(@Min(1) int purchasedQuantity, @Min(1) int buyQty, @Min(1) int getQty) {}

    public record FreeItemsResponse(int freeItems) {}
}
