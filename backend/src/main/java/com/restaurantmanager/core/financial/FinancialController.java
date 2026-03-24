package com.restaurantmanager.core.financial;

import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.financial.dto.OverrideTokenRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/financial")
public class FinancialController {
    private final FinancialService financialService;

    public FinancialController(FinancialService financialService) {
        this.financialService = financialService;
    }

    @PostMapping("/discount")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public Map<String, Object> discount(@Valid @RequestBody OverrideTokenRequest request) {
        return financialService.performAction(request.overrideToken(), OverrideActionType.DISCOUNT);
    }

    @PostMapping("/void")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public Map<String, Object> voidOrder(@Valid @RequestBody OverrideTokenRequest request) {
        return financialService.performAction(request.overrideToken(), OverrideActionType.VOID);
    }

    @PostMapping("/refund")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public Map<String, Object> refund(@Valid @RequestBody OverrideTokenRequest request) {
        return financialService.performAction(request.overrideToken(), OverrideActionType.REFUND);
    }
}
