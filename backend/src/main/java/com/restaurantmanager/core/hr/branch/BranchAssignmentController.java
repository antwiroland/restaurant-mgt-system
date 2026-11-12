package com.restaurantmanager.core.hr.branch;

import com.restaurantmanager.core.hr.employee.EmployeeService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class BranchAssignmentController {
    private final BranchAssignmentService assignmentService;
    private final EmployeeService employeeService;

    public BranchAssignmentController(BranchAssignmentService assignmentService, EmployeeService employeeService) {
        this.assignmentService = assignmentService;
        this.employeeService = employeeService;
    }

    @GetMapping("/employees/{employeeId}/assignments")
    public List<BranchAssignmentService.AssignmentView> history(@PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        return assignmentService.history(principal, employeeId);
    }

    @PostMapping("/employees/{employeeId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchAssignmentService.AssignmentView assign(@PathVariable UUID employeeId,
                                                         @Valid @RequestBody BranchAssignmentService.AssignmentRequest request,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return assignmentService.assign(principal, employeeId, request);
    }

    @PostMapping("/assignments/{id}/end")
    public BranchAssignmentService.AssignmentView end(@PathVariable UUID id, @Valid @RequestBody BranchAssignmentService.EndRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return assignmentService.end(principal, id, request);
    }

    @PostMapping("/employees/{employeeId}/transfer")
    public EmployeeService.EmployeeDetail transfer(@PathVariable UUID employeeId, @Valid @RequestBody EmployeeService.TransferRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.transfer(principal, employeeId, request);
    }

    @GetMapping("/branches/workforce")
    public List<BranchAssignmentService.BranchWorkforce> workforce(@AuthenticationPrincipal UserPrincipal principal) {
        return assignmentService.workforce(principal);
    }
}
