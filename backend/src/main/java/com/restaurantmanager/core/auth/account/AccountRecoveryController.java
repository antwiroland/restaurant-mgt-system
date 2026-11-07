package com.restaurantmanager.core.auth.account;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class AccountRecoveryController {
    private final AccountRecoveryService service;

    public AccountRecoveryController(AccountRecoveryService service) {
        this.service = service;
    }

    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest request, HttpServletRequest http) {
        service.requestReset(request.emailOrPhone(), http.getRemoteAddr());
        return Map.of("message", "If an account matches, a reset link has been sent.");
    }

    @PostMapping("/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
        service.resetPassword(request.token(), request.newPassword(), http.getRemoteAddr());
    }

    @PostMapping("/auth/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void change(@Valid @RequestBody ChangeRequest request, @AuthenticationPrincipal UserPrincipal principal,
                       HttpServletRequest http) {
        service.changePassword(principal, request.currentPassword(), request.newPassword(), http.getRemoteAddr());
    }

    @PostMapping("/auth/email/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("isAuthenticated()")
    public void requestVerification(@AuthenticationPrincipal UserPrincipal principal) {
        service.requestEmailVerification(principal);
    }

    @PostMapping("/auth/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        service.verifyEmail(request.token(), http.getRemoteAddr());
    }

    @PostMapping("/users/{userId}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public void adminReset(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest http) {
        service.adminReset(userId, principal, http.getRemoteAddr());
    }

    public record ForgotRequest(@NotBlank @Size(max = 255) String emailOrPhone) {}

    public record ResetRequest(@NotBlank String token, @NotBlank @Size(max = 128) String newPassword) {}

    public record ChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}

    public record TokenRequest(@NotBlank String token) {}
}
