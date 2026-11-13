package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationTableHoldIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ReservationTableHolds tableHolds;

    @Test
    void givenReservationWithinTheHour_whenSync_thenFreeTableIsReserved() {
        Instant now = Instant.now();
        RestaurantTableEntity table = createTable("H1", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.plus(50, ChronoUnit.MINUTES), null);

        tableHolds.sync(now);

        assertThat(tableStatus(table)).isEqualTo(TableStatus.RESERVED);
        assertThat(reload(reservation).getTableHeldAt()).isNotNull();
    }

    @Test
    void givenReservationMoreThanAnHourAway_whenSync_thenTableStaysAvailable() {
        Instant now = Instant.now();
        RestaurantTableEntity table = createTable("H2", TableStatus.AVAILABLE);
        createReservation(table, now.plus(90, ChronoUnit.MINUTES), null);

        tableHolds.sync(now);

        assertThat(tableStatus(table)).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void givenGuestStillAtTable_whenSync_thenNotInterruptedAndHeldOnceFree() {
        Instant now = Instant.now();
        RestaurantTableEntity table = createTable("H3", TableStatus.OCCUPIED);
        ReservationEntity reservation = createReservation(table, now.plus(30, ChronoUnit.MINUTES), null);

        tableHolds.sync(now);
        assertThat(tableStatus(table)).isEqualTo(TableStatus.OCCUPIED);
        assertThat(reload(reservation).getTableHeldAt()).isNull();

        setTableStatus(table, TableStatus.AVAILABLE); // the earlier guest paid and left
        tableHolds.sync(now.plus(1, ChronoUnit.MINUTES));
        assertThat(tableStatus(table)).isEqualTo(TableStatus.RESERVED);
    }

    @Test
    void givenHeldTable_whenCustomerCancels_thenTableReleased() throws Exception {
        Instant now = Instant.now();
        UserEntity customer = createUser("Customer", "+233200310001", "customer+hold1@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("H4", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.plus(40, ChronoUnit.MINUTES), customer.getId());
        tableHolds.sync(now);
        assertThat(tableStatus(table)).isEqualTo(TableStatus.RESERVED);

        mockMvc.perform(delete("/reservations/" + reservation.getId())
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isNoContent());

        assertThat(tableStatus(table)).isEqualTo(TableStatus.AVAILABLE);
        assertThat(reload(reservation).getTableHeldAt()).isNull();
    }

    @Test
    void givenHeldTable_whenStaffCancelsReservation_thenTableReleased() throws Exception {
        Instant now = Instant.now();
        UserEntity cashier = createUser("Cashier", "+233200310002", "cashier+hold1@x.com", "secret123", Role.CASHIER);
        RestaurantTableEntity table = createTable("H5", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.plus(40, ChronoUnit.MINUTES), null);
        tableHolds.sync(now);

        mockMvc.perform(patch("/reservations/" + reservation.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        assertThat(tableStatus(table)).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void givenCancelledReservationWhileGuestSeated_whenCancelled_thenOccupiedTableUntouched() throws Exception {
        Instant now = Instant.now();
        UserEntity cashier = createUser("Cashier", "+233200310003", "cashier+hold2@x.com", "secret123", Role.CASHIER);
        RestaurantTableEntity table = createTable("H6", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.plus(40, ChronoUnit.MINUTES), null);
        tableHolds.sync(now);
        setTableStatus(table, TableStatus.OCCUPIED); // staff let a walk-in sit regardless

        mockMvc.perform(patch("/reservations/" + reservation.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        assertThat(tableStatus(table)).isEqualTo(TableStatus.OCCUPIED);
    }

    @Test
    void givenNoShowPastGracePeriod_whenSync_thenTableReleased() {
        Instant now = Instant.now();
        RestaurantTableEntity table = createTable("H7", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.minus(20, ChronoUnit.MINUTES), null);
        tableHolds.sync(now);
        assertThat(tableStatus(table)).isEqualTo(TableStatus.RESERVED);

        tableHolds.sync(now.plus(11, ChronoUnit.MINUTES)); // 31 minutes after the booked time, nobody seated

        assertThat(tableStatus(table)).isEqualTo(TableStatus.AVAILABLE);
        assertThat(reload(reservation).getTableHeldAt()).isNull();
    }

    @Test
    void givenPartySeatedAndPaid_whenSync_thenTableNotReservedAgain() throws Exception {
        Instant now = Instant.now();
        UserEntity admin = createUser("Admin", "+233200310004", "admin+hold1@x.com", "secret123", Role.ADMIN);
        RestaurantTableEntity table = createTable("H8", TableStatus.AVAILABLE);
        ReservationEntity reservation = createReservation(table, now.plus(10, ChronoUnit.MINUTES), null);
        tableHolds.sync(now);

        mockMvc.perform(patch("/tables/" + table.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OCCUPIED\"}"))
                .andExpect(status().isOk());
        assertThat(reload(reservation).getSeatedAt()).isNotNull();

        setTableStatus(table, TableStatus.AVAILABLE); // bill paid, party gone
        tableHolds.sync(now.plus(20, ChronoUnit.MINUTES));

        assertThat(tableStatus(table)).isEqualTo(TableStatus.AVAILABLE);
    }

    private TableStatus tableStatus(RestaurantTableEntity table) {
        return restaurantTableRepository.findById(table.getId()).orElseThrow().getStatus();
    }

    private void setTableStatus(RestaurantTableEntity table, TableStatus status) {
        RestaurantTableEntity fresh = restaurantTableRepository.findById(table.getId()).orElseThrow();
        fresh.setStatus(status);
        restaurantTableRepository.save(fresh);
    }

    private ReservationEntity reload(ReservationEntity reservation) {
        return reservationRepository.findById(reservation.getId()).orElseThrow();
    }

    private RestaurantTableEntity createTable(String number, TableStatus status) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main Hall");
        table.setStatus(status);
        table.setQrToken(number + "-hold-token");
        return restaurantTableRepository.save(table);
    }

    private ReservationEntity createReservation(RestaurantTableEntity table, Instant reservedAt, UUID customerUserId) {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setTable(table);
        reservation.setCustomerName("Kwame");
        reservation.setCustomerPhone("+233200399999");
        reservation.setCustomerUserId(customerUserId);
        reservation.setPartySize(4);
        reservation.setReservedAt(reservedAt);
        reservation.setDurationMins(90);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservationRepository.save(reservation);
    }
}
