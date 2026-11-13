package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.hr.attendance.ClockAction;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Staff screens for biometric attendance: devices, gateways, device user links, the review queue and health. */
@RestController
@RequestMapping("/hr/attendance-devices")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER','RIDER','HR')")
public class AttendanceDeviceController {
    private final DeviceAdminService service;

    public AttendanceDeviceController(DeviceAdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public DeviceAdminService.Overview overview(@AuthenticationPrincipal UserPrincipal principal) {
        return service.overview(principal);
    }

    @GetMapping("/gateways")
    public List<DeviceAdminService.GatewayView> gateways(@AuthenticationPrincipal UserPrincipal principal) {
        return service.gateways(principal);
    }

    @PostMapping("/gateways")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceAdminService.GatewayKeyView createGateway(@Valid @RequestBody DeviceAdminService.GatewayRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return service.createGateway(principal, request);
    }

    @PostMapping("/gateways/{id}/rotate-key")
    public DeviceAdminService.GatewayKeyView rotateKey(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.rotateKey(principal, id);
    }

    @PostMapping("/gateways/{id}/revoke")
    public DeviceAdminService.GatewayView revoke(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.revoke(principal, id);
    }

    @GetMapping("/devices")
    public List<DeviceAdminService.DeviceView> devices(@AuthenticationPrincipal UserPrincipal principal) {
        return service.devices(principal);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceAdminService.DeviceView createDevice(@Valid @RequestBody DeviceAdminService.DeviceRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return service.saveDevice(principal, null, request);
    }

    @PutMapping("/devices/{id}")
    public DeviceAdminService.DeviceView updateDevice(@PathVariable UUID id, @Valid @RequestBody DeviceAdminService.DeviceRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return service.saveDevice(principal, id, request);
    }

    @GetMapping("/device-ids")
    public List<DeviceAdminService.DeviceIdView> deviceIds(@AuthenticationPrincipal UserPrincipal principal) {
        return service.deviceIds(principal);
    }

    @PostMapping("/device-ids")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceAdminService.DeviceIdView addDeviceId(@Valid @RequestBody DeviceAdminService.DeviceIdRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return service.addDeviceId(principal, request);
    }

    @DeleteMapping("/device-ids/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDeviceId(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        service.removeDeviceId(principal, id);
    }

    /** {@code filter=review} (default): scans waiting for a decision; {@code filter=all}: every scan in the range. */
    @GetMapping("/events")
    public List<DeviceAdminService.EventView> events(@RequestParam(required = false) String filter,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return service.events(principal, filter, from, to);
    }

    public record ApplyRequest(ClockAction action, @Size(max = 300) String note) {}

    public record DismissRequest(@Size(max = 300) String note) {}

    @PostMapping("/events/{id}/apply")
    public DeviceAdminService.EventView apply(@PathVariable UUID id, @Valid @RequestBody ApplyRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return service.apply(principal, id, request.action(), request.note());
    }

    @PostMapping("/events/{id}/retry")
    public DeviceAdminService.EventView retry(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.retry(principal, id);
    }

    @PostMapping("/events/{id}/dismiss")
    public DeviceAdminService.EventView dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return service.dismiss(principal, id, request.note());
    }
}
