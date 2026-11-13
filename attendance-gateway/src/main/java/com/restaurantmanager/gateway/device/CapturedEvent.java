package com.restaurantmanager.gateway.device;

import java.time.Instant;

/**
 * One attendance scan as read from a device, before it is queued.
 *
 * @param deviceSerialNumber the device's registered serial number
 * @param deviceEventId      the device's own id for the event (Hikvision: serialNo), unique per device
 * @param employeeDeviceId   the person's user id on the device (Hikvision: employeeNoString), kept as is
 * @param eventType          what the device said it was (e.g. checkIn, checkOut, breakOut, breakIn) or null if it did not say
 * @param authenticationMethod FACE, FINGERPRINT, CARD or OTHER
 * @param deviceTimestamp    the device's time of the scan
 * @param rawEvent           the event exactly as received (JSON)
 */
public record CapturedEvent(String deviceSerialNumber, String deviceEventId, String employeeDeviceId, String eventType,
                            String authenticationMethod, Instant deviceTimestamp, String rawEvent) {}
