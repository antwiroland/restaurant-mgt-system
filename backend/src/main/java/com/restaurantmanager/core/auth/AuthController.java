package com.restaurantmanager.core.auth;

import com.restaurantmanager.core.auth.dto.*;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UserPrincipal principal,
                       @Valid @RequestBody LogoutRequest request,
                       HttpServletRequest servletRequest) {
        authService.logout(principal, request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/pin/verify")
    public PinVerifyResponse verifyPin(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody PinVerifyRequest request,
                                       HttpServletRequest servletRequest) {
        return authService.verifyPin(principal, request, servletRequest.getRemoteAddr());
    }
}
