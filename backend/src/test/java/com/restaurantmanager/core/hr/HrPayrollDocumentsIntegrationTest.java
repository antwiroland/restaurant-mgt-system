package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payslip PDFs, payout details (bank or Mobile Money) and payroll payment files. */
class HrPayrollDocumentsIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BranchEntity accra;
    private UserEntity admin;
    private UserEntity clerk;
    private UserEntity viewer;
    private UserEntity chefUser;
    private UUID chef;
    private UUID cook;
    private UUID waiter;

    @BeforeEach
    void setUpData() throws Exception {
        accra = branch("ACC");
        admin = staff("Owner", Role.ADMIN, null);
        clerk = staff("Payroll Clerk", Role.CASHIER, accra);
        grant(clerk, HrPermission.PAYROLL_VIEW, HrPermission.PAYROLL_PROCESS, HrPermission.PAYROLL_APPROVE, HrPermission.EMPLOYEE_VIEW);
        viewer = staff("Payroll Viewer", Role.CASHIER, accra);
        grant(viewer, HrPermission.PAYROLL_VIEW);
        chefUser = staff("Kwesi Chef", Role.CASHIER, accra);
        chef = createEmployee(admin, "Kwesi", "Chef", accra);
        postJson("/hr/employees/" + chef + "/link-user", Map.of("userId", chefUser.getId()), admin).andExpect(status().isOk());
        cook = createEmployee(admin, "Ama", "Cook", accra);
        waiter = createEmployee(admin, "Yaw", "Waiter", accra);
        for (UUID e : new UUID[]{chef, cook, waiter}) {
            String id = json(postJson("/hr/employees/" + e + "/contracts", Map.of("contractType", "PERMANENT", "startDate", "2025-01-15",
                    "salaryType", "MONTHLY", "salaryAmount", "3000.00", "weeklyHours", 40), admin).andExpect(status().isCreated())).get("id").asText();
            postJson("/hr/contracts/" + id + "/activate", Map.of(), admin).andExpect(status().isOk());
        }
    }

    @Test
    void payslipPdf_forPayrollStaffAndTheEmployeeOnceReleased() throws Exception {
        String runId = finalizedRun();
        JsonNode run = json(getJson("/hr/payroll-runs/" + runId, clerk));
        String slip = payslipOf(run, chef);

        MockHttpServletResponse pdf = getJson("/hr/payslips/" + slip + "/pdf", clerk).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(pdf.getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getHeader("Content-Disposition")).contains("payslip-").endsWith(".pdf\"");
        String text = new String(pdf.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("%PDF-").contains("Payslip", "Kwesi Chef", "3000.00", "Net pay");

        getJson("/hr/payslips/" + slip + "/pdf", chefUser).andExpect(status().isOk()); // own, finalized
        getJson("/hr/payslips/" + payslipOf(run, cook) + "/pdf", chefUser).andExpect(status().isNotFound()); // someone else's
        assertThat(auditLogRepository.findByAction(AuditAction.HR_PAYSLIP_DOWNLOADED)).hasSize(2)
                .noneMatch(a -> a.getMetadata() != null && a.getMetadata().contains("3000"));
    }

    @Test
    void payoutDetails_validatedMaskedForViewers_andEditableByTheEmployee() throws Exception {
        putJson("/hr/employees/" + cook + "/payout", Map.of("method", "BANK", "bankName", "GCB"), clerk)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Bank payments need the bank, account name and account number"));
        putJson("/hr/employees/" + cook + "/payout", bank("Ama Cook", "1234567890123"), viewer).andExpect(status().isForbidden());
        putJson("/hr/employees/" + cook + "/payout", bank("Ama Cook", "1234567890123"), clerk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true));

        getJson("/hr/employees/" + cook + "/payout", clerk).andExpect(jsonPath("$.bankAccountNumber").value("1234567890123"));
        getJson("/hr/employees/" + cook + "/payout", viewer)
                .andExpect(jsonPath("$.masked").value(true)).andExpect(jsonPath("$.bankAccountNumber").value("********0123"));

        // the employee keeps their own details up to date
        putJson("/hr/me/payout", Map.of("method", "MOBILE_MONEY", "momoNetwork", "MTN", "momoNumber", "024 400 0001"), chefUser)
                .andExpect(status().isOk()).andExpect(jsonPath("$.momoNumber").value("0244000001"));
        getJson("/hr/me/payout", chefUser).andExpect(jsonPath("$.method").value("MOBILE_MONEY"));
        assertThat(auditLogRepository.findByAction(AuditAction.HR_PAYOUT_DETAILS_CHANGED)).hasSize(2)
                .noneMatch(a -> a.getMetadata() != null && (a.getMetadata().contains("1234567890123") || a.getMetadata().contains("0244000001")));
    }

    @Test
    void paymentFiles_perMethod_onlyWhenFinalizedAndComplete() throws Exception {
        grant(clerk, HrPermission.PAYROLL_PAY);
        putJson("/hr/employees/" + chef + "/payout", Map.of("method", "MOBILE_MONEY", "momoNetwork", "MTN", "momoNumber", "0244000001"), clerk).andExpect(status().isOk());
        putJson("/hr/employees/" + cook + "/payout", bank("Ama Cook", "1234567890123"), clerk).andExpect(status().isOk());
        // the waiter has no payout details yet

        String runId = finalizedRun();
        JsonNode summary = json(getJson("/hr/payroll-runs/" + runId + "/payment-files", clerk).andExpect(status().isOk()));
        assertThat(summary.get("ready").asBoolean()).isTrue();
        assertThat(summary.get("groups").findValuesAsText("method")).containsExactlyInAnyOrder("MOBILE_MONEY", "BANK");
        assertThat(summary.get("missing").findValuesAsText("employeeName")).containsExactly("Yaw Waiter");
        assertThat(summary.get("missing").get(0).get("problem").asText()).isEqualTo("No payout method set");

        MockHttpServletResponse bankFile = getJson("/hr/payroll-runs/" + runId + "/payment-files/BANK", clerk).andExpect(status().isOk()).andReturn().getResponse();
        String bankCsv = new String(bankFile.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(bankCsv).contains("Account number", "Ama Cook", "GCB", "1234567890123").doesNotContain("Kwesi");
        assertThat(bankFile.getHeader("Content-Disposition")).contains("-bank-payments.csv");
        String momoCsv = new String(getJson("/hr/payroll-runs/" + runId + "/payment-files/MOBILE_MONEY", clerk)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(momoCsv).contains("Mobile Money number", "Kwesi Chef", "MTN", "0244000001");
        assertThat(momoCsv.split("\r\n")).hasSize(2);

        getJson("/hr/payroll-runs/" + runId + "/payment-files/CASH", clerk).andExpect(status().isBadRequest());
        getJson("/hr/payroll-runs/" + runId + "/payment-files/BANK", viewer).andExpect(status().isForbidden());
        assertThat(auditLogRepository.findByAction(AuditAction.HR_PAYMENT_FILE_EXPORTED)).hasSize(2)
                .noneMatch(a -> a.getMetadata() != null && a.getMetadata().contains("1234567890123"));

        // incomplete details for someone due that way: refused rather than a short file
        putJson("/hr/employees/" + waiter + "/payout", bank("Yaw Waiter", "9999"), clerk).andExpect(status().isOk());
        jdbcTemplateUpdate("UPDATE hr_employees SET bank_account_number = NULL WHERE id = ?", waiter);
        getJson("/hr/payroll-runs/" + runId + "/payment-files/BANK", clerk)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Missing Bank details for: Yaw Waiter"));
    }

    @Test
    void paymentFile_refusedBeforeTheRunIsFinalized() throws Exception {
        grant(clerk, HrPermission.PAYROLL_PAY);
        putJson("/hr/employees/" + cook + "/payout", bank("Ama Cook", "1234567890123"), clerk).andExpect(status().isOk());
        String runId = createRun(clerk);
        postJson("/hr/payroll-runs/" + runId + "/calculate", Map.of(), clerk).andExpect(status().isOk());
        getJson("/hr/payroll-runs/" + runId + "/payment-files", clerk).andExpect(jsonPath("$.ready").value(false));
        getJson("/hr/payroll-runs/" + runId + "/payment-files/BANK", clerk)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Finalize the payroll run before exporting payment files"));
    }

    // ------------------------------------------------------------ helpers

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void jdbcTemplateUpdate(String sql, UUID id) {
        jdbcTemplate.update(sql, id);
    }

    private static Map<String, Object> bank(String accountName, String accountNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("method", "BANK");
        body.put("bankName", "GCB");
        body.put("bankBranch", "Osu");
        body.put("bankAccountName", accountName);
        body.put("bankAccountNumber", accountNumber);
        return body;
    }

    private String createRun(UserEntity actor) throws Exception {
        LocalDate start = LocalDate.now(ZONE).minusMonths(1).withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return json(postJson("/hr/payroll-runs", Map.of("branchId", accra.getId(), "periodStart", start.toString(), "periodEnd", end.toString(),
                "payDate", end.toString()), actor).andExpect(status().isCreated())).at("/summary/id").asText();
    }

    private String finalizedRun() throws Exception {
        String id = createRun(clerk);
        postJson("/hr/payroll-runs/" + id + "/calculate", Map.of(), clerk).andExpect(status().isOk());
        postJson("/hr/payroll-runs/" + id + "/approve", Map.of(), admin).andExpect(status().isOk());
        postJson("/hr/payroll-runs/" + id + "/finalize", Map.of(), admin).andExpect(status().isOk());
        return id;
    }

    private static String payslipOf(JsonNode run, UUID employee) {
        for (JsonNode p : run.get("payslips")) {
            if (p.get("employeeId").asText().equals(employee.toString())) return p.get("id").asText();
        }
        throw new AssertionError("No payslip for " + employee);
    }
}
