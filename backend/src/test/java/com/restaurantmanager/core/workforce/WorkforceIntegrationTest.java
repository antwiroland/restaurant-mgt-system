package com.restaurantmanager.core.workforce;

import com.restaurantmanager.core.hr.HrIntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The quick rota (/scheduling) and time clock. Shifts go through HR scheduling, so staff need an employee record. */
class WorkforceIntegrationTest extends HrIntegrationTestSupport {
    private UserEntity manager;
    private UserEntity cashier;

    @BeforeEach
    void setUpData() {
        var accra = branch("WF1");
        UserEntity admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Manager", Role.MANAGER, accra);
        cashier = staff("Efo", Role.CASHIER, accra);
        try {
            UUID employee = createEmployee(admin, "Efo", "Mensah", accra);
            postJson("/hr/employees/" + employee + "/link-user", Map.of("userId", cashier.getId()), admin)
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void givenShift_whenOverlappingShiftScheduled_then409() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        schedule(start, start.plus(8, ChronoUnit.HOURS)).andExpect(status().isCreated());
        schedule(start.plus(4, ChronoUnit.HOURS), start.plus(10, ChronoUnit.HOURS)).andExpect(status().isConflict());
        // Back-to-back is not an overlap, but HR's rest rule warns; it saves once the manager confirms.
        schedule(start.plus(8, ChronoUnit.HOURS), start.plus(12, ChronoUnit.HOURS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.issues[0].code").value("SHORT_REST"));
        schedule(start.plus(8, ChronoUnit.HOURS), start.plus(12, ChronoUnit.HOURS), true).andExpect(status().isCreated());

        mockMvc.perform(get("/scheduling/my-shifts")
                        .param("from", start.minus(1, ChronoUnit.DAYS).toString())
                        .param("to", start.plus(2, ChronoUnit.DAYS).toString())
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userName").value("Efo"));
    }

    @Test
    void givenManager_whenListingStaff_thenCustomersAreExcludedAndCashiersCannotList() throws Exception {
        createUser("Cust", "+233340000008", "c8@x.com", "secret123", Role.CUSTOMER);
        mockMvc.perform(get("/scheduling/staff").header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'CUSTOMER')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Efo')].role").value("CASHIER"));
        mockMvc.perform(get("/scheduling/staff").header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCustomer_whenScheduled_then400() throws Exception {
        UserEntity customer = createUser("Cust", "+233340000009", "c9@x.com", "secret123", Role.CUSTOMER);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        mockMvc.perform(post("/scheduling/shifts")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}".formatted(customer.getId(), start, start.plus(4, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenScheduledShift_whenStaffClocksInLateAndWorksLong_thenTimesheetShowsLatenessAndOvertime() throws Exception {
        Instant shiftStart = Instant.now().minus(20, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        schedule(shiftStart, shiftStart.plus(8, ChronoUnit.HOURS)).andExpect(status().isCreated());

        String body = mockMvc.perform(post("/timeclock/clock-in")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").isNotEmpty())
                .andExpect(jsonPath("$.open").value(true))
                .andReturn().getResponse().getContentAsString();
        String entryId = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(post("/timeclock/clock-in")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/timeclock/clock-out")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"breakMinutes\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        // Manager corrects the entry: worked 10h with a 30 min break (9h30 worked -> 90 min overtime)
        mockMvc.perform(put("/timeclock/entries/" + entryId)
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clockInAt\":\"%s\",\"clockOutAt\":\"%s\",\"breakMinutes\":30,\"reason\":\"Forgot to clock out\"}"
                                .formatted(shiftStart.plus(15, ChronoUnit.MINUTES), shiftStart.plus(615, ChronoUnit.MINUTES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(570))
                .andExpect(jsonPath("$.lateMinutes").value(15));

        LocalDate day = shiftStart.atZone(ZoneId.of("Africa/Accra")).toLocalDate();
        mockMvc.perform(get("/timeclock/timesheet")
                        .param("from", day.minusDays(1).toString())
                        .param("to", day.plusDays(1).toString())
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Efo"))
                .andExpect(jsonPath("$[0].workedMinutes").value(570))
                .andExpect(jsonPath("$[0].scheduledMinutes").value(480))
                .andExpect(jsonPath("$[0].overtimeMinutes").value(90))
                .andExpect(jsonPath("$[0].lateArrivals").value(1));

        mockMvc.perform(get("/timeclock/timesheet")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenNotClockedIn_whenClockingOut_then409() throws Exception {
        mockMvc.perform(post("/timeclock/clock-out")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/timeclock/status")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isNoContent());
    }

    @Test
    void givenShift_whenDeleted_thenGone() throws Exception {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        String id = objectMapper.readTree(schedule(start, start.plus(6, ChronoUnit.HOURS))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(delete("/scheduling/shifts/" + id)
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions schedule(Instant start, Instant end) throws Exception {
        return schedule(start, end, false);
    }

    private org.springframework.test.web.servlet.ResultActions schedule(Instant start, Instant end, boolean acknowledge) throws Exception {
        return mockMvc.perform(post("/scheduling/shifts")
                .header("Authorization", "Bearer " + accessToken(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"position\":\"Cashier\",\"acknowledgeWarnings\":%s}"
                        .formatted(cashier.getId(), start, end, acknowledge)));
    }
}
