package com.restaurantmanager.gateway.hikvision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.device.CapturedEvent;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads Hikvision access-control events (ISAPI JSON) into {@link CapturedEvent}s.
 *
 * <p>Two shapes are handled: the live alert stream ({@code {"eventType":"AccessControllerEvent","dateTime":...,
 * "AccessControllerEvent":{"majorEventType":5,"subEventType":75,"employeeNoString":"1001","serialNo":12,
 * "attendanceStatus":"checkIn","currentVerifyMode":"face"}}}) and the event search result
 * ({@code {"AcsEvent":{"InfoList":[{"major":5,"minor":75,"time":...,"employeeNoString":"1001","serialNo":12,...}]}}}).</p>
 *
 * <p>Only "authentication passed" events of an enrolled person count (major 5, minor in the accepted set, with an
 * employee number). Door, alarm and heartbeat events are ignored.</p>
 */
public class HikvisionEventParser {
    static final int MAJOR_EVENT = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serialNumber;
    private final Set<Integer> acceptedMinors;
    private final ZoneId deviceZone;

    public HikvisionEventParser(String serialNumber, Set<Integer> acceptedMinors, ZoneId deviceZone) {
        this.serialNumber = serialNumber;
        this.acceptedMinors = acceptedMinors;
        this.deviceZone = deviceZone;
    }

    /** One part of the alert stream. Empty when it is not an attendance scan. */
    public Optional<CapturedEvent> fromAlert(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!"AccessControllerEvent".equalsIgnoreCase(root.path("eventType").asText())) {
                return Optional.empty();
            }
            JsonNode e = root.path("AccessControllerEvent");
            int major = e.path("majorEventType").asInt(-1);
            int minor = e.path("subEventType").asInt(-1);
            String time = e.hasNonNull("time") ? e.get("time").asText() : root.path("dateTime").asText(null);
            return build(major, minor, e, time, json);
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }

    /** The events in one page of an AcsEvent search response. */
    public List<CapturedEvent> fromSearch(JsonNode acsEvent) {
        List<CapturedEvent> events = new ArrayList<>();
        for (JsonNode info : acsEvent.path("InfoList")) {
            build(info.path("major").asInt(-1), info.path("minor").asInt(-1), info, info.path("time").asText(null), info.toString())
                    .ifPresent(events::add);
        }
        return events;
    }

    private Optional<CapturedEvent> build(int major, int minor, JsonNode e, String time, String raw) {
        String employee = e.path("employeeNoString").asText("");
        if (employee.isBlank() && e.hasNonNull("employeeNo")) {
            employee = e.get("employeeNo").asText("");
        }
        if (major != MAJOR_EVENT || !acceptedMinors.contains(minor) || employee.isBlank() || time == null) {
            return Optional.empty();
        }
        Instant at = parseTime(time);
        if (at == null) {
            return Optional.empty();
        }
        // The device's own event number; if a firmware omits it, derive a stable one so redelivery stays a duplicate.
        String eventId = e.hasNonNull("serialNo") ? e.get("serialNo").asText()
                : "h-" + UUID.nameUUIDFromBytes((employee + "|" + at + "|" + minor).getBytes(StandardCharsets.UTF_8));
        return Optional.of(new CapturedEvent(serialNumber, eventId, employee.trim(), attendanceStatus(e),
                authMethod(e.path("currentVerifyMode").asText(""), minor), at, raw));
    }

    /** The device's attendance status, or null when it did not set one (so the backend never guesses from it). */
    static String attendanceStatus(JsonNode e) {
        String status = e.path("attendanceStatus").asText("");
        if (status.isBlank() || "undefined".equalsIgnoreCase(status)) {
            return null;
        }
        return status;
    }

    static String authMethod(String verifyMode, int minor) {
        String mode = verifyMode.toLowerCase(Locale.ROOT);
        if (mode.contains("face")) return "FACE";
        if (mode.contains("finger") || mode.equals("fp")) return "FINGERPRINT";
        if (mode.contains("card")) return "CARD";
        return switch (minor) {
            case 75 -> "FACE";
            case 38 -> "FINGERPRINT";
            case 1 -> "CARD";
            default -> "OTHER";
        };
    }

    /** ISAPI times carry an offset (2026-11-13T07:55:00+00:00); a time without one is in the device's zone. */
    Instant parseTime(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(value).atZone(deviceZone).toInstant();
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }
}
