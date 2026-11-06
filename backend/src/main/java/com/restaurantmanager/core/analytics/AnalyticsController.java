package com.restaurantmanager.core.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private final AnalyticsReportService reportService;

    public AnalyticsController(AnalyticsReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AnalyticsReportService.AnalyticsSummary analyticsSummary(@Valid @RequestBody AnalyticsSummaryRequest request) {
        return reportService.summarize(request.records, request.topItemLimit);
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AnalyticsReportService.RevenueResponse revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "DAY") AnalyticsReportService.RevenuePeriod period) {
        return reportService.revenue(from, to, branchId, period);
    }

    @GetMapping("/top-items")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<com.restaurantmanager.core.analytics.TopItem> topItems(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "10") @Min(1) int limit) {
        return reportService.topItems(from, to, branchId, limit);
    }

    @GetMapping("/peak-hours")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<AnalyticsReportService.PeakHourPoint> peakHours(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return reportService.peakHours(from, to, branchId);
    }

    @GetMapping("/average-order-value")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AnalyticsReportService.AverageOrderValueResponse averageOrderValue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return reportService.averageOrderValue(from, to, branchId);
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AnalyticsReportService.AnalyticsOverview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "DAY") AnalyticsReportService.RevenuePeriod period) {
        return reportService.overview(from, to, branchId, period);
    }

    public record AnalyticsSummaryRequest(@NotNull List<OrderAnalyticsRecord> records, @Min(1) int topItemLimit) {}
}
