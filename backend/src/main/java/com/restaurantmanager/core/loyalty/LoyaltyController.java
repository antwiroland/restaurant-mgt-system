package com.restaurantmanager.core.loyalty;

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
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/loyalty")
public class LoyaltyController {
    private final LoyaltyAccountService loyaltyAccountService;

    public LoyaltyController(LoyaltyAccountService loyaltyAccountService) {
        this.loyaltyAccountService = loyaltyAccountService;
    }

    @PostMapping("/accrue")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public LoyaltyAccrueResponse accrue(@Valid @RequestBody LoyaltyAccrueRequest request) {
        int awarded = loyaltyAccountService.accrue(request.customerId(), request.paymentAmount(), request.orderId());
        return new LoyaltyAccrueResponse(awarded, loyaltyAccountService.balance(request.customerId()));
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public LoyaltyRedeemResponse redeem(@Valid @RequestBody LoyaltyRedeemRequest request) {
        var result = loyaltyAccountService.redeem(request.customerId(), request.pointsToRedeem(), request.orderTotal(), request.orderId());
        return new LoyaltyRedeemResponse(result.newTotal(), result.remainingPoints(), result.redeemedPoints());
    }

    @GetMapping("/{customerId}/balance")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public LoyaltyBalanceResponse balance(@PathVariable UUID customerId) {
        return new LoyaltyBalanceResponse(loyaltyAccountService.balance(customerId));
    }

    @GetMapping("/{customerId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public List<LoyaltyTransaction> history(@PathVariable UUID customerId) {
        return loyaltyAccountService.history(customerId);
    }

    public record LoyaltyAccrueRequest(@NotNull UUID customerId, @NotNull BigDecimal paymentAmount, @NotBlank String orderId) {}

    public record LoyaltyAccrueResponse(int pointsAwarded, int currentBalance) {}

    public record LoyaltyRedeemRequest(@NotNull UUID customerId, @Min(1) int pointsToRedeem,
                                       @NotNull BigDecimal orderTotal, @NotBlank String orderId) {}

    public record LoyaltyRedeemResponse(BigDecimal newTotal, int remainingPoints, int redeemedPoints) {}

    public record LoyaltyBalanceResponse(int points) {}
}
