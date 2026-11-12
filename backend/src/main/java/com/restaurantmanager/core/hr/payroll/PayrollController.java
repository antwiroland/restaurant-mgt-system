package com.restaurantmanager.core.hr.payroll;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class PayrollController {
    private final PayrollService payroll;
    private final PayrollRuleService rules;
    private final PayrollDocumentService documents;

    public PayrollController(PayrollService payroll, PayrollRuleService rules, PayrollDocumentService documents) {
        this.payroll = payroll;
        this.rules = rules;
        this.documents = documents;
    }

    @GetMapping("/payroll-rules")
    public List<PayrollRuleService.RuleView> rules(@AuthenticationPrincipal UserPrincipal principal) {
        return rules.list(principal);
    }

    @PostMapping("/payroll-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollRuleService.RuleView addRule(@Valid @RequestBody PayrollRuleService.RuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return rules.add(principal, request);
    }

    @GetMapping("/payroll-runs")
    public List<PayrollService.RunSummary> runs(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.list(principal, branchId);
    }

    @PostMapping("/payroll-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollService.RunDetail create(@Valid @RequestBody PayrollService.CreateRunRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.create(principal, request);
    }

    @GetMapping("/payroll-runs/{id}")
    public PayrollService.RunDetail get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.get(principal, id);
    }

    @PostMapping("/payroll-runs/{id}/calculate")
    public PayrollService.RunDetail calculate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.calculate(principal, id);
    }

    @PostMapping("/payroll-runs/{id}/adjustments")
    public PayrollService.RunDetail addAdjustment(@PathVariable UUID id, @Valid @RequestBody PayrollService.AdjustmentRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.addAdjustment(principal, id, request);
    }

    @DeleteMapping("/payroll-runs/{id}/adjustments/{adjustmentId}")
    public PayrollService.RunDetail removeAdjustment(@PathVariable UUID id, @PathVariable UUID adjustmentId,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.removeAdjustment(principal, id, adjustmentId);
    }

    @PostMapping("/payroll-runs/{id}/approve")
    public PayrollService.RunDetail approve(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.approve(principal, id);
    }

    @PostMapping("/payroll-runs/{id}/reopen")
    public PayrollService.RunDetail reopen(@PathVariable UUID id, @Valid @RequestBody PayrollService.ReasonRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.reopen(principal, id, request);
    }

    @PostMapping("/payroll-runs/{id}/finalize")
    public PayrollService.RunDetail finalizeRun(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.finalizeRun(principal, id);
    }

    @PostMapping("/payroll-runs/{id}/pay")
    public PayrollService.RunDetail pay(@PathVariable UUID id, @Valid @RequestBody PayrollService.PayRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.pay(principal, id, request);
    }

    @PostMapping("/payroll-runs/{id}/cancel")
    public PayrollService.RunDetail cancel(@PathVariable UUID id, @Valid @RequestBody PayrollService.ReasonRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.cancel(principal, id, request);
    }

    @GetMapping("/payslips/{id}")
    public PayrollService.PayslipDetail payslip(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return payroll.payslip(principal, id);
    }

    @GetMapping("/me/payslips")
    public List<PayrollService.MyPayslip> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return payroll.mine(principal);
    }

    /** A payslip as PDF (payroll staff in scope, or the employee once the run is finalized). */
    @GetMapping("/payslips/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> payslipPdf(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return download(documents.payslipPdf(principal, id));
    }

    @GetMapping("/employees/{id}/payout")
    public PayrollDocumentService.PayoutDetails payout(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.payout(principal, id);
    }

    @org.springframework.web.bind.annotation.PutMapping("/employees/{id}/payout")
    public PayrollDocumentService.PayoutDetails updatePayout(@PathVariable UUID id, @Valid @RequestBody PayrollDocumentService.PayoutRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return documents.updatePayout(principal, id, request);
    }

    @GetMapping("/me/payout")
    public PayrollDocumentService.PayoutDetails myPayout(@AuthenticationPrincipal UserPrincipal principal) {
        return documents.myPayout(principal);
    }

    @org.springframework.web.bind.annotation.PutMapping("/me/payout")
    public PayrollDocumentService.PayoutDetails updateMyPayout(@Valid @RequestBody PayrollDocumentService.PayoutRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return documents.updateMyPayout(principal, request);
    }

    /** Who the run's payment files would pay, per method, and who is missing payout details. */
    @GetMapping("/payroll-runs/{id}/payment-files")
    public PayrollDocumentService.PaymentFileSummary paymentFiles(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return documents.paymentFileSummary(principal, id);
    }

    /** The bank or Mobile Money bulk-payment CSV for a finalized run. */
    @GetMapping("/payroll-runs/{id}/payment-files/{method}")
    public org.springframework.http.ResponseEntity<byte[]> paymentFile(@PathVariable UUID id, @PathVariable PaymentMethod method,
                                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return download(documents.paymentFile(principal, id, method));
    }

    private static org.springframework.http.ResponseEntity<byte[]> download(com.restaurantmanager.core.reporting.ReportService.Report file) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
