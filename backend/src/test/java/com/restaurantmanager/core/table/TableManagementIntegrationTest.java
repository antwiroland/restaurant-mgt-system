package com.restaurantmanager.core.table;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TableManagementIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenAdminToken_whenCreateTable_then201WithUniqueQrToken() throws Exception {
        UserEntity admin = createUser("Admin", "+233200200001", "admin+table1@x.com", "secret123", Role.ADMIN);

        mockMvc.perform(post("/tables")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"T1\",\"capacity\":4,\"zone\":\"Main Hall\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("T1"))
                .andExpect(jsonPath("$.qrToken").isString());
    }

    @Test
    void givenTwoTables_whenListTables_thenBothReturnedWithStatus() throws Exception {
        UserEntity manager = createUser("Manager", "+233200200002", "manager+table1@x.com", "secret123", Role.MANAGER);
        createTable("T1");
        createTable("T2");

        mockMvc.perform(get("/tables")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void givenCashierToken_whenUpdateTableStatus_then200() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233200200003", "cashier+table1@x.com", "secret123", Role.CASHIER);
        RestaurantTableEntity table = createTable("T3");

        mockMvc.perform(patch("/tables/" + table.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OCCUPIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void givenCustomerToken_whenUpdateTableStatus_then403() throws Exception {
        UserEntity customer = createUser("Customer", "+233200200004", "customer+table1@x.com", "secret123", Role.CUSTOMER);
        RestaurantTableEntity table = createTable("T4");

        mockMvc.perform(patch("/tables/" + table.getId() + "/status")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OCCUPIED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenValidQrToken_whenScanTable_then200WithTableInfo() throws Exception {
        RestaurantTableEntity table = createTable("T5");

        mockMvc.perform(post("/tables/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + table.getQrToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(table.getId().toString()))
                .andExpect(jsonPath("$.tableNumber").value("T5"));
    }

    @Test
    void givenInvalidQrToken_whenScanTable_then404() throws Exception {
        mockMvc.perform(post("/tables/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"invalid-token\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenDuplicateTableNumber_whenCreate_then409() throws Exception {
        UserEntity admin = createUser("Admin", "+233200200005", "admin+table2@x.com", "secret123", Role.ADMIN);
        createTable("T6");

        mockMvc.perform(post("/tables")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"T6\",\"capacity\":4,\"zone\":\"Main Hall\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenManagerToken_whenGetTableQrImage_then200Png() throws Exception {
        UserEntity manager = createUser("Manager", "+233200200006", "manager+table2@x.com", "secret123", Role.MANAGER);
        RestaurantTableEntity table = createTable("T7");

        mockMvc.perform(get("/tables/" + table.getId() + "/qr-image?payload=https://example.com/scan/" + table.getQrToken())
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void givenAdminToken_whenDeleteTable_then204AndTableRemoved() throws Exception {
        UserEntity admin = createUser("Admin", "+233200200007", "admin+table3@x.com", "secret123", Role.ADMIN);
        RestaurantTableEntity table = createTable("T8");

        mockMvc.perform(delete("/tables/" + table.getId())
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tables")
                        .header("Authorization", "Bearer " + accessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + table.getId() + "')]").isEmpty());
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
}
