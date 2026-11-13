package com.restaurantmanager.core.hr.employee;

import com.restaurantmanager.core.hr.common.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/employees")
    public PageResponse<EmployeeService.EmployeeSummary> list(@RequestParam(required = false) String q,
                                                             @RequestParam(required = false) UUID branchId,
                                                             @RequestParam(required = false) UUID departmentId,
                                                             @RequestParam(required = false) UUID positionId,
                                                             @RequestParam(required = false) EmploymentStatus status,
                                                             @RequestParam(required = false) EmploymentType type,
                                                             @RequestParam(defaultValue = "false") boolean includeExited,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "25") int size,
                                                             @RequestParam(required = false) String sort,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.list(principal,
                new EmployeeService.EmployeeFilter(q, branchId, departmentId, positionId, status, type, includeExited), page, size, sort);
    }

    @GetMapping(value = "/employees/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<String> export(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) UUID branchId,
                                                                  @RequestParam(required = false) UUID departmentId,
                                                                  @RequestParam(required = false) UUID positionId,
                                                                  @RequestParam(required = false) EmploymentStatus status,
                                                                  @RequestParam(required = false) EmploymentType type,
                                                                  @RequestParam(defaultValue = "false") boolean includeExited,
                                                                  @AuthenticationPrincipal UserPrincipal principal) {
        String csv = employeeService.exportCsv(principal,
                new EmployeeService.EmployeeFilter(q, branchId, departmentId, positionId, status, type, includeExited));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"employees.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body("\uFEFF" + csv);
    }

    @GetMapping("/employees/linkable-users")
    public List<EmployeeService.LinkableUser> linkableUsers(@AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.linkableUsers(principal);
    }

    @PostMapping("/employees/import-users")
    public EmployeeService.ImportResult importUsers(@Valid @RequestBody EmployeeService.ImportRequest request,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.importUsers(principal, request);
    }

    @GetMapping("/employees/{id}")
    public EmployeeService.EmployeeDetail get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.get(principal, id);
    }

    @GetMapping("/employees/{id}/audit")
    public List<EmployeeService.AuditEntry> audit(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.auditTrail(principal, id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeService.EmployeeDetail create(@Valid @RequestBody EmployeeService.EmployeeRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.create(principal, request);
    }

    @PutMapping("/employees/{id}")
    public EmployeeService.EmployeeDetail update(@PathVariable UUID id, @Valid @RequestBody EmployeeService.EmployeeRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.update(principal, id, request);
    }

    @PostMapping("/employees/{id}/status")
    public EmployeeService.EmployeeDetail changeStatus(@PathVariable UUID id, @Valid @RequestBody EmployeeService.StatusRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.changeStatus(principal, id, request);
    }

    @PostMapping("/employees/{id}/link-user")
    public EmployeeService.EmployeeDetail linkUser(@PathVariable UUID id, @Valid @RequestBody EmployeeService.LinkUserRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.linkUser(principal, id, request.userId());
    }

    @PostMapping("/employees/{id}/create-login")
    public EmployeeService.EmployeeDetail createLogin(@PathVariable UUID id, @Valid @RequestBody EmployeeService.CreateLoginRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.createLogin(principal, id, request);
    }

    @DeleteMapping("/employees/{id}/link-user")
    public EmployeeService.EmployeeDetail unlinkUser(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.unlinkUser(principal, id);
    }

    @GetMapping("/me/profile")
    public EmployeeService.EmployeeDetail myProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.myProfile(principal);
    }

    /** Self-service: own contact details and emergency contacts only. */
    @PutMapping("/me/profile")
    public EmployeeService.EmployeeDetail updateMyProfile(@Valid @RequestBody EmployeeService.MyProfileRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return employeeService.updateMyProfile(principal, request);
    }
}
