package com.restaurantmanager.core.hr.tips;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER')")
public class TipController {
    private final TipService tips;

    public TipController(TipService tips) {
        this.tips = tips;
    }

    @GetMapping("/tip-rules")
    public List<TipService.RuleView> rules(@AuthenticationPrincipal UserPrincipal principal) {
        return tips.rules(principal);
    }

    @PostMapping("/tip-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public TipService.RuleView addRule(@Valid @RequestBody TipService.RuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.addRule(principal, request);
    }

    @GetMapping("/tip-pools")
    public List<TipService.PoolSummary> pools(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.list(principal, branchId);
    }

    @PostMapping("/tip-pools")
    @ResponseStatus(HttpStatus.CREATED)
    public TipService.PoolDetail create(@Valid @RequestBody TipService.CreatePoolRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.create(principal, request);
    }

    @GetMapping("/tip-pools/{id}")
    public TipService.PoolDetail get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.get(principal, id);
    }

    @PostMapping("/tip-pools/{id}/calculate")
    public TipService.PoolDetail calculate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.calculate(principal, id);
    }

    @PostMapping("/tip-pools/{id}/approve")
    public TipService.PoolDetail approve(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return tips.approve(principal, id);
    }

    @PostMapping("/tip-pools/{id}/reopen")
    public TipService.PoolDetail reopen(@PathVariable UUID id, @Valid @RequestBody TipService.ReasonRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return tips.reopen(principal, id, request);
    }

    @PostMapping("/tip-pools/{id}/cancel")
    public TipService.PoolDetail cancel(@PathVariable UUID id, @Valid @RequestBody TipService.ReasonRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return tips.cancel(principal, id, request);
    }

    @GetMapping("/me/tips")
    public List<TipService.MyShare> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return tips.mine(principal);
    }
}
