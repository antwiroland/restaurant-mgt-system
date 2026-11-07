package com.restaurantmanager.core.servicerequest;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ServiceRequestController {
    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    /** Public (table QR token): call a waiter or ask for the bill. Repeat taps within a few minutes are merged. */
    @PostMapping("/tables/public/{tableToken}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestService.RequestView create(@PathVariable String tableToken,
                                                    @Valid @RequestBody ServiceRequestService.CreateRequest request) {
        return service.createFromTable(tableToken, request);
    }

    /** Public (table QR token): the guest's recent requests and their status. */
    @GetMapping("/tables/public/{tableToken}/requests")
    public List<ServiceRequestService.RequestView> listForTable(@PathVariable String tableToken) {
        return service.listForTable(tableToken);
    }

    @GetMapping("/service-requests")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<ServiceRequestService.RequestView> list(@RequestParam(defaultValue = "false") boolean includeResolved,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return service.listForStaff(includeResolved, principal);
    }

    @PatchMapping("/service-requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ServiceRequestService.RequestView update(@PathVariable UUID id,
                                                    @Valid @RequestBody ServiceRequestService.StatusRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return service.updateStatus(id, request.status(), principal);
    }
}
