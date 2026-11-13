package com.restaurantmanager.gateway.device;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * One vendor's way of talking to an attendance device. Hikvision (ISAPI) is implemented; ZKTeco or others plug in by
 * adding another implementation and choosing it by the vendor set in the backend. Nothing else in the gateway changes.
 */
public interface DeviceConnector extends AutoCloseable {

    /** Checks the device answers and reads its clock. */
    DeviceProbe probe() throws Exception;

    /**
     * Listens for scans as they happen, calling {@code onEvent} for each. Blocks until the connection drops or
     * {@link #close()} is called; the caller reconnects.
     */
    void listen(Consumer<CapturedEvent> onEvent) throws Exception;

    /** Scans stored on the device between two times (to recover anything missed while disconnected). */
    List<CapturedEvent> search(Instant from, Instant to) throws Exception;

    @Override
    void close();

    /**
     * @param serialNumber        what the device reports as its serial number (may differ from the registered one)
     * @param model               device model
     * @param clockOffsetSeconds  device clock minus this computer's clock
     */
    record DeviceProbe(String serialNumber, String model, Integer clockOffsetSeconds) {}
}
