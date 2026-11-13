package com.restaurantmanager.core.hr.device;

import com.restaurantmanager.core.hr.attendance.ClockAction;
import org.junit.jupiter.api.Test;

import static com.restaurantmanager.core.hr.device.AttendanceDeviceEntity.DirectionMode.CLOCK_IN;
import static com.restaurantmanager.core.hr.device.AttendanceDeviceEntity.DirectionMode.CLOCK_OUT;
import static com.restaurantmanager.core.hr.device.AttendanceDeviceEntity.DirectionMode.DEVICE_REPORTED;
import static org.assertj.core.api.Assertions.assertThat;

class DeviceEventProcessorUnitTest {

    @Test
    void hikvisionAttendanceStatuses_mapToClockActions() {
        assertThat(DeviceEventProcessor.reportedType("checkIn")).isEqualTo(DeviceEventEntity.ReportedType.CLOCK_IN);
        assertThat(DeviceEventProcessor.reportedType("checkOut")).isEqualTo(DeviceEventEntity.ReportedType.CLOCK_OUT);
        assertThat(DeviceEventProcessor.reportedType("breakOut")).isEqualTo(DeviceEventEntity.ReportedType.BREAK_OUT);
        assertThat(DeviceEventProcessor.reportedType("breakIn")).isEqualTo(DeviceEventEntity.ReportedType.BREAK_IN);
        assertThat(DeviceEventProcessor.reportedType("overtimeIn")).isEqualTo(DeviceEventEntity.ReportedType.CLOCK_IN);
        assertThat(DeviceEventProcessor.reportedType("CLOCK_OUT")).isEqualTo(DeviceEventEntity.ReportedType.CLOCK_OUT);
        assertThat(DeviceEventProcessor.reportedType("undefined")).isEqualTo(DeviceEventEntity.ReportedType.UNKNOWN);
        assertThat(DeviceEventProcessor.reportedType(null)).isEqualTo(DeviceEventEntity.ReportedType.UNKNOWN);
    }

    @Test
    void theDevicesOwnReportWins_thenTheTerminalDirection_neverAGuess() {
        assertThat(DeviceEventProcessor.action(DeviceEventEntity.ReportedType.CLOCK_OUT, CLOCK_IN)).isEqualTo(ClockAction.CLOCK_OUT);
        assertThat(DeviceEventProcessor.action(DeviceEventEntity.ReportedType.BREAK_OUT, DEVICE_REPORTED)).isEqualTo(ClockAction.BREAK_START);
        assertThat(DeviceEventProcessor.action(DeviceEventEntity.ReportedType.UNKNOWN, CLOCK_OUT)).isEqualTo(ClockAction.CLOCK_OUT);
        // No report and no fixed direction: nothing is assumed (the event goes to review)
        assertThat(DeviceEventProcessor.action(DeviceEventEntity.ReportedType.UNKNOWN, DEVICE_REPORTED)).isNull();
    }

    @Test
    void gatewayKeys_areRandom_andOnlyTheirHashIsKept() {
        GatewayService.IssuedKey a = GatewayService.newKey();
        GatewayService.IssuedKey b = GatewayService.newKey();
        assertThat(a.key()).startsWith(a.prefix() + "_").isNotEqualTo(b.key());
        assertThat(a.prefix()).hasSize(15).startsWith("rmgw_");
        assertThat(a.hash()).hasSize(64).isEqualTo(GatewayService.hash(a.key())).doesNotContain(a.key());
    }
}
