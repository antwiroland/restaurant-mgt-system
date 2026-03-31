package com.restaurantmanager.core.runtime;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.auth.dto.PinVerifyRequest;
import com.restaurantmanager.core.common.OverrideActionType;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.phase8.common.DiscountType;
import com.restaurantmanager.core.phase8.promo.PromoCodeEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhaseRuntimeIntegrationTest extends BaseIntegrationTest {

    @Test
    void givenCustomer_whenApplyPromoViaPhase8_thenDiscountReturned() throws Exception {
        UserEntity customer = createUser("Customer", "+233220000001", "customer1@x.com", "secret123", Role.CUSTOMER);

        String payload = """
                {
                  "code":"WELCOME10",
                  "discountType":"PERCENTAGE",
                  "discountValue":10,
                  "minOrderAmount":20,
                  "expiresAt":"%s",
                  "usageLimit":100,
                  "usedCount":0,
                  "active":true,
                  "subtotal":50
                }
                """.formatted(Instant.now().plusSeconds(600));

        mockMvc.perform(post("/phase8/promo/apply")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(5.0))
                .andExpect(jsonPath("$.newTotal").value(45.0));
    }

    @Test
    void givenCustomer_whenValidatePromoViaPhase8_thenPromoMetadataReturned() throws Exception {
        UserEntity customer = createUser("Customer", "+233220000011", "customer11@x.com", "secret123", Role.CUSTOMER);
        PromoCodeEntity promo = new PromoCodeEntity();
        promo.setCode("SAVE10");
        promo.setDescription("10 percent off");
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setDiscountValue(new BigDecimal("10.00"));
        promo.setMinOrderAmount(new BigDecimal("20.00"));
        promo.setUsageLimit(1000);
        promo.setUsageCount(0);
        promo.setActive(true);
        promo.setExpiryDate(Instant.now().plusSeconds(3600));
        promoCodeRepository.save(promo);

        mockMvc.perform(get("/phase8/promo-codes/validate/SAVE10")
                        .header("Authorization", "Bearer " + accessToken(customer))
                        .param("subtotal", "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SAVE10"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(10))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void givenManager_whenManagePromoCodes_thenCrudWorkflowSupported() throws Exception {
        UserEntity manager = createUser("Manager", "+233220000012", "manager12@x.com", "secret123", Role.MANAGER);

        String createPayload = """
                {
                  "code":"SPRING25",
                  "description":"25 percent off spring promotion",
                  "discountType":"PERCENTAGE",
                  "discountValue":25,
                  "minOrderAmount":40,
                  "maxDiscount":15,
                  "expiryDate":"%s",
                  "usageLimit":250,
                  "active":true
                }
                """.formatted(Instant.now().plusSeconds(7200));

        String responseBody = mockMvc.perform(post("/phase8/promo-codes")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SPRING25"))
                .andExpect(jsonPath("$.usageCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String promoId = objectMapper.readTree(responseBody).get("id").asText();

        String updatePayload = """
                {
                  "code":"SPRING25",
                  "description":"Updated spring promotion",
                  "discountType":"PERCENTAGE",
                  "discountValue":20,
                  "minOrderAmount":30,
                  "maxDiscount":12,
                  "expiryDate":"%s",
                  "usageLimit":300,
                  "active":true
                }
                """.formatted(Instant.now().plusSeconds(10800));

        mockMvc.perform(put("/phase8/promo-codes/" + promoId)
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated spring promotion"))
                .andExpect(jsonPath("$.discountValue").value(20))
                .andExpect(jsonPath("$.minOrderAmount").value(30))
                .andExpect(jsonPath("$.usageLimit").value(300));

        mockMvc.perform(patch("/phase8/promo-codes/" + promoId + "/status")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/phase8/promo-codes")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("SPRING25"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void givenCustomer_whenListPromoCodes_thenForbidden() throws Exception {
        UserEntity customer = createUser("Customer", "+233220000013", "customer13@x.com", "secret123", Role.CUSTOMER);

        mockMvc.perform(get("/phase8/promo-codes")
                        .header("Authorization", "Bearer " + accessToken(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenValidDiscountOverride_whenApplyPhase9Discount_thenDiscountApplied() throws Exception {
        UserEntity manager = createUser("Manager", "+233220000002", "manager1@x.com", "secret123", Role.MANAGER);
        mockMvc.perform(post("/users/" + manager.getId() + "/pin")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());

        String verifyResponse = mockMvc.perform(post("/auth/pin/verify")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinVerifyRequest("1234", OverrideActionType.DISCOUNT))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String overrideToken = objectMapper.readTree(verifyResponse).get("overrideToken").asText();

        String payload = """
                {
                  "total":100,
                  "mode":"PERCENTAGE",
                  "value":10,
                  "overrideToken":"%s"
                }
                """.formatted(overrideToken);

        mockMvc.perform(post("/phase9/discount/apply")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(10.0))
                .andExpect(jsonPath("$.newTotal").value(90.0));
    }

    @Test
    void givenCashier_whenQueryPhase9Audit_then403() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233220000003", "cashier1@x.com", "secret123", Role.CASHIER);

        mockMvc.perform(get("/phase9/audit")
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenAdmin_whenRequestPhase10AnalyticsSummary_thenAggregatesReturned() throws Exception {
        UserEntity admin = createUser("Admin", "+233220000004", "admin1@x.com", "secret123", Role.ADMIN);
        UUID customer = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        UUID menuItem = UUID.randomUUID();

        String payload = """
                {
                  "topItemLimit": 3,
                  "records": [
                    {
                      "orderId":"%s",
                      "customerId":"%s",
                      "menuItemId":"%s",
                      "itemName":"Jollof",
                      "quantity":2,
                      "lineRevenue":30,
                      "orderHour":12,
                      "createdAt":"%s",
                      "paymentSuccessful":true
                    },
                    {
                      "orderId":"%s",
                      "customerId":"%s",
                      "menuItemId":"%s",
                      "itemName":"Jollof",
                      "quantity":1,
                      "lineRevenue":15,
                      "orderHour":12,
                      "createdAt":"%s",
                      "paymentSuccessful":true
                    }
                  ]
                }
                """.formatted(order1, customer, menuItem, Instant.now(), order2, customer, menuItem, Instant.now());

        mockMvc.perform(post("/phase10/analytics/summary")
                        .header("Authorization", "Bearer " + accessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyRevenue").value(45.0))
                .andExpect(jsonPath("$.peakHour").value(12))
                .andExpect(jsonPath("$.repeatCustomers").value(1))
                .andExpect(jsonPath("$.topItems[0].name").value("Jollof"));
    }

    @Test
    void givenStaff_whenSanitizeSearchViaPhase10_thenSanitizedStringReturned() throws Exception {
        UserEntity manager = createUser("Manager", "+233220000005", "manager2@x.com", "secret123", Role.MANAGER);

        mockMvc.perform(post("/phase10/security/sanitize-search")
                        .header("Authorization", "Bearer " + accessToken(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"' OR 1=1; --\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(" OR 1=1 "));
    }
}
