package com.restaurantmanager.core.hr.org;

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

/** Coarse role gate keeps customers out; {@link OrgService} enforces HR permissions and branch scope. */
@RestController
@RequestMapping("/hr")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class OrgController {
    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/departments")
    public List<OrgService.DepartmentView> departments(@RequestParam(defaultValue = "false") boolean includeArchived,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.departments(principal, includeArchived);
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgService.DepartmentView createDepartment(@Valid @RequestBody OrgService.DepartmentRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.saveDepartment(null, request, principal);
    }

    @PutMapping("/departments/{id}")
    public OrgService.DepartmentView updateDepartment(@PathVariable UUID id, @Valid @RequestBody OrgService.DepartmentRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.saveDepartment(id, request, principal);
    }

    @PostMapping("/departments/{id}/archive")
    public OrgService.DepartmentView archiveDepartment(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.setDepartmentActive(id, false, principal);
    }

    @PostMapping("/departments/{id}/restore")
    public OrgService.DepartmentView restoreDepartment(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.setDepartmentActive(id, true, principal);
    }

    @GetMapping("/positions")
    public List<OrgService.PositionView> positions(@RequestParam(defaultValue = "false") boolean includeArchived,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.positions(principal, includeArchived);
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgService.PositionView createPosition(@Valid @RequestBody OrgService.PositionRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.savePosition(null, request, principal);
    }

    @PutMapping("/positions/{id}")
    public OrgService.PositionView updatePosition(@PathVariable UUID id, @Valid @RequestBody OrgService.PositionRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.savePosition(id, request, principal);
    }

    @PostMapping("/positions/{id}/archive")
    public OrgService.PositionView archivePosition(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.setPositionActive(id, false, principal);
    }

    @PostMapping("/positions/{id}/restore")
    public OrgService.PositionView restorePosition(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return orgService.setPositionActive(id, true, principal);
    }
}
