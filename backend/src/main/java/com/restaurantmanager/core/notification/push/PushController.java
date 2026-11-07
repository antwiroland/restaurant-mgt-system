package com.restaurantmanager.core.notification.push;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/devices")
public class PushController {
    private final PushNotificationService service;

    public PushController(PushNotificationService service) {
        this.service = service;
    }

    /** The app registers its Expo push token after sign-in. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void register(@Valid @RequestBody PushNotificationService.DeviceRequest request,
                         @AuthenticationPrincipal UserPrincipal principal) {
        service.register(principal.userId(), request);
    }

    /** Called on sign-out so the device stops receiving this account's messages. */
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void unregister(@PathVariable String token, @AuthenticationPrincipal UserPrincipal principal) {
        service.unregister(principal.userId(), token);
    }
}
