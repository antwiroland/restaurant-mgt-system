package com.restaurantmanager.core.profile;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/restaurant/profile")
public class RestaurantProfileController {
    private final RestaurantProfileService service;

    public RestaurantProfileController(RestaurantProfileService service) {
        this.service = service;
    }

    /** Public: customer pages, receipts and apps read the restaurant's name, hours and contact details. */
    @GetMapping
    public RestaurantProfileService.ProfileView get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantProfileService.ProfileView update(@Valid @RequestBody RestaurantProfileService.ProfileRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return service.update(request, principal.userId());
    }
}
