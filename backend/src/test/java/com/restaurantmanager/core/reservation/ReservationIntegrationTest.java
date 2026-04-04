package com.restaurantmanager.core.reservation;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.table.RestaurantTableEntity;
import com.restaurantmanager.core.table.TableStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenAvailableTableAndTime_whenCreateReservation_then201() throws Exception {
        RestaurantTableEntity table = createTable("R1");
        Instant reservedAt = Instant.now().plusSeconds(86400);

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"tableId\":\"" + table.getId() + "\"," +
                                "\"customerName\":\"Kwame\"," +
                                "\"customerPhone\":\"+233200300001\"," +
                                "\"partySize\":4," +
                                "\"reservedAt\":\"" + reservedAt + "\"," +
                                "\"durationMins\":90}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(table.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void givenSameTableAndOverlappingTime_whenCreateReservation_then409() throws Exception {
        RestaurantTableEntity table = createTable("R2");
        Instant reservedAt = Instant.now().plusSeconds(172800);

        mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" +
                        "\"tableId\":\"" + table.getId() + "\"," +
                        "\"customerName\":\"Kwame\"," +
                        "\"customerPhone\":\"+233200300002\"," +
                        "\"partySize\":4," +
                        "\"reservedAt\":\"" + reservedAt + "\"," +
                        "\"durationMins\":90}"));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"tableId\":\"" + table.getId() + "\"," +
                                "\"customerName\":\"Ama\"," +
                                "\"customerPhone\":\"+233200300003\"," +
                                "\"partySize\":2," +
                                "\"reservedAt\":\"" + reservedAt.plusSeconds(1800) + "\"," +
                                "\"durationMins\":90}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenNoAuth_whenCreateReservationWithNameAndPhone_then201GuestReservation() throws Exception {
        RestaurantTableEntity table = createTable("R3");
        Instant reservedAt = Instant.now().plusSeconds(259200);

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"tableId\":\"" + table.getId() + "\"," +
                                "\"customerName\":\"Guest User\"," +
                                "\"customerPhone\":\"+233200300004\"," +
                                "\"partySize\":3," +
                                "\"reservedAt\":\"" + reservedAt + "\"," +
                                "\"durationMins\":90}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Guest User"));
    }

    @Test
    void givenStaffToken_whenConfirmReservation_then200StatusConfirmed() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200300005", "cashier+res1@x.com", "secret123", Role.CASHIER);
        ReservationEntity reservation = createReservation(createTable("R4"), Instant.now().plusSeconds(345600), null);

        mockMvc.perform(patch("/reservations/" + reservation.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void givenCustomerToken_whenCancelOwnReservation_then204() throws Exception {
        UserEntity customer = createUser("Customer", "+233200300006", "customer+res1@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("R5");
        Instant reservedAt = Instant.now().plusSeconds(432000);

        String body = mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"tableId\":\"" + table.getId() + "\"," +
                                "\"partySize\":2," +
                                "\"reservedAt\":\"" + reservedAt + "\"," +
                                "\"durationMins\":90}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reservationId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(delete("/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenCustomerToken_whenCancelOtherCustomerReservation_then403() throws Exception {
        UserEntity customer1 = createUser("Customer1", "+233200300007", "customer+res2@x.com", "secret123", Role.CUSTOMER);
        UserEntity customer2 = createUser("Customer2", "+233200300008", "customer+res3@x.com", "secret123", Role.CUSTOMER);

        ReservationEntity reservation = createReservation(createTable("R6"), Instant.now().plusSeconds(518400), customer1.getId());

        mockMvc.perform(delete("/reservations/" + reservation.getId())
                        .header("Authorization", "Bearer " + accessToken(customer2)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenDateFilter_whenListReservations_thenOnlyReservationsForThatDateReturned() throws Exception {
        UserEntity manager = createUser("Manager", "+233200300009", "manager+res1@x.com", "secret123", Role.MANAGER);
        RestaurantTableEntity table = createTable("R7");

        createReservation(table, Instant.parse("2026-04-10T18:00:00Z"), null);
        createReservation(table, Instant.parse("2026-04-11T18:00:00Z"), null);

        mockMvc.perform(get("/reservations")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .param("date", "2026-04-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void givenCustomerToken_whenListOwnReservations_thenOnlyCustomerReservationsReturned() throws Exception {
        UserEntity customer1 = createUser("Customer4", "+233200300010", "customer+res4@x.com", "secret123", Role.CUSTOMER);
        UserEntity customer2 = createUser("Customer5", "+233200300011", "customer+res5@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("R8");

        createReservation(table, Instant.parse("2026-04-12T18:00:00Z"), customer1.getId());
        createReservation(table, Instant.parse("2026-04-13T18:00:00Z"), customer2.getId());

        mockMvc.perform(get("/reservations/mine")
                        .header("Authorization", "Bearer " + accessToken(customer1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Kwame"));
    }

    @Test
    void givenGuestPhone_whenLookupGuestReservations_thenOnlyMatchingGuestReservationsReturned() throws Exception {
        RestaurantTableEntity table = createTable("R9");
        createReservation(table, Instant.parse("2026-04-14T18:00:00Z"), null);
        ReservationEntity other = createReservation(table, Instant.parse("2026-04-15T18:00:00Z"), null);
        other.setCustomerPhone("+233200300099");
        reservationRepository.save(other);

        mockMvc.perform(post("/reservations/guest/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+233200399999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerPhone").value("+233200399999"));
    }

    @Test
    void givenGuestPhone_whenCancelMatchingGuestReservation_then204() throws Exception {
        ReservationEntity reservation = createReservation(createTable("R10"), Instant.now().plusSeconds(604800), null);

        mockMvc.perform(post("/reservations/" + reservation.getId() + "/guest-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+233200399999"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/reservations/guest/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+233200399999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void givenWrongGuestPhone_whenCancelGuestReservation_then403() throws Exception {
        ReservationEntity reservation = createReservation(createTable("R11"), Instant.now().plusSeconds(691200), null);

        mockMvc.perform(post("/reservations/" + reservation.getId() + "/guest-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"+233200300123"}
                                """))
                .andExpect(status().isForbidden());
    }

    private RestaurantTableEntity createTable(String number) {
        RestaurantTableEntity table = new RestaurantTableEntity();
        table.setNumber(number);
        table.setCapacity(4);
        table.setZone("Main Hall");
        table.setStatus(TableStatus.AVAILABLE);
        table.setQrToken(number + "-token");
        return restaurantTableRepository.save(table);
    }

    private ReservationEntity createReservation(RestaurantTableEntity table, Instant reservedAt, java.util.UUID customerUserId) {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setTable(table);
        reservation.setCustomerName("Kwame");
        reservation.setCustomerPhone("+233200399999");
        reservation.setCustomerUserId(customerUserId);
        reservation.setPartySize(4);
        reservation.setReservedAt(reservedAt);
        reservation.setDurationMins(90);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setNotes(null);
        return reservationRepository.save(reservation);
    }
}
