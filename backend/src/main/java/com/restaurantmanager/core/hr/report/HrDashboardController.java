package com.restaurantmanager.core.hr.report;

import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class HrDashboardController {
    private final HrDashboardService dashboard;

    public HrDashboardController(HrDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /** Needs hr.reports.view; sections follow the viewer's other HR permissions and branch scope. */
    @GetMapping("/dashboard")
    public HrDashboardService.Dashboard dashboard(@RequestParam(required = false) UUID branchId, @AuthenticationPrincipal UserPrincipal principal) {
        return dashboard.dashboard(principal, branchId);
    }
}
