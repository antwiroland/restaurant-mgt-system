package com.restaurantmanager.core.reporting;

import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/sales")
    public ResponseEntity<byte[]> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) UUID branchId,
                                        @RequestParam(defaultValue = "CSV") ReportService.Format format,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return download(reportService.sales(from, to, branchId, principal, format));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<byte[]> reconciliation(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                 @RequestParam(required = false) UUID branchId,
                                                 @RequestParam(defaultValue = "CSV") ReportService.Format format,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return download(reportService.reconciliation(date, branchId, principal, format));
    }

    @GetMapping("/audit")
    public ResponseEntity<byte[]> audit(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "CSV") ReportService.Format format) {
        return download(reportService.audit(from, to, format));
    }

    @GetMapping("/timesheet")
    public ResponseEntity<byte[]> timesheet(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "CSV") ReportService.Format format) {
        return download(reportService.timesheet(from, to, format));
    }

    private ResponseEntity<byte[]> download(ReportService.Report report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(report.filename()).build().toString())
                .body(report.content());
    }
}
