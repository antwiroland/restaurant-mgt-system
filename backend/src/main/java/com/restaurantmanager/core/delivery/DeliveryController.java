package com.restaurantmanager.core.delivery;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/delivery")
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** Public: zones customers can choose at checkout. */
    @GetMapping("/zones")
    public List<DeliveryService.ZoneResponse> activeZones() {
        return deliveryService.activeZones();
    }

    @GetMapping("/zones/all")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<DeliveryService.ZoneResponse> allZones() {
        return deliveryService.allZones();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryService.ZoneResponse createZone(@Valid @RequestBody DeliveryService.ZoneRequest request) {
        return deliveryService.saveZone(null, request);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryService.ZoneResponse updateZone(@PathVariable UUID id, @Valid @RequestBody DeliveryService.ZoneRequest request) {
        return deliveryService.saveZone(id, request);
    }

    @PatchMapping("/zones/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryService.ZoneResponse setZoneStatus(@PathVariable UUID id, @RequestBody ZoneStatusRequest request) {
        return deliveryService.setZoneActive(id, request.active());
    }

    @GetMapping("/riders")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<DeliveryService.RiderResponse> riders() {
        return deliveryService.riders();
    }

    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public DeliveryService.DeliveryView assign(@PathVariable UUID orderId,
                                               @Valid @RequestBody AssignRiderRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.assignRider(orderId, request.riderId(), principal);
    }

    @GetMapping("/rider/orders")
    @PreAuthorize("hasRole('RIDER')")
    public List<DeliveryService.DeliveryView> riderQueue(@AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.riderQueue(principal);
    }

    @PostMapping("/orders/{orderId}/pickup")
    @PreAuthorize("hasRole('RIDER')")
    public DeliveryService.DeliveryView pickup(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.markPickedUp(orderId, principal);
    }

    @PostMapping("/orders/{orderId}/delivered")
    @PreAuthorize("hasRole('RIDER')")
    public DeliveryService.DeliveryView delivered(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.markDelivered(orderId, principal);
    }

    @PostMapping("/orders/{orderId}/location")
    @PreAuthorize("hasRole('RIDER')")
    public DeliveryService.DeliveryView location(@PathVariable UUID orderId,
                                                 @Valid @RequestBody LocationRequest request,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.updateLocation(orderId, request.latitude(), request.longitude(), principal);
    }

    @GetMapping("/orders/{orderId}/tracking")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','CUSTOMER','RIDER')")
    public DeliveryService.DeliveryView tracking(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return deliveryService.tracking(orderId, principal);
    }

    public record AssignRiderRequest(@NotNull UUID riderId) {}

    public record LocationRequest(@NotNull Double latitude, @NotNull Double longitude) {}

    public record ZoneStatusRequest(boolean active) {}
}
