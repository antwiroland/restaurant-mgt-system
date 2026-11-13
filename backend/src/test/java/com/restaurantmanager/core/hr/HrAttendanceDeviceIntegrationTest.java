package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.attendance.AttendanceEventRepository;
import com.restaurantmanager.core.hr.device.DeviceEventRepository;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Biometric attendance through a local gateway: keys, device events, idempotency, direction rules, review and health. */
class HrAttendanceDeviceIntegrationTest extends HrIntegrationTestSupport {
    @Autowired
    private DeviceEventRepository deviceEventRepository;
    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity manager;
    private UUID cook;
    private UUID cleaner;
    private String key;
    private UUID gatewayId;
    private UUID reception;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        cook = createEmployee(admin, "Kwesi", "Cook", accra);
        cleaner = createEmployee(admin, "Gifty", "Cleaner", accra);

        JsonNode gw = json(postJson("/hr/attendance-devices/gateways",
                Map.of("name", "Accra back office PC", "gatewayIdentifier", "GATEWAY-001", "branchId", accra.getId()), admin)
                .andExpect(status().isCreated()));
        key = gw.get("key").asText();
        gatewayId = UUID.fromString(gw.at("/gateway/id").asText());
        assertThat(key).startsWith(gw.at("/gateway/keyPrefix").asText());

