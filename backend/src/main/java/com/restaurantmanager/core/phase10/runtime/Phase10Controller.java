package com.restaurantmanager.core.phase10.runtime;

import com.restaurantmanager.core.phase10.analytics.OrderAnalyticsRecord;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/phase10")
public class Phase10Controller {
    private final Phase10RuntimeService runtimeService;

    public Phase10Controller(Phase10RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @PostMapping("/analytics/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Phase10RuntimeService.AnalyticsSummary analyticsSummary(@Valid @RequestBody AnalyticsSummaryRequest request) {
        return runtimeService.summarize(request.records, request.topItemLimit);
    }

    @PostMapping("/load/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Phase10RuntimeService.LoadReport runLoad(@Valid @RequestBody LoadRunRequest request) {
        return runtimeService.runLoad(request.concurrentRequests, request.subscribers);
    }

    @PostMapping("/security/sanitize-search")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public SecurityStringResponse sanitizeSearch(@Valid @RequestBody SecurityStringRequest request) {
        return new SecurityStringResponse(runtimeService.sanitizeSearch(request.value));
    }

    @PostMapping("/security/escape-notes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public SecurityStringResponse escapeNotes(@Valid @RequestBody SecurityStringRequest request) {
        return new SecurityStringResponse(runtimeService.escapeReceiptNotes(request.value));
    }

    @GetMapping("/security/order-access/{ownerId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER')")
    public OrderAccessResponse canAccess(@PathVariable UUID ownerId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return new OrderAccessResponse(runtimeService.canCustomerAccessOrder(principal.userId(), ownerId));
    }

    public record AnalyticsSummaryRequest(@NotNull List<OrderAnalyticsRecord> records, @Min(1) int topItemLimit) {}

    public record LoadRunRequest(@Min(1) int concurrentRequests, @Min(1) int subscribers) {}

    public record SecurityStringRequest(@NotBlank String value) {}

    public record SecurityStringResponse(String value) {}

    public record OrderAccessResponse(boolean allowed) {}
}
