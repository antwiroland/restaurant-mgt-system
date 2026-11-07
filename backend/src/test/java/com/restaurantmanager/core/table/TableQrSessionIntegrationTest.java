package com.restaurantmanager.core.table;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TableQrSessionIntegrationTest extends BaseIntegrationTest {
    private UserEntity cashier;
    private RestaurantTableEntity table;

    @BeforeEach
    void setUpData() {
        cashier = createUser("Cashier", "+233260000001", "qr-cashier@x.com", "secret123", Role.CASHIER);
        table = new RestaurantTableEntity();
        table.setNumber("Q1");
        table.setCapacity(4);
        table.setZone("Main");
        table.setStatus(TableStatus.OCCUPIED);
        table.setQrToken("q1-" + Instant.now().toEpochMilli());
        table = restaurantTableRepository.save(table);
    }

    @Test
    void givenGuestScan_whenSessionLinked_thenStaffSeesPersistedActiveSession() throws Exception {
        link("phone-a");
        link("phone-b");
        link("phone-a"); // same device again: no duplicate

        mockMvc.perform(get("/qr/sessions/Q1/active")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sessionCount").value(2));
        assertEquals(2, tableMobileSessionRepository.count());
    }

    @Test
    void givenActiveSessions_whenStaffClosesTableSession_thenNoActiveSessions() throws Exception {
        link("phone-a");

        mockMvc.perform(delete("/qr/sessions/Q1")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/qr/sessions/Q1/active")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sessionCount").value(0));
    }

    @Test
    void givenActiveSessions_whenTableClosed_thenSessionsClosedAutomatically() throws Exception {
        link("phone-a");
        table.setStatus(TableStatus.OCCUPIED);
        restaurantTableRepository.save(table);

        mockMvc.perform(post("/orders/dine-in/tables/" + table.getId() + "/close")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/qr/sessions/Q1/active")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void givenUnknownTableToken_whenLinking_then404() throws Exception {
        mockMvc.perform(post("/qr/sessions/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"nope\",\"sessionId\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenStaff_whenDownloadingTableCard_thenRealPdfReturned() throws Exception {
        byte[] pdf = mockMvc.perform(get("/qr/tables/Q1/pdf")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("table-Q1-qr.pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.contains("/scan/" + table.getQrToken()));
    }

    private void link(String sessionId) throws Exception {
        mockMvc.perform(post("/qr/sessions/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableToken\":\"" + table.getQrToken() + "\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value("Q1"));
    }
}
