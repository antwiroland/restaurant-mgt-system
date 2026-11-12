package com.restaurantmanager.core.hr.contract;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
public class ContractController {
    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping("/employees/{employeeId}/contracts")
    public List<ContractService.ContractView> list(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.list(principal, employeeId);
    }

    @PostMapping("/employees/{employeeId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractService.ContractView create(@PathVariable UUID employeeId, @Valid @RequestBody ContractService.ContractRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.create(principal, employeeId, request);
    }

    @GetMapping("/me/contracts")
    public List<ContractService.ContractView> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return contractService.mine(principal);
    }

    @GetMapping("/contracts/due")
    public List<ContractService.ContractView> due(@RequestParam(defaultValue = "30") int days,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.due(principal, days);
    }

    @PutMapping("/contracts/{id}")
    public ContractService.ContractView update(@PathVariable UUID id, @Valid @RequestBody ContractService.ContractRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.update(principal, id, request);
    }

    @PostMapping("/contracts/{id}/activate")
    public ContractService.ContractView activate(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.activate(principal, id);
    }

    @PostMapping("/contracts/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractService.ContractView renew(@PathVariable UUID id, @Valid @RequestBody ContractService.ContractRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.renew(principal, id, request);
    }

    @PostMapping("/contracts/{id}/end")
    public ContractService.ContractView end(@PathVariable UUID id, @Valid @RequestBody ContractService.EndRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return contractService.end(principal, id, request);
    }
}
