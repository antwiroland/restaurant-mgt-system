package com.restaurantmanager.core.user;

import com.restaurantmanager.core.security.UserPrincipal;
import com.restaurantmanager.core.common.Pagination;
import com.restaurantmanager.core.user.dto.AssignRoleRequest;
import com.restaurantmanager.core.user.dto.SetPinRequest;
import com.restaurantmanager.core.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listAll(@RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        List<UserResponse> all = userService.listAll();
        Pagination.Params params = Pagination.from(page, size);
        List<UserResponse> data = Pagination.slice(all, params);
        return ResponseEntity.ok()
                .headers(Pagination.headers(all.size(), params))
                .body(data);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse assignRole(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody AssignRoleRequest request,
                                   HttpServletRequest servletRequest) {
        return userService.assignRole(principal, id, request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CUSTOMER','CASHIER')")
    public void setPin(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID id,
                       @Valid @RequestBody SetPinRequest request) {
        userService.setPin(principal, id, request);
    }
}