        reception = device("Staff entrance", "DS-K1T-0001", "DEVICE_REPORTED");
        postJson("/hr/attendance-devices/device-ids", Map.of("employeeId", cook, "deviceUserId", "1001"), admin)
                .andExpect(status().isCreated());
    }

    private UUID device(String name, String serial, String direction) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("serialNumber", serial);
        body.put("vendor", "HIKVISION");
        body.put("model", "DS-K1T341");
        body.put("ipAddress", "192.168.1.60");
        body.put("port", 80);
        body.put("gatewayId", gatewayId);
        body.put("branchId", accra.getId());
        body.put("directionMode", direction);
        return UUID.fromString(json(postJson("/hr/attendance-devices/devices", body, admin).andExpect(status().isCreated())).get("id").asText());
    }

    private static Map<String, Object> event(String serial, String eventId, String user, String type, Instant at) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("deviceSerialNumber", serial);
        e.put("deviceEventId", eventId);
        e.put("employeeDeviceId", user);
        if (type != null) e.put("eventType", type);
        e.put("authenticationMethod", "FACE");
        e.put("deviceTimestamp", at.toString());
        e.put("gatewayReceivedAt", at.plusSeconds(2).toString());
        e.put("rawEvent", "{\"major\":5,\"minor\":75}");
        return e;
    }

    private ResultActions send(String withKey, List<Map<String, Object>> events) throws Exception {
        var request = post("/attendance/gateway/events").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("gatewayId", "GATEWAY-001", "events", events)));
        if (withKey != null) request.header("X-Gateway-Key", withKey);
        return mockMvc.perform(request);
    }

    private Map<String, String> statuses(JsonNode batch) {
        Map<String, String> byId = new HashMap<>();
        batch.get("results").forEach(r -> byId.put(r.get("deviceEventId").asText(), r.get("status").asText()));
        return byId;
    }

    @Test
    void fullDayFromTheDevice_becomesOneAttendanceRecord_usingDeviceTimes_andRedeliveryChangesNothing() throws Exception {
        Instant in = Instant.now().minus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        List<Map<String, Object>> day = new ArrayList<>(List.of(
                // sent out of order on purpose: processed in device-time order
                event("DS-K1T-0001", "105", "1001", "checkOut", in.plus(4, ChronoUnit.HOURS)),
                event("DS-K1T-0001", "101", "1001", "checkIn", in),
                event("DS-K1T-0001", "102", "1001", "checkIn", in.plus(1, ChronoUnit.MINUTES)), // scanned twice
                event("DS-K1T-0001", "103", "1001", "breakOut", in.plus(2, ChronoUnit.HOURS)),
                event("DS-K1T-0001", "104", "1001", "breakIn", in.plus(150, ChronoUnit.MINUTES))));

        JsonNode first = json(send(key, day).andExpect(status().isOk()));
        assertThat(statuses(first)).containsEntry("101", "ACCEPTED").containsEntry("102", "REPEAT_SCAN")
                .containsEntry("103", "ACCEPTED").containsEntry("104", "ACCEPTED").containsEntry("105", "ACCEPTED");

        assertThat(timeEntryRepository.findAll()).hasSize(1);
        var entry = timeEntryRepository.findAll().get(0);
        assertThat(entry.getClockInAt()).isEqualTo(in);
        assertThat(entry.getClockOutAt()).isEqualTo(in.plus(4, ChronoUnit.HOURS));
        assertThat(entry.getSource()).isEqualTo("DEVICE");
        assertThat(entry.getTerminalId()).isEqualTo("device:DS-K1T-0001");

        getJson("/hr/attendance/entries?branchId=" + accra.getId() + "&from=" + java.time.LocalDate.now().minusDays(1)
                + "&to=" + java.time.LocalDate.now(), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].breaks[0].type").value("MEAL"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        // The gateway did not get the answer and sends everything again: nothing new is recorded
        JsonNode again = json(send(key, day).andExpect(status().isOk()));
        again.get("results").forEach(r -> assertThat(r.get("duplicate").asBoolean()).isTrue());
        assertThat(timeEntryRepository.findAll()).hasSize(1);
        assertThat(attendanceEventRepository.findAll()).filteredOn(e -> "ACCEPTED".equals(e.getOutcome())).hasSize(4);
        assertThat(deviceEventRepository.findAll()).hasSize(5);
    }

    @Test
    void aSecondClockIn_isNeverTurnedIntoAClockOut() throws Exception {
        Instant in = Instant.now().minus(4, ChronoUnit.HOURS);
        send(key, List.of(event("DS-K1T-0001", "201", "1001", "checkIn", in))).andExpect(status().isOk());
        JsonNode second = json(send(key, List.of(event("DS-K1T-0001", "202", "1001", "checkIn", in.plus(3, ChronoUnit.HOURS))))
                .andExpect(status().isOk()));
        assertThat(statuses(second)).containsEntry("202", "REJECTED");
        assertThat(second.at("/results/0/message").asText()).contains("already clocked in");
        assertThat(timeEntryRepository.findAll()).hasSize(1);
        assertThat(timeEntryRepository.findAll().get(0).getClockOutAt()).isNull();
    }

    @Test
    void unknownDirection_goesToReview_andAManagerDecides_fixedDirectionTerminalsNeedNoReview() throws Exception {
        Instant at = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS); // devices report whole seconds
        JsonNode unclear = json(send(key, List.of(event("DS-K1T-0001", "301", "1001", null, at))).andExpect(status().isOk()));
        assertThat(statuses(unclear)).containsEntry("301", "NEEDS_REVIEW");
        assertThat(timeEntryRepository.findAll()).isEmpty();

        JsonNode queue = json(getJson("/hr/attendance-devices/events", manager).andExpect(status().isOk()));
        assertThat(queue).hasSize(1);
        String id = queue.get(0).get("id").asText();
        assertThat(queue.get(0).get("employeeName").asText()).isEqualTo("Kwesi Cook");

        postJson("/hr/attendance-devices/events/" + id + "/apply", Map.of("action", "CLOCK_IN", "note", "Kwesi confirmed he arrived"), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.appliedAction").value("CLOCK_IN"));
        assertThat(timeEntryRepository.findAll().get(0).getClockInAt()).isEqualTo(at);
        assertThat(json(getJson("/hr/attendance-devices/events", manager))).isEmpty();
        postJson("/hr/attendance-devices/events/" + id + "/apply", Map.of("action", "CLOCK_IN"), manager).andExpect(status().isConflict());

        // An exit-only reader: every scan is a clock-out, no review needed
        device("Back door (exit)", "DS-K1T-0002", "CLOCK_OUT");
        JsonNode out = json(send(key, List.of(event("DS-K1T-0002", "1", "1001", null, Instant.now().minusSeconds(60)))).andExpect(status().isOk()));
        assertThat(statuses(out)).containsEntry("1", "ACCEPTED");
        assertThat(timeEntryRepository.findAll().get(0).getClockOutAt()).isNotNull();
    }

    @Test
    void unknownPerson_isHeldUntilLinked_thenRetried() throws Exception {
        JsonNode result = json(send(key, List.of(event("DS-K1T-0001", "401", "2002", "checkIn", Instant.now().minusSeconds(600))))
                .andExpect(status().isOk()));
        assertThat(statuses(result)).containsEntry("401", "UNMATCHED");
        String id = json(getJson("/hr/attendance-devices/events", admin)).get(0).get("id").asText();
        getJson("/hr/attendance-devices/overview", admin).andExpect(jsonPath("$.reviewCounts.UNMATCHED").value(1));

        // Managers cannot change device settings or links
        postJson("/hr/attendance-devices/device-ids", Map.of("employeeId", cleaner, "deviceUserId", "2002"), manager)
                .andExpect(status().isForbidden());
        postJson("/hr/attendance-devices/device-ids", Map.of("employeeId", cleaner, "deviceUserId", "2002", "deviceId", reception), admin)
                .andExpect(status().isCreated());
        postJson("/hr/attendance-devices/events/" + id + "/retry", Map.of(), manager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.employeeName").value("Gifty Cleaner"));
    }

    @Test
    void eventsQueuedWhileOffline_forDays_areStillRecorded_withTheirOriginalTime() throws Exception {
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        JsonNode result = json(send(key, List.of(
                event("DS-K1T-0001", "501", "1001", "checkIn", twoDaysAgo),
                event("DS-K1T-0001", "502", "1001", "checkOut", twoDaysAgo.plus(8, ChronoUnit.HOURS)))).andExpect(status().isOk()));
        assertThat(statuses(result)).containsEntry("501", "ACCEPTED").containsEntry("502", "ACCEPTED");
        assertThat(timeEntryRepository.findAll().get(0).getClockInAt()).isEqualTo(twoDaysAgo);

        // A device clock running ahead is not trusted
        JsonNode ahead = json(send(key, List.of(event("DS-K1T-0001", "503", "1001", "checkIn", Instant.now().plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk()));
        assertThat(statuses(ahead)).containsEntry("503", "NEEDS_REVIEW");
    }

    @Test
    void keys_areChecked_revocable_andRotatable() throws Exception {
        send(null, List.of()).andExpect(status().isUnauthorized());
        send("rmgw_notarealkey_xxxxxxxxxxxxxxxx", List.of()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/attendance/gateway/config").header("Authorization", "Gateway " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayId").value("GATEWAY-001"))
                .andExpect(jsonPath("$.devices[0].serialNumber").value("DS-K1T-0001"))
                .andExpect(jsonPath("$.devices[0].directionMode").value("DEVICE_REPORTED"));
        assertThat(mockMvc.perform(get("/attendance/gateway/config").header("X-Gateway-Key", key)).andReturn().getResponse()
                .getContentAsString()).doesNotContain("password");

        postJson("/hr/attendance-devices/gateways/" + gatewayId + "/revoke", Map.of(), admin).andExpect(status().isOk());
        send(key, List.of()).andExpect(status().isUnauthorized());

        String newKey = json(postJson("/hr/attendance-devices/gateways/" + gatewayId + "/rotate-key", Map.of(), admin)
                .andExpect(status().isOk())).get("key").asText();
        send(key, List.of()).andExpect(status().isUnauthorized());
        send(newKey, List.of()).andExpect(status().isOk());

        postJson("/hr/attendance-devices/gateways", Map.of("name", "x", "gatewayIdentifier", "GW-2"), manager).andExpect(status().isForbidden());
    }

    @Test
    void heartbeat_updatesHealth_andTheOverviewWarnsAboutProblems() throws Exception {
        Map<String, Object> beat = new HashMap<>();
        beat.put("gatewayId", "GATEWAY-001");
        beat.put("softwareVersion", "1.0.0");
        beat.put("pendingEvents", 75);
        beat.put("failedSyncAttempts", 3);
        beat.put("lastError", "Connection refused");
        beat.put("devices", List.of(Map.of("serialNumber", "DS-K1T-0001", "status", "OFFLINE", "message", "No route to host",
                "clockOffsetSeconds", 300)));
        mockMvc.perform(post("/attendance/gateway/heartbeat").header("X-Gateway-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(beat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices[0].serialNumber").value("DS-K1T-0001"));

        JsonNode overview = json(getJson("/hr/attendance-devices/overview", manager).andExpect(status().isOk()));
        assertThat(overview.at("/gateways/0/status").asText()).isEqualTo("ONLINE");
        assertThat(overview.at("/gateways/0/pendingEvents").asInt()).isEqualTo(75);
        assertThat(overview.at("/devices/0/status").asText()).isEqualTo("OFFLINE");
        String alerts = overview.get("alerts").toString();
        assertThat(alerts).contains("Staff entrance is not connected", "75 events waiting", "clock is 5 min fast", "Connection refused");
    }
}
