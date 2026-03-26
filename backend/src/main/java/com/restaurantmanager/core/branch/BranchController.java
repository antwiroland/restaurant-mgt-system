package com.restaurantmanager.core.branch;

import com.restaurantmanager.core.branch.dto.BranchRequest;
import com.restaurantmanager.core.branch.dto.BranchResponse;
import com.restaurantmanager.core.common.Pagination;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/branches")
public class BranchController {
    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public ResponseEntity<List<BranchResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        List<BranchResponse> all = branchService.list(principal);
        Pagination.Params params = Pagination.from(page, size);
        List<BranchResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BranchResponse create(@Valid @RequestBody BranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BranchResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }
}
