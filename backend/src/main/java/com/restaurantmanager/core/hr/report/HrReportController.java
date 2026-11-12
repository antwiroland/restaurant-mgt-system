package com.restaurantmanager.core.hr.report;

import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/hr/reports")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class HrReportController {
    private final HrReportService reports;

    public HrReportController(HrReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<HrReportService.ReportInfo> available(@AuthenticationPrincipal UserPrincipal principal) {
        return reports.available(principal);
    }

    @GetMapping("/{type}")
    public HrReportService.Report run(@PathVariable HrReportService.ReportType type,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID branchId,
                                      @RequestParam(defaultValue = "false") boolean includeExited,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        return reports.run(principal, type, new HrReportService.ReportQuery(from, to, branchId, includeExited));
    }

    @GetMapping("/{type}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable HrReportService.ReportType type,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) UUID branchId,
                                      @RequestParam(defaultValue = "false") boolean includeExited,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        byte[] body = reports.csv(principal, type, new HrReportService.ReportQuery(from, to, branchId, includeExited));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        String name = "hr-" + type.name().toLowerCase(Locale.ROOT) + (from != null ? "-" + from : "") + (to != null ? "-to-" + to : "") + ".csv";
        headers.setContentDisposition(ContentDisposition.attachment().filename(name).build());
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
