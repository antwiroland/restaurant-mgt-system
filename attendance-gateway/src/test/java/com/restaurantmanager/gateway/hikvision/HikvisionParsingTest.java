package com.restaurantmanager.gateway.hikvision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantmanager.gateway.device.CapturedEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HikvisionParsingTest {
    private final HikvisionEventParser parser = new HikvisionEventParser("DS-K1T341-0001", Set.of(1, 38, 75), ZoneId.of("Africa/Accra"));

    @Test
    void faceScanFromTheAlertStream_keepsTheDeviceTimeAndStatus() {
        String json = """
                {"ipAddress":"192.168.1.60","dateTime":"2026-11-13T07:55:00+00:00","eventType":"AccessControllerEvent",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":75,"employeeNoString":"1001","serialNo":10234,
                 "attendanceStatus":"checkIn","currentVerifyMode":"face","name":"John Mensah"}}""";
        CapturedEvent e = parser.fromAlert(json).orElseThrow();
        assertThat(e.deviceSerialNumber()).isEqualTo("DS-K1T341-0001");
        assertThat(e.deviceEventId()).isEqualTo("10234");
        assertThat(e.employeeDeviceId()).isEqualTo("1001");
        assertThat(e.eventType()).isEqualTo("checkIn");
        assertThat(e.authenticationMethod()).isEqualTo("FACE");
        assertThat(e.deviceTimestamp()).isEqualTo(Instant.parse("2026-11-13T07:55:00Z"));
        assertThat(e.rawEvent()).contains("John Mensah");
    }

    @Test
    void noAttendanceStatus_isPassedOnAsUnknown_neverGuessed() {
        String json = """
                {"dateTime":"2026-11-13T12:00:00+00:00","eventType":"AccessControllerEvent",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":38,"employeeNoString":"1002","serialNo":7,"attendanceStatus":"undefined"}}""";
        CapturedEvent e = parser.fromAlert(json).orElseThrow();
        assertThat(e.eventType()).isNull();
        assertThat(e.authenticationMethod()).isEqualTo("FINGERPRINT");
    }

    @Test
    void doorAlarmsHeartbeatsAndFailedScans_areIgnored() {
        assertThat(parser.fromAlert("{\"eventType\":\"videoloss\",\"eventState\":\"inactive\"}")).isEmpty();
        // major 5 minor 76 = face authentication failed (not in the accepted list)
        assertThat(parser.fromAlert("""
                {"dateTime":"2026-11-13T12:00:00+00:00","eventType":"AccessControllerEvent",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":76,"employeeNoString":"1001","serialNo":8}}""")).isEmpty();
        // a stranger (no employee number)
        assertThat(parser.fromAlert("""
                {"dateTime":"2026-11-13T12:00:00+00:00","eventType":"AccessControllerEvent",
                 "AccessControllerEvent":{"majorEventType":5,"subEventType":75,"serialNo":9}}""")).isEmpty();
        assertThat(parser.fromAlert("not json")).isEmpty();
    }

    @Test
    void searchResults_andTimesWithoutAnOffsetUseTheDeviceZone() throws Exception {
        var acs = new ObjectMapper().readTree("""
                {"AcsEvent":{"responseStatusStrg":"OK","numOfMatches":2,"InfoList":[
                  {"major":5,"minor":1,"time":"2026-11-13T17:10:00","employeeNoString":"1003","serialNo":55,"attendanceStatus":"checkOut","currentVerifyMode":"card"},
                  {"major":3,"minor":1024,"time":"2026-11-13T17:11:00+00:00","serialNo":56}]}}""").path("AcsEvent");
        List<CapturedEvent> events = parser.fromSearch(acs);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).deviceTimestamp()).isEqualTo(Instant.parse("2026-11-13T17:10:00Z"));
        assertThat(events.get(0).authenticationMethod()).isEqualTo("CARD");
        assertThat(events.get(0).eventType()).isEqualTo("checkOut");
    }

    @Test
    void multipartStream_isSplitIntoParts_withAndWithoutLengths() throws Exception {
        String stream = """
                --MIME_boundary\r
                Content-Type: application/json; charset="UTF-8"\r
                Content-Length: 21\r
                \r
                {"eventType":"first"}\r
                --MIME_boundary\r
                Content-Type: image/jpeg\r
                Content-Length: 4\r
                \r
                JPEG\r
                --MIME_boundary\r
                Content-Type: application/json\r
                \r
                {"eventType":"second"}\r
                --MIME_boundary--\r
                """;
        MultipartStreamReader reader = new MultipartStreamReader(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),
                MultipartStreamReader.boundaryOf("multipart/mixed; boundary=MIME_boundary"));
        MultipartStreamReader.Part first = reader.next();
        assertThat(first.text()).isEqualTo("{\"eventType\":\"first\"}");
        assertThat(reader.next().contentType()).isEqualTo("image/jpeg");
        MultipartStreamReader.Part third = reader.next();
        assertThat(third.text()).isEqualTo("{\"eventType\":\"second\"}");
        assertThat(reader.next()).isNull();
    }
}
