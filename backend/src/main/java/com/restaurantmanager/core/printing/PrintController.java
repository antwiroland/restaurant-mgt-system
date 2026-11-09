package com.restaurantmanager.core.printing;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PrintController {
    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @GetMapping("/printers")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<PrintService.PrinterView> list(@AuthenticationPrincipal UserPrincipal principal) {
        return printService.list(principal);
    }

    @PostMapping("/printers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PrintService.PrinterView create(@Valid @RequestBody PrintService.PrinterRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        validateWidth(request);
        return printService.save(null, request, principal);
    }

    @PutMapping("/printers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PrintService.PrinterView update(@PathVariable UUID id, @Valid @RequestBody PrintService.PrinterRequest request,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        validateWidth(request);
        return printService.save(id, request, principal);
    }

    @DeleteMapping("/printers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable UUID id) {
        printService.delete(id);
    }

    @PostMapping("/printers/{id}/test")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PrintService.PrintResult test(@PathVariable UUID id) {
        return printService.test(id);
    }

    @PostMapping("/orders/{orderId}/print/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public PrintService.PrintResult printReceipt(@PathVariable UUID orderId, @RequestBody(required = false) PrintRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return printService.printReceipt(orderId, request == null ? null : request.printerId(), principal);
    }

    @PostMapping("/orders/{orderId}/print/kitchen")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public PrintService.PrintResult printKitchen(@PathVariable UUID orderId, @RequestBody(required = false) PrintRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return printService.printKitchenTicket(orderId, request == null ? null : request.printerId(), principal);
    }

    private static void validateWidth(PrintService.PrinterRequest request) {
        if (request.paperWidthMm() != null && request.paperWidthMm() != 58 && request.paperWidthMm() != 80) {
            throw new ApiException(400, "paperWidthMm must be 58 or 80");
        }
    }

    public record PrintRequest(UUID printerId) {}
}
