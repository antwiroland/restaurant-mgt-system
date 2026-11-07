package com.restaurantmanager.core.financial;

import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.financial.dto.FinancialDtos.DiscountRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.DiscountResponse;
import com.restaurantmanager.core.financial.dto.FinancialDtos.FinancialAuditRecord;
import com.restaurantmanager.core.financial.dto.FinancialDtos.ReconciliationReport;
import com.restaurantmanager.core.financial.dto.FinancialDtos.RefundRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.RefundResponse;
import com.restaurantmanager.core.financial.dto.FinancialDtos.SignOff;
import com.restaurantmanager.core.financial.dto.FinancialDtos.SignOffRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.VoidRequest;
import com.restaurantmanager.core.financial.dto.FinancialDtos.VoidResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/financial")
public class FinancialController {
    private final FinancialService financialService;

    public FinancialController(FinancialService financialService) {
        this.financialService = financialService;
    }

    @PostMapping("/discount")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DiscountResponse discount(@Valid @RequestBody DiscountRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal,
                                     HttpServletRequest servletRequest) {
        return financialService.applyDiscount(request, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/void")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public VoidResponse voidOrder(@Valid @RequestBody VoidRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal,
                                  HttpServletRequest servletRequest) {
        return financialService.voidOrder(request, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/refund")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public RefundResponse refund(@Valid @RequestBody RefundRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal,
                                 HttpServletRequest servletRequest) {
        return financialService.refund(request, principal, servletRequest.getRemoteAddr());
    }

    @GetMapping("/reconciliation/{businessDate}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ReconciliationReport reconciliation(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
                                               @RequestParam(required = false) UUID branchId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return financialService.reconciliation(businessDate, branchId, principal);
    }

    @PostMapping("/reconciliation/{businessDate}/sign-off")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ReconciliationReport signOff(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
                                        @Valid @RequestBody SignOffRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest servletRequest) {
        return financialService.signOff(businessDate, request, principal, servletRequest.getRemoteAddr());
    }

    @GetMapping("/reconciliation/sign-offs")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<SignOff> signOffs(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financialService.signOffs(from, to);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<FinancialAuditRecord> audit(@RequestParam(required = false) AuditAction action,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to) {
        return financialService.audit(action, from, to);
    }
}
