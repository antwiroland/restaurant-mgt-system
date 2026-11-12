package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderStatus;
import com.restaurantmanager.core.order.OrderType;
import com.restaurantmanager.core.payment.PaymentEntity;
import com.restaurantmanager.core.payment.PaymentMethod;
import com.restaurantmanager.core.payment.PaymentStatus;
import com.restaurantmanager.core.user.UserEntity;
import com.restaurantmanager.core.workforce.TimeEntryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrTipsIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity clerk;
    private UserEntity waiterUser;
    private UUID waiter;
    private UUID cook;
    private UUID visitor;
    private LocalDate monthStart;
    private LocalDate monthEnd;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        clerk = staff("Tip Clerk", Role.CASHIER, accra);
        grant(clerk, HrPermission.TIPS_MANAGE, HrPermission.SERVICE_CHARGE_MANAGE, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_VIEW,
                HrPermission.EMPLOYEE_VIEW);
        waiterUser = staff("Ama Waiter", Role.CASHIER, accra);
        waiter = createEmployee(admin, "Ama", "Waiter", accra);
        postJson("/hr/employees/" + waiter + "/link-user", Map.of("userId", waiterUser.getId()), admin).andExpect(status().isOk());
        cook = createEmployee(admin, "Kofi", "Cook", accra);
        // works at Kumasi but covered shifts at Accra: shares Accra's tips, paid by Kumasi payroll
        visitor = createEmployee(admin, "Yaw", "Visitor", kumasi);
        postJson("/hr/employees/" + visitor + "/assignments", Map.of("branchId", accra.getId(), "assignmentType", "SECONDARY",
                "startDate", "2025-02-01", "reason", "Cover"), admin).andExpect(status().isCreated());
        monthStart = LocalDate.now(ZONE).minusMonths(1).withDayOfMonth(1);
        monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private void worked(UUID employee, BranchEntity branch, LocalDate day, int from, int to) {
        TimeEntryEntity e = new TimeEntryEntity();
        e.setEmployeeId(employee);
        e.setBranchId(branch.getId());
        e.setClockInAt(at(day, from));
        e.setClockOutAt(at(day, to));
        e.setBreakMinutes(0);
        e.setSource("POS");
        timeEntryRepository.save(e);
    }

    private void paidOrder(BranchEntity branch, String tip, String serviceCharge, Instant paidAt, boolean fullyRefunded) {
        OrderEntity order = new OrderEntity();
        order.setCustomerUserId(admin.getId());
        order.setCreatedByUserId(admin.getId());
        order.setType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.COMPLETED);
        order.setBranch(branch);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTipAmount(new BigDecimal(tip));
        order.setServiceCharge(new BigDecimal(serviceCharge));
        order.setTotal(new BigDecimal("100.00").add(new BigDecimal(tip)).add(new BigDecimal(serviceCharge)));
        order = orderRepository.save(order);
        PaymentEntity payment = new PaymentEntity();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setCurrency("GHS");
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.SUCCESS);
        if (fullyRefunded) payment.setRefundedAmount(order.getTotal());
        payment.setPaystackReference("TIP-" + UUID.randomUUID());
        payment.setIdempotencyKey("TIP-" + UUID.randomUUID());
        payment.setPaidAt(paidAt);
        paymentRepository.save(payment);
    }

    private String pool(String source) throws Exception {
        return json(postJson("/hr/tip-pools", Map.of("branchId", accra.getId(), "source", source, "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), clerk).andExpect(status().isCreated())).at("/summary/id").asText();
    }

    private JsonNode allocation(JsonNode pool, UUID employee) {
        for (JsonNode a : pool.get("allocations")) {
            if (a.get("employeeId").asText().equals(employee.toString())) return a;
        }
        throw new AssertionError("No allocation for " + employee);
    }

    @Test
    void tipsPool_collectsPaidOrders_sharesByHours_andIsPaidThroughHomeBranchPayroll() throws Exception {
        LocalDate day = monthStart.plusDays(3);
        paidOrder(accra, "60.00", "0", at(day, 13), false);
        paidOrder(accra, "40.00", "0", at(day, 20), false);
        paidOrder(accra, "999.00", "0", at(day, 21), true);                 // fully refunded: excluded
        paidOrder(kumasi, "500.00", "0", at(day, 12), false);               // other branch: excluded
        paidOrder(accra, "777.00", "0", at(monthEnd.plusDays(1), 12), false); // after the period: excluded
        worked(waiter, accra, day, 8, 16);   // 480 min
        worked(cook, accra, day, 8, 12);     // 240 min
        worked(visitor, accra, day, 12, 16); // 240 min
        worked(cook, kumasi, day.plusDays(1), 8, 16); // other branch hours do not count
        postJson("/hr/tip-rules", Map.of("source", "TIPS", "method", "HOURS", "housePercent", 10, "taxable", true, "effectiveFrom", "2020-01-01"), admin)
                .andExpect(status().isCreated());

        String id = pool("TIPS");
        JsonNode calc = json(postJson("/hr/tip-pools/" + id + "/calculate", Map.of(), clerk).andExpect(status().isOk()));
        assertThat(calc.at("/summary/orderCount").asInt()).isEqualTo(2);
        assertThat(calc.at("/summary/collectedAmount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(calc.at("/summary/houseAmount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(calc.at("/summary/distributableAmount").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(allocation(calc, waiter).get("amount").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(allocation(calc, cook).get("amount").decimalValue()).isEqualByComparingTo("22.50");
        assertThat(allocation(calc, visitor).get("amount").decimalValue()).isEqualByComparingTo("22.50");
        assertThat(calc.at("/rule/scope").asText()).isEqualTo("ORGANISATION");
        assertThat(calc.at("/summary/poolNumber").asText()).matches("TIP-[0-9]{6}");

        // four eyes; staff see only approved shares
        assertThat(json(getJson("/hr/me/tips", waiterUser))).isEmpty();
        postJson("/hr/tip-pools/" + id + "/approve", Map.of(), clerk).andExpect(status().isForbidden());
        postJson("/hr/tip-pools/" + id + "/approve", Map.of(), admin).andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("APPROVED"));
        JsonNode mine = json(getJson("/hr/me/tips", waiterUser).andExpect(status().isOk()));
        assertThat(mine.get(0).get("amount").decimalValue()).isEqualByComparingTo("45.00");

        // payroll: Accra pays its own staff their shares; the visitor's share waits for Kumasi's payroll
        for (UUID e : List.of(waiter, cook, visitor)) {
            String c = json(postJson("/hr/employees/" + e + "/contracts", Map.of("contractType", "PERMANENT", "startDate", "2025-01-15",
                    "salaryType", "MONTHLY", "salaryAmount", "2000.00", "weeklyHours", 40), admin).andExpect(status().isCreated())).get("id").asText();
            postJson("/hr/contracts/" + c + "/activate", Map.of(), admin).andExpect(status().isOk());
        }
        String run = json(postJson("/hr/payroll-runs", Map.of("branchId", accra.getId(), "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), clerk).andExpect(status().isCreated())).at("/summary/id").asText();
        JsonNode payroll = json(postJson("/hr/payroll-runs/" + run + "/calculate", Map.of(), clerk).andExpect(status().isOk()));
        assertThat(payroll.at("/summary/grossTotal").decimalValue()).isEqualByComparingTo("4067.50"); // 2 × 2000 + 45 + 22.50

        // a pool used by a calculated payroll run cannot change
        postJson("/hr/tip-pools/" + id + "/reopen", Map.of("reason", "Recount"), admin).andExpect(status().isConflict());

        postJson("/hr/payroll-runs/" + run + "/approve", Map.of(), admin).andExpect(status().isOk());
        postJson("/hr/payroll-runs/" + run + "/finalize", Map.of(), admin).andExpect(status().isOk());
        assertThat(json(getJson("/hr/tip-pools/" + id, clerk)).at("/summary/status").asText()).isEqualTo("APPROVED"); // visitor not paid yet

        String kumasiRun = json(postJson("/hr/payroll-runs", Map.of("branchId", kumasi.getId(), "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), admin).andExpect(status().isCreated())).at("/summary/id").asText();
        JsonNode kumasiPayroll = json(postJson("/hr/payroll-runs/" + kumasiRun + "/calculate", Map.of(), admin));
        assertThat(kumasiPayroll.at("/summary/grossTotal").decimalValue()).isEqualByComparingTo("2022.50");
        UserEntity secondApprover = staff("Second", Role.ADMIN, null);
        postJson("/hr/payroll-runs/" + kumasiRun + "/approve", Map.of(), secondApprover).andExpect(status().isOk());
        postJson("/hr/payroll-runs/" + kumasiRun + "/finalize", Map.of(), admin).andExpect(status().isOk());
        assertThat(json(getJson("/hr/tip-pools/" + id, clerk)).at("/summary/status").asText()).isEqualTo("PAID");
        assertThat(auditLogRepository.findByAction(AuditAction.HR_TIPS_CHANGED)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void serviceChargePool_usesBranchRuleWithPositionPoints_andOnePoolPerPeriod() throws Exception {
        String waiterPos = json(postJson("/hr/positions", Map.of("code", "WAITER", "title", "Waiter", "category", "FRONT_OF_HOUSE"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        String cookPos = json(postJson("/hr/positions", Map.of("code", "COOK", "title", "Cook", "category", "KITCHEN"), admin)
                .andExpect(status().isCreated())).get("id").asText();
        for (Object[] e : new Object[][]{{waiter, "Ama", "Waiter", waiterPos}, {cook, "Kofi", "Cook", cookPos}}) {
            Map<String, Object> body = new HashMap<>(employeeBody((String) e[1], (String) e[2], accra));
            body.put("positionId", e[3]);
            putJson("/hr/employees/" + e[0], body, admin).andExpect(status().isOk());
        }
        LocalDate day = monthStart.plusDays(5);
        paidOrder(accra, "0", "150.00", at(day, 13), false);
        worked(waiter, accra, day, 8, 16);
        worked(cook, accra, day, 8, 16);
        worked(visitor, accra, day, 8, 16); // no position: not listed, so no points
        // organisation rule (hours) is overridden by the branch rule (points, waiters 2, cooks 1, a 60-minute minimum)
        postJson("/hr/tip-rules", Map.of("source", "SERVICE_CHARGE", "method", "HOURS", "effectiveFrom", "2020-01-01", "taxable", true), admin)
                .andExpect(status().isCreated());
        postJson("/hr/tip-rules", Map.of("branchId", accra.getId(), "source", "SERVICE_CHARGE", "method", "POINTS", "housePercent", 0, "taxable", false,
                "minMinutes", 60, "positionPoints", Map.of(waiterPos, 2, cookPos, 1), "effectiveFrom", "2020-01-01"), clerk).andExpect(status().isCreated());

        String id = pool("SERVICE_CHARGE");
        JsonNode calc = json(postJson("/hr/tip-pools/" + id + "/calculate", Map.of(), clerk));
        assertThat(calc.at("/rule/scope").asText()).isEqualTo("BRANCH");
        assertThat(calc.at("/rule/taxable").asBoolean()).isFalse();
        assertThat(allocation(calc, waiter).get("amount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(allocation(calc, cook).get("amount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(allocation(calc, visitor).get("amount").decimalValue()).isZero();

        postJson("/hr/tip-pools", Map.of("branchId", accra.getId(), "source", "SERVICE_CHARGE", "periodStart", monthEnd.toString(),
                "periodEnd", monthEnd.toString()), clerk).andExpect(status().isConflict());
        // a tips pool for the same period is a different pool
        pool("TIPS");
        postJson("/hr/tip-pools", Map.of("branchId", accra.getId(), "source", "TIPS", "periodStart", LocalDate.now(ZONE).toString(),
                "periodEnd", LocalDate.now(ZONE).toString()), clerk).andExpect(status().isBadRequest()); // day not over yet

        postJson("/hr/tip-pools/" + id + "/cancel", Map.of("reason", "Wrong rule"), clerk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("CANCELLED"));
        pool("SERVICE_CHARGE"); // the period is free again
    }

    @Test
    void permissions_areSeparatePerSource_andBranchScoped() throws Exception {
        UserEntity tipsOnly = staff("Tips Only", Role.CASHIER, accra);
        grant(tipsOnly, HrPermission.TIPS_MANAGE);
        UserEntity kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        grant(kumasiManager, HrPermission.TIPS_MANAGE);
        postJson("/hr/tip-pools", Map.of("branchId", accra.getId(), "source", "SERVICE_CHARGE", "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), tipsOnly).andExpect(status().isForbidden());
        String id = json(postJson("/hr/tip-pools", Map.of("branchId", accra.getId(), "source", "TIPS", "periodStart", monthStart.toString(),
                "periodEnd", monthEnd.toString()), tipsOnly).andExpect(status().isCreated())).at("/summary/id").asText();
        getJson("/hr/tip-pools/" + id, kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/tip-pools", staff("Plain", Role.MANAGER, accra)).andExpect(status().isForbidden()); // not a manager default
        postJson("/hr/tip-rules", Map.of("source", "TIPS", "method", "EQUAL", "effectiveFrom", "2020-01-01"), tipsOnly)
                .andExpect(status().isForbidden()); // organisation-wide rules are central
    }
}
