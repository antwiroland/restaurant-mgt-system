package com.restaurantmanager.gateway.hikvision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.device.CapturedEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a fake ISAPI device that, like the real ones, demands Digest authentication. */
class HikvisionIsapiConnectorTest {
    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final AtomicInteger searchCalls = new AtomicInteger();
    private final List<String> searchBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startDevice() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ISAPI/System/deviceInfo", ex -> respond(ex, "application/xml",
                "<DeviceInfo><deviceName>Staff entrance</deviceName><model>DS-K1T341AMF</model><serialNumber>DS-K1T341AMF20231101V0101ENAB1234567</serialNumber></DeviceInfo>"));
        server.createContext("/ISAPI/System/time", ex -> respond(ex, "application/xml",
                "<Time><timeMode>NTP</timeMode><localTime>" + Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.SECONDS).toString().replace("Z", "+00:00")
                        + "</localTime></Time>"));
        server.createContext("/ISAPI/AccessControl/AcsEvent", ex -> {
            if (!authorized(ex)) return;
            searchBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = searchCalls.incrementAndGet() == 1
                    ? "{\"AcsEvent\":{\"responseStatusStrg\":\"MORE\",\"numOfMatches\":1,\"InfoList\":[{\"major\":5,\"minor\":75,\"time\":\"2026-11-13T07:55:00+00:00\",\"employeeNoString\":\"1001\",\"serialNo\":1,\"attendanceStatus\":\"checkIn\"}]}}"
                    : "{\"AcsEvent\":{\"responseStatusStrg\":\"OK\",\"numOfMatches\":1,\"InfoList\":[{\"major\":5,\"minor\":38,\"time\":\"2026-11-13T17:05:00+00:00\",\"employeeNoString\":\"1001\",\"serialNo\":2,\"attendanceStatus\":\"checkOut\"}]}}";
            respond(ex, "application/json", body);
        });
        server.createContext("/ISAPI/Event/notification/alertStream", ex -> {
            if (!authorized(ex)) return;
            ex.getResponseHeaders().add("Content-Type", "multipart/mixed; boundary=MIME_boundary");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                part(out, "{\"eventType\":\"videoloss\",\"eventState\":\"inactive\"}");
                part(out, "{\"dateTime\":\"2026-11-13T12:00:00+00:00\",\"eventType\":\"AccessControllerEvent\",\"AccessControllerEvent\":"
                        + "{\"majorEventType\":5,\"subEventType\":75,\"employeeNoString\":\"1001\",\"serialNo\":3,\"attendanceStatus\":\"breakOut\"}}");
                out.write("--MIME_boundary--\r\n".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void stopDevice() {
        server.stop(0);
    }

    /** First answer 401 with a Digest challenge; then require a Digest answer for user admin. */
    private boolean authorized(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Digest ") || !auth.contains("username=\"admin\"") || !auth.contains("response=\"")) {
            ex.getResponseHeaders().add("WWW-Authenticate", "Digest realm=\"DS-K1T341\", qop=\"auth\", nonce=\"abc123\", opaque=\"xyz\"");
            ex.sendResponseHeaders(401, -1);
            ex.close();
            return false;
        }
        authHeaders.add(auth);
        return true;
    }

    private void respond(HttpExchange ex, String type, String body) throws IOException {
        if (!authorized(ex)) return;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void part(OutputStream out, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        out.write(("--MIME_boundary\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private HikvisionIsapiConnector connector() {
        return new HikvisionIsapiConnector("DS-K1T341-0001", "127.0.0.1", server.getAddress().getPort(), false, "admin", "Passw0rd!",
                Set.of(1, 38, 75), ZoneId.of("Africa/Accra"));
    }

    @Test
    void probe_readsIdentityAndClockOffset_withDigestLogin() throws Exception {
        try (HikvisionIsapiConnector c = connector()) {
            var probe = c.probe();
            assertThat(probe.model()).isEqualTo("DS-K1T341AMF");
            assertThat(probe.serialNumber()).contains("DS-K1T341AMF");
            assertThat(probe.clockOffsetSeconds()).isBetween(290, 310); // device 5 minutes fast
        }
        assertThat(authHeaders).isNotEmpty().allMatch(h -> h.contains("realm=\"DS-K1T341\""));
    }

    @Test
    void search_followsPagesUntilTheDeviceSaysOk() throws Exception {
        try (HikvisionIsapiConnector c = connector()) {
            List<CapturedEvent> events = c.search(Instant.parse("2026-11-13T00:00:00Z"), Instant.parse("2026-11-14T00:00:00Z"));
            assertThat(events).extracting(CapturedEvent::deviceEventId).containsExactly("1", "2");
            assertThat(events).extracting(CapturedEvent::eventType).containsExactly("checkIn", "checkOut");
        }
        assertThat(searchCalls.get()).isEqualTo(2);
        var first = new ObjectMapper().readTree(searchBodies.get(0)).path("AcsEventCond");
        var second = new ObjectMapper().readTree(searchBodies.get(1)).path("AcsEventCond");
        assertThat(first.path("major").asInt()).isEqualTo(5);
        assertThat(first.path("startTime").asText()).isEqualTo("2026-11-13T00:00:00Z");
        assertThat(second.path("searchResultPosition").asInt()).isEqualTo(1);
        assertThat(second.path("searchID").asText()).isEqualTo(first.path("searchID").asText());
    }

    @Test
    void alertStream_deliversAttendanceScansOnly() throws Exception {
        List<CapturedEvent> received = new ArrayList<>();
        try (HikvisionIsapiConnector c = connector()) {
            c.listen(received::add);
        }
        assertThat(received).hasSize(1);
        assertThat(received.get(0).eventType()).isEqualTo("breakOut");
        assertThat(received.get(0).deviceEventId()).isEqualTo("3");
    }
}
