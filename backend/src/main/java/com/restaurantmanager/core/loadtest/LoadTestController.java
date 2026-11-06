package com.restaurantmanager.core.loadtest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/load-tests")
public class LoadTestController {
    private final LoadTestService loadTestService = new LoadTestService();

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public LoadReport run(@Valid @RequestBody LoadRunRequest request) {
        long orderLatencyMs = loadTestService.concurrentOrderCreationLatencyMs(request.concurrentRequests());
        long paymentLatencyMs = loadTestService.concurrentPaymentInitiationLatencyMs(request.concurrentRequests());
        int deliveredEvents = loadTestService.broadcastOrderEvent(request.subscribers());
        return new LoadReport(orderLatencyMs, paymentLatencyMs, deliveredEvents);
    }

    public record LoadRunRequest(@Min(1) int concurrentRequests, @Min(1) int subscribers) {}

    public record LoadReport(long orderLatencyMs, long paymentLatencyMs, int deliveredEvents) {}
}
