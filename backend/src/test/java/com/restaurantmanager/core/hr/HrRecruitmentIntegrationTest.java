package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.common.AuditAction;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrRecruitmentIntegrationTest extends HrIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Africa/Accra");
    private static final byte[] PDF = "%PDF-1.4\n%cv\n".getBytes(StandardCharsets.US_ASCII);

    private BranchEntity accra;
    private BranchEntity kumasi;
    private UserEntity admin;
    private UserEntity manager;
    private UserEntity kumasiManager;
    private UserEntity chef;

    @BeforeEach
    void setUpData() {
        accra = branch("ACC");
        kumasi = branch("KSI");
        admin = staff("Owner", Role.ADMIN, null);
        manager = staff("Accra Manager", Role.MANAGER, accra);
        kumasiManager = staff("Kumasi Manager", Role.MANAGER, kumasi);
        chef = staff("Head Chef", Role.CASHIER, accra);
    }

    private Map<String, Object> vacancy(int openings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("branchId", accra.getId());
        body.put("title", "Line cook");
        body.put("employmentType", "FULL_TIME");
        body.put("openings", openings);
        body.put("requirements", "Food hygiene certificate");
        return body;
    }

    private String openVacancy(UserEntity actor, int openings) throws Exception {
        String id = json(postJson("/hr/vacancies", vacancy(openings), actor).andExpect(status().isCreated())).at("/summary/id").asText();
        postJson("/hr/vacancies/" + id + "/status", Map.of("status", "OPEN"), actor).andExpect(status().isOk());
        return id;
    }

    private Map<String, Object> candidate(String first, String phone) {
        Map<String, Object> c = new HashMap<>();
        c.put("firstName", first);
        c.put("lastName", "Mensah");
        c.put("phone", phone);
        c.put("source", "WALK_IN");
        return c;
    }

    private JsonNode applyNew(String vacancy, String first, String phone, UserEntity actor) throws Exception {
        return json(postJson("/hr/vacancies/" + vacancy + "/applications", Map.of("candidate", candidate(first, phone)), actor)
                .andExpect(status().isCreated()));
    }

    private ResultActions move(String application, String stage, String note, UserEntity actor) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("stage", stage);
        body.put("note", note);
        return postJson("/hr/applications/" + application + "/stage", body, actor);
    }

    private void toAcceptedOffer(String application, UserEntity actor, Map<String, Object> offer) throws Exception {
        move(application, "OFFER", null, actor).andExpect(status().isOk());
        putJson("/hr/applications/" + application + "/offer", offer, actor).andExpect(status().isOk()).andExpect(jsonPath("$.offer.status").value("SENT"));
        postJson("/hr/applications/" + application + "/offer-response", Map.of("accepted", true), actor)
                .andExpect(status().isOk()).andExpect(jsonPath("$.offer.status").value("ACCEPTED"));
    }

    @Test
    void managerRunsThePipeline_interviewerRecordsFeedback_hireCreatesEmployeeAndCopiesCv() throws Exception {
        Map<String, Object> withPay = vacancy(1);
        withPay.put("salaryMin", 2000);
        postJson("/hr/vacancies", withPay, manager).andExpect(status().isForbidden()); // no pay permission
        String vacancy = openVacancy(manager, 1);

        JsonNode app = applyNew(vacancy, "Akosua", "+233240000111", manager);
        String appId = app.get("id").asText();
        String candidateId = app.at("/candidate/id").asText();
        assertThat(app.get("stage").asText()).isEqualTo("APPLIED");
        postJson("/hr/candidates", candidate("Akosua", "+233240000111"), manager).andExpect(status().isConflict()); // same phone
        mockMvc.perform(multipart("/hr/candidates/" + candidateId + "/documents").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF))
                .param("title", "CV").header("Authorization", bearer(manager))).andExpect(status().isOk());

        String second = applyNew(vacancy, "Kojo", "+233240000222", manager).get("id").asText();

        move(appId, "SCREENING", null, manager).andExpect(status().isOk());
        Instant when = Instant.now().plus(1, ChronoUnit.DAYS);
        postJson("/hr/applications/" + appId + "/interviews", Map.of("kind", "IN_PERSON", "scheduledAt", when.toString(), "interviewerId", chef.getId()),
                manager).andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("INTERVIEW"));

        // the interviewer has no recruitment permission but sees and records their own interview
        JsonNode mine = json(getJson("/hr/me/interviews", chef).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("candidateName").asText()).isEqualTo("Akosua Mensah");
        String interviewId = mine.get(0).get("id").asText();
        getJson("/hr/vacancies", chef).andExpect(status().isForbidden());
        putJson("/hr/interviews/" + interviewId, Map.of("outcome", "PASSED"), chef).andExpect(status().isBadRequest()); // score needed
        putJson("/hr/interviews/" + interviewId, Map.of("outcome", "PASSED", "score", 4, "feedback", "Calm under pressure"), chef)
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(4));
        putJson("/hr/interviews/" + interviewId, Map.of("outcome", "FAILED", "score", 1), staff("Other", Role.CASHIER, accra))
                .andExpect(status().isNotFound());

        Map<String, Object> payOffer = Map.of("contractType", "PERMANENT", "salaryType", "MONTHLY", "salaryAmount", 2500,
                "startDate", LocalDate.now(ZONE).plusDays(7).toString());
        move(appId, "OFFER", null, manager).andExpect(status().isOk());
        putJson("/hr/applications/" + appId + "/offer", payOffer, manager).andExpect(status().isForbidden());
        putJson("/hr/applications/" + appId + "/offer", Map.of("contractType", "PERMANENT", "startDate", LocalDate.now(ZONE).plusDays(7).toString()), manager)
                .andExpect(status().isOk());
        postJson("/hr/applications/" + appId + "/hire", Map.of(), manager).andExpect(status().isConflict()); // not accepted yet
        postJson("/hr/applications/" + appId + "/offer-response", Map.of("accepted", true), manager).andExpect(status().isOk());

        JsonNode hired = json(postJson("/hr/applications/" + appId + "/hire", Map.of(), manager).andExpect(status().isOk()));
        assertThat(hired.get("contractCreated").asBoolean()).isFalse();
        assertThat(hired.get("contractNote").asText()).isEqualTo("Someone who manages contracts must create the contract");
        assertThat(hired.get("documentsCopied").asInt()).isEqualTo(1);
        String employeeId = hired.get("employeeId").asText();
        JsonNode employee = json(getJson("/hr/employees/" + employeeId, admin).andExpect(status().isOk()));
        assertThat(employee.at("/summary/fullName").asText()).isEqualTo("Akosua Mensah");
        assertThat(employee.at("/summary/primaryBranchId").asText()).isEqualTo(accra.getId().toString());
        assertThat(employee.at("/summary/hireDate").asText()).isEqualTo(LocalDate.now(ZONE).plusDays(7).toString());
        JsonNode docs = json(getJson("/hr/employees/" + employeeId + "/documents", admin));
        assertThat(docs.get(0).get("documentType").asText()).isEqualTo("CV");

        // the only opening is filled: the vacancy closes and the other applicant is told
        JsonNode v = json(getJson("/hr/vacancies/" + vacancy, manager));
        assertThat(v.at("/summary/status").asText()).isEqualTo("FILLED");
        assertThat(v.at("/summary/hiredCount").asInt()).isEqualTo(1);
        assertThat(json(getJson("/hr/applications/" + second, manager)).get("rejectReason").asText()).isEqualTo("Position filled");
        assertThat(auditLogRepository.findByAction(AuditAction.HR_RECRUITMENT_CHANGED)).hasSizeGreaterThan(5);

        // the hired candidate's own file can now be removed without touching the employee's copy
        JsonNode anonymised = json(postJson("/hr/candidates/" + candidateId + "/anonymise", Map.of(), manager).andExpect(status().isOk()));
        assertThat(anonymised.at("/summary/fullName").asText()).isEqualTo("Removed");
        assertThat(anonymised.get("documents")).isEmpty();
        getJson("/hr/documents/" + docs.get(0).get("id").asText() + "/download", admin).andExpect(status().isOk());
    }

    @Test
    void hrHireWithPay_createsDraftContractWithOfferedTerms() throws Exception {
        String vacancy = openVacancy(admin, 2);
        String appId = applyNew(vacancy, "Yaa", "+233240000333", admin).get("id").asText();
        toAcceptedOffer(appId, admin, Map.of("contractType", "PERMANENT", "salaryType", "MONTHLY", "salaryAmount", 2500, "weeklyHours", 45,
                "startDate", LocalDate.now(ZONE).toString()));
        JsonNode detail = json(getJson("/hr/applications/" + appId, manager));
        assertThat(detail.at("/offer/salaryAmount").isNull()).isTrue(); // hidden from the manager
        assertThat(detail.at("/offer/payHidden").asBoolean()).isTrue();

        JsonNode hired = json(postJson("/hr/applications/" + appId + "/hire", Map.of(), admin).andExpect(status().isOk()));
        assertThat(hired.get("contractCreated").asBoolean()).isTrue();
        JsonNode contracts = json(getJson("/hr/employees/" + hired.get("employeeId").asText() + "/contracts", admin));
        assertThat(contracts.get(0).get("status").asText()).isEqualTo("DRAFT");
        assertThat(contracts.get(0).get("salaryAmount").decimalValue()).isEqualByComparingTo("2500");
        assertThat(json(getJson("/hr/vacancies/" + vacancy, admin)).at("/summary/status").asText()).isEqualTo("OPEN"); // one opening left
    }

    @Test
    void stageRules_closingAVacancy_andBranchIsolation() throws Exception {
        String vacancy = openVacancy(manager, 1);
        String a = applyNew(vacancy, "Efua", "+233240000444", manager).get("id").asText();
        move(a, "INTERVIEW", null, manager).andExpect(status().isOk());                         // stages may be skipped
        move(a, "SCREENING", null, manager).andExpect(status().isConflict());                   // never backwards
        move(a, "HIRED", null, manager).andExpect(status().isConflict());                       // only through hire
        move(a, "REJECTED", null, manager).andExpect(status().isBadRequest());                  // reason required
        move(a, "REJECTED", "Not available evenings", manager).andExpect(status().isOk());
        move(a, "OFFER", null, manager).andExpect(status().isConflict());                       // reconsider first
        move(a, "SCREENING", "Availability changed", manager).andExpect(status().isOk()).andExpect(jsonPath("$.rejectReason").isEmpty());

        getJson("/hr/vacancies/" + vacancy, kumasiManager).andExpect(status().isNotFound());
        getJson("/hr/applications/" + a, kumasiManager).andExpect(status().isNotFound());
        assertThat(json(getJson("/hr/vacancies", kumasiManager))).isEmpty();

        String b = applyNew(vacancy, "Kwame", "+233240000555", manager).get("id").asText();
        toAcceptedOffer(b, manager, Map.of("contractType", "PERMANENT", "startDate", LocalDate.now(ZONE).toString()));
        postJson("/hr/vacancies/" + vacancy + "/status", Map.of("status", "CLOSED", "reason", "Budget"), manager).andExpect(status().isConflict());
        move(b, "WITHDRAWN", "Took another job", manager).andExpect(status().isOk());
        postJson("/hr/vacancies/" + vacancy + "/status", Map.of("status", "CLOSED"), manager).andExpect(status().isBadRequest());
        postJson("/hr/vacancies/" + vacancy + "/status", Map.of("status", "CLOSED", "reason", "Budget"), manager).andExpect(status().isOk());
        assertThat(json(getJson("/hr/applications/" + a, manager)).get("stage").asText()).isEqualTo("REJECTED");
        postJson("/hr/vacancies/" + vacancy + "/applications", Map.of("candidate", candidate("Late", "+233240000666")), manager)
                .andExpect(status().isConflict()); // closed
        postJson("/hr/vacancies/" + vacancy + "/status", Map.of("status", "OPEN"), manager).andExpect(status().isOk()); // reopen
    }

    @Test
    void publicCareers_listOnlyPublishedOpenVacancies_withoutSignInOrPay() throws Exception {
        Map<String, Object> listed = vacancy(2);
        listed.put("title", "Waiter (public)");
        listed.put("description", "Lunch and evening service on the terrace");
        listed.put("salaryMin", 2000);
        listed.put("salaryMax", 2600);
        listed.put("closingDate", LocalDate.now(ZONE).toString()); // still open on its closing day
        String open = json(postJson("/hr/vacancies", listed, admin).andExpect(status().isCreated())).at("/summary/id").asText();
        postJson("/hr/vacancies/" + open + "/status", Map.of("status", "OPEN"), admin).andExpect(status().isOk());
        JsonNode published = json(postJson("/hr/vacancies/" + open + "/publish", Map.of("published", true), admin).andExpect(status().isOk()));
        assertThat(published.at("/summary/published").asBoolean()).isTrue();
        String publishedAt = published.at("/summary/publishedAt").asText();
        getJson("/hr/vacancies", chef).andExpect(status().isForbidden());
        postJson("/hr/vacancies/" + open + "/publish", Map.of("published", false), chef).andExpect(status().isForbidden());

        String notPublished = openVacancy(admin, 1); // open, but HR has not advertised it

        Map<String, Object> draft = vacancy(1);
        draft.put("title", "Draft (public)");
        String draftId = json(postJson("/hr/vacancies", draft, admin).andExpect(status().isCreated())).at("/summary/id").asText();
        postJson("/hr/vacancies/" + draftId + "/publish", Map.of("published", true), admin).andExpect(status().isConflict()); // open it first

        Map<String, Object> expired = vacancy(1);
        expired.put("title", "Expired (public)");
        expired.put("closingDate", LocalDate.now(ZONE).minusDays(1).toString());
        String expiredId = json(postJson("/hr/vacancies", expired, admin).andExpect(status().isCreated())).at("/summary/id").asText();
        postJson("/hr/vacancies/" + expiredId + "/status", Map.of("status", "OPEN"), admin).andExpect(status().isOk());
        postJson("/hr/vacancies/" + expiredId + "/publish", Map.of("published", true), admin).andExpect(status().isConflict()); // closing date passed

        String paused = openVacancy(admin, 1);
        postJson("/hr/vacancies/" + paused + "/publish", Map.of("published", true), admin).andExpect(status().isOk());
        JsonNode held = json(postJson("/hr/vacancies/" + paused + "/status", Map.of("status", "ON_HOLD"), admin).andExpect(status().isOk()));
        assertThat(held.at("/summary/published").asBoolean()).isFalse(); // leaving OPEN takes it off the careers page

        JsonNode jobs = json(mockMvc.perform(get("/careers")).andExpect(status().isOk()));
        assertThat(jobs.findValuesAsText("id")).contains(open).doesNotContain(notPublished, draftId, expiredId, paused);
        JsonNode job = json(mockMvc.perform(get("/careers/" + open)).andExpect(status().isOk()));
        assertThat(job.get("title").asText()).isEqualTo("Waiter (public)");
        assertThat(job.get("description").asText()).isEqualTo("Lunch and evening service on the terrace");
        assertThat(job.get("requirements").asText()).isEqualTo("Food hygiene certificate");
        assertThat(job.get("branchName").asText()).isEqualTo(accra.getName());
        assertThat(job.get("openings").asInt()).isEqualTo(2);
        assertThat(Instant.parse(job.get("postedAt").asText())).isEqualTo(Instant.parse(publishedAt));
        assertThat(job.has("salaryMin")).isFalse();
        assertThat(job.has("salaryMax")).isFalse();
        assertThat(job.has("hiringManagerId")).isFalse();

        mockMvc.perform(get("/careers/" + draftId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/careers/" + notPublished)).andExpect(status().isNotFound());
        mockMvc.perform(get("/careers/" + expiredId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/careers")).andExpect(status().isUnauthorized()); // read-only

        // unpublishing removes it straight away; publishing again brings it back; closing removes it for good
        postJson("/hr/vacancies/" + open + "/publish", Map.of("published", false), admin).andExpect(status().isOk());
        mockMvc.perform(get("/careers/" + open)).andExpect(status().isNotFound());
        assertThat(json(mockMvc.perform(get("/careers"))).findValuesAsText("id")).doesNotContain(open);
        postJson("/hr/vacancies/" + open + "/publish", Map.of("published", true), admin).andExpect(status().isOk());
        mockMvc.perform(get("/careers/" + open)).andExpect(status().isOk());
        postJson("/hr/vacancies/" + open + "/status", Map.of("status", "CLOSED", "reason", "Filled internally"), admin).andExpect(status().isOk());
        mockMvc.perform(get("/careers/" + open)).andExpect(status().isNotFound());
        assertThat(json(getJson("/hr/vacancies/" + open, admin)).at("/summary/published").asBoolean()).isFalse();
        postJson("/hr/vacancies/" + open + "/status", Map.of("status", "OPEN"), admin).andExpect(status().isOk());
        mockMvc.perform(get("/careers/" + open)).andExpect(status().isNotFound()); // reopening needs a deliberate re-publish
    }

    @Test
    void anonymise_needsEveryApplicationClosed_andRemovesPersonalData() throws Exception {
        String vacancy = openVacancy(manager, 1);
        JsonNode app = applyNew(vacancy, "Abena", "+233240000777", manager);
        String candidateId = app.at("/candidate/id").asText();
        postJson("/hr/candidates/" + candidateId + "/anonymise", Map.of(), manager).andExpect(status().isConflict());
        move(app.get("id").asText(), "WITHDRAWN", null, manager).andExpect(status().isOk());
        JsonNode done = json(postJson("/hr/candidates/" + candidateId + "/anonymise", Map.of(), manager).andExpect(status().isOk()));
        assertThat(done.at("/summary/phone").isNull()).isTrue();
        assertThat(done.at("/summary/anonymised").asBoolean()).isTrue();
        // the same person can apply again as a new candidate
        applyNew(vacancy, "Abena", "+233240000777", manager);
        assertThat(json(getJson("/hr/candidates?q=abena", manager))).hasSize(1);
    }
}
