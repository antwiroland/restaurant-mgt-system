package com.restaurantmanager.core.phase9.runtime;

import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.phase9.audit.FinancialAuditEvent;
import com.restaurantmanager.core.phase9.discount.DiscountMode;
import com.restaurantmanager.core.phase9.discount.DiscountResult;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationEntry;
import com.restaurantmanager.core.phase9.reconciliation.ReconciliationSummary;
import com.restaurantmanager.core.phase9.refund.PaymentRecord;
import com.restaurantmanager.core.phase9.voiding.OrderChannel;
import com.restaurantmanager.core.phase9.voiding.TableLinkStatus;
import com.restaurantmanager.core.phase9.voiding.VoidableOrder;
import com.restaurantmanager.core.phase9.voiding.VoidableOrderStatus;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/phase9")
public class Phase9Controller {
    private final Phase9RuntimeService runtimeService;

    public Phase9Controller(Phase9RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/discount/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiscountResult applyDiscount(@Valid @RequestBody DiscountRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return runtimeService.applyDiscount(request.total, request.mode, request.value, request.overrideToken, principal.userId());
    }

    @PostMapping("/refund/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public PaymentRecord applyRefund(@Valid @RequestBody RefundRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return runtimeService.refund(request.paymentId, request.paidAmount, request.amount, request.overrideToken, principal.userId());
    }

    @PostMapping("/void/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public VoidableOrder applyVoid(@Valid @RequestBody VoidRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return runtimeService.voidOrder(
                request.orderId,
                request.channel,
                request.status,
                request.tableStatus,
                request.overrideToken,
                principal.userId()
        );
    }

    @PostMapping("/reconciliation/summarize")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ReconciliationSummary summarize(@Valid @RequestBody ReconciliationSummaryRequest request) {
        return runtimeService.summarize(request.businessDate, request.entries);
    }

    @PostMapping("/reconciliation/{businessDate}/sign-off")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ReconciliationSummary signOff(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
                                         @Valid @RequestBody SignOffRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return runtimeService.signOff(businessDate, request.overrideToken, principal.userId());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<FinancialAuditEvent> audit(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestParam(required = false) AuditAction action,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to) {
        return runtimeService.queryAudit(principal.role(), action, from, to);
    }

    public record DiscountRequest(
            @NotNull BigDecimal total,
            @NotNull DiscountMode mode,
            @NotNull BigDecimal value,
            @NotBlank String overrideToken
    ) {}

    public record RefundRequest(
            @NotNull UUID paymentId,
            @NotNull BigDecimal paidAmount,
            @NotNull BigDecimal amount,
            @NotBlank String overrideToken
    ) {}

    public record VoidRequest(
            @NotNull UUID orderId,
            @NotNull OrderChannel channel,
            @NotNull VoidableOrderStatus status,
            @NotNull TableLinkStatus tableStatus,
            @NotBlank String overrideToken
    ) {}

    public record ReconciliationSummaryRequest(
            @NotNull LocalDate businessDate,
            @NotNull List<ReconciliationEntry> entries
    ) {}

    public record SignOffRequest(@NotBlank String overrideToken) {}
}
