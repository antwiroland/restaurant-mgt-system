package com.restaurantmanager.core.table.payment;

import com.restaurantmanager.core.security.UserPrincipal;
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
@RequestMapping("/tables")
public class TablePaymentController {
    private final TablePaymentWatchService watchService;
    private final WalkoutService walkoutService;

    public TablePaymentController(TablePaymentWatchService watchService, WalkoutService walkoutService) {
        this.watchService = watchService;
        this.walkoutService = walkoutService;
    }

    /** Tables that still owe money, with what is owed and whether staff should go to the table now. */
    @GetMapping("/payment-watch")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<TablePaymentWatchService.TableWatch> watch(@RequestParam(required = false) UUID branchId,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return watchService.watch(branchId, principal);
    }

    /** Close out a table whose guests left without paying (manager only). */
    @PostMapping("/{tableId}/walkout")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WalkoutService.WalkoutView walkout(@PathVariable UUID tableId, @RequestBody WalkoutService.WalkoutRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return walkoutService.record(tableId, request, principal);
    }
}
