package com.restaurantmanager.core.phase10.runtime;

import com.restaurantmanager.core.phase10.analytics.AnalyticsService;
import com.restaurantmanager.core.phase10.analytics.OrderAnalyticsRecord;
import com.restaurantmanager.core.phase10.analytics.TopItem;
import com.restaurantmanager.core.phase10.load.LoadTestService;
import com.restaurantmanager.core.phase10.security.SecurityGuardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class Phase10RuntimeService {
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final LoadTestService loadTestService = new LoadTestService();
    private final SecurityGuardService securityGuardService;

    public Phase10RuntimeService(SecurityGuardService securityGuardService) {
        this.securityGuardService = securityGuardService;
    }

    public AnalyticsSummary summarize(List<OrderAnalyticsRecord> records, int topItemLimit) {
        BigDecimal dailyRevenue = analyticsService.dailyRevenue(records);
        List<TopItem> topItems = analyticsService.topItems(records, topItemLimit);
        int peakHour = analyticsService.peakHour(records);
        int repeatCustomers = analyticsService.repeatCustomers(records);
        return new AnalyticsSummary(dailyRevenue, topItems, peakHour, repeatCustomers);
    }

    public LoadReport runLoad(int concurrentRequests, int subscribers) {
        long orderLatencyMs = loadTestService.concurrentOrderCreationLatencyMs(concurrentRequests);
        long paymentLatencyMs = loadTestService.concurrentPaymentInitiationLatencyMs(concurrentRequests);
        int deliveredEvents = loadTestService.broadcastOrderEvent(subscribers);
        return new LoadReport(orderLatencyMs, paymentLatencyMs, deliveredEvents);
    }

    public String sanitizeSearch(String input) {
        return securityGuardService.sanitizeSearch(input);
    }

    public String escapeReceiptNotes(String notes) {
        return securityGuardService.escapeReceiptNotes(notes);
    }

    public boolean canCustomerAccessOrder(UUID requesterId, UUID orderOwnerId) {
        return securityGuardService.canCustomerAccessOrder(requesterId, orderOwnerId);
    }

    public record AnalyticsSummary(BigDecimal dailyRevenue, List<TopItem> topItems, int peakHour, int repeatCustomers) {}

    public record LoadReport(long orderLatencyMs, long paymentLatencyMs, int deliveredEvents) {}
}
