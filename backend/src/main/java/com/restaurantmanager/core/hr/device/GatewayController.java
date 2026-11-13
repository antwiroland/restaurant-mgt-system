package com.restaurantmanager.core.hr.device;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API for local attendance gateways. Not for people: each request carries the gateway key, either as
 * {@code X-Gateway-Key: rmgw_...} or {@code Authorization: Gateway rmgw_...}. Must be called over HTTPS in production.
 */
@RestController
@RequestMapping("/attendance/gateway")
public class GatewayController {
    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /** Device events, in any order (they are processed in device-time order). Every event in the batch is answered. */
    @PostMapping("/events")
    public GatewayService.BatchResult events(@RequestHeader(value = "X-Gateway-Key", required = false) String key,
                                             @RequestHeader(value = "Authorization", required = false) String authorization,
                                             @Valid @RequestBody GatewayService.EventBatch batch) {
        return gatewayService.receive(key(key, authorization), batch);
    }

    /** Health report; answers with the current configuration so device changes reach the gateway within a minute. */
    @PostMapping("/heartbeat")
    public GatewayService.GatewayConfig heartbeat(@RequestHeader(value = "X-Gateway-Key", required = false) String key,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @Valid @RequestBody GatewayService.Heartbeat heartbeat) {
        return gatewayService.heartbeat(key(key, authorization), heartbeat);
    }

    @GetMapping("/config")
    public GatewayService.GatewayConfig config(@RequestHeader(value = "X-Gateway-Key", required = false) String key,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        return gatewayService.config(key(key, authorization));
    }

    @GetMapping("/devices")
    public List<GatewayService.DeviceConfig> devices(@RequestHeader(value = "X-Gateway-Key", required = false) String key,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        return gatewayService.devices(key(key, authorization));
    }

    @PostMapping("/sync-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void syncStatus(@RequestHeader(value = "X-Gateway-Key", required = false) String key,
                           @RequestHeader(value = "Authorization", required = false) String authorization,
                           @Valid @RequestBody GatewayService.SyncStatus status) {
        gatewayService.syncStatus(key(key, authorization), status);
    }

    private static String key(String header, String authorization) {
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        if (authorization != null && authorization.regionMatches(true, 0, "Gateway ", 0, 8)) {
            return authorization.substring(8).trim();
        }
        return null;
    }
}
