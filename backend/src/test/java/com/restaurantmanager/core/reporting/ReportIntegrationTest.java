package com.restaurantmanager.core.reporting;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIntegrationTest extends BaseIntegrationTest {
    private UserEntity manager;
    private UserEntity cashier;
    private String today;

    @BeforeEach
    void setUpData() throws Exception {
        manager = createUser("Manager", "+233350000001", "rep-manager@x.com", "secret123", Role.MANAGER);
        cashier = createUser("Cashier", "+233350000002", "rep-cashier@x.com", "secret123", Role.CASHIER);
        today = LocalDate.now(ZoneId.of("Africa/Accra")).toString();
        MenuItemEntity item = createMenuItem();
        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + accessToken(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"items\":[{\"menuItemId\":\"" + item.getId() + "\",\"quantity\":2}]}"))
                .andReturn().getResponse().getContentAsString();
        OrderEntity order = orderRepository.findById(UUID.fromString(objectMapper.readTree(body).get("id").asText())).orElseThrow();
        PaymentEntity payment = new PaymentEntity();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setCurrency("GHS");
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaystackReference("REP-" + UUID.randomUUID());
        payment.setIdempotencyKey("REP-" + UUID.randomUUID());
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);
    }

    @Test
    void givenPaidOrder_whenSalesCsvExported_thenRowWithTotalsAndAttachmentHeader() throws Exception {
        byte[] csv = mockMvc.perform(get("/reports/sales").param("from", today).param("to", today).param("format", "CSV")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sales_" + today)))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        String[] lines = text.trim().split("\r\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("Subtotal"));
        assertTrue(lines[1].contains("PICKUP"));
        assertTrue(lines[1].contains("CASH"));
        assertTrue(lines[1].contains("50.00"));
    }

    @Test
    void givenPaidOrder_whenSalesPdfExported_thenValidPdf() throws Exception {
        byte[] pdf = mockMvc.perform(get("/reports/sales").param("from", today).param("to", today).param("format", "PDF")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.contains("(Sales report) Tj"));
        assertTrue(text.trim().endsWith("%%EOF"));
    }

    @Test
    void givenReconciliationAndAuditAndTimesheet_whenExported_thenOk() throws Exception {
        for (String format : List.of("CSV", "PDF")) {
            mockMvc.perform(get("/reports/reconciliation").param("date", today).param("format", format)
                            .header("Authorization", "Bearer " + accessToken(manager)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/reports/audit").param("from", today).param("to", today).param("format", format)
                            .header("Authorization", "Bearer " + accessToken(manager)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/reports/timesheet").param("from", today).param("to", today).param("format", format)
                            .header("Authorization", "Bearer " + accessToken(manager)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void givenCashier_whenExporting_then403() throws Exception {
        mockMvc.perform(get("/reports/sales").param("from", today).param("to", today)
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void csv_escapesQuotesCommasAndFormulaInjection() {
        assertEquals("\"'=SUM(A1,A2)\"", CsvWriter.escape("=SUM(A1,A2)"));
        assertEquals("'+233200000000", CsvWriter.escape("+233200000000"));
        assertEquals("\"He said \"\"hi\"\"\"", CsvWriter.escape("He said \"hi\""));
        assertEquals("-5", CsvWriter.escape(-5));
    }

    @Test
    void pdf_paginatesLongTables() {
        PdfTableDocument doc = new PdfTableDocument("Long", false).columns(new String[]{"A", "B"}, new double[]{1, 1});
        for (int i = 0; i < 200; i++) {
            doc.row(List.of("row " + i, i));
        }
        String text = new String(doc.toBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("/Count 4"), "200 rows should span several pages");
        assertTrue(text.contains("(Page 4 of 4) Tj"));
    }

    private MenuItemEntity createMenuItem() {
        CategoryEntity category = new CategoryEntity();
        category.setName("Rep-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        category = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(category);
        item.setName("Omo tuo");
        item.setPrice(new BigDecimal("25.00"));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }
}
