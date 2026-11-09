package com.restaurantmanager.core.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.hr.access.HrPermission;
import com.restaurantmanager.core.hr.access.HrUserPermissionEntity;
import com.restaurantmanager.core.hr.access.HrUserPermissionRepository;
import com.restaurantmanager.core.user.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Shared helpers for HR integration tests: branches, staff in branches, grants and JSON calls. */
public abstract class HrIntegrationTestSupport extends BaseIntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired
    protected BranchRepository branchRepository;
    @Autowired
    protected HrUserPermissionRepository grantRepository;

    protected BranchEntity branch(String code) {
        return branchRepository.findAll().stream().filter(b -> b.getCode().equals(code)).findFirst().orElseGet(() -> {
            BranchEntity branch = new BranchEntity();
            branch.setCode(code);
            branch.setName("Branch " + code);
            branch.setActive(true);
            return branchRepository.save(branch);
        });
    }

    protected UserEntity staff(String name, Role role, BranchEntity branch) {
        int n = SEQ.incrementAndGet();
        UserEntity user = createUser(name, "+23355" + String.format("%07d", n), "hr" + n + "@x.com", "secret123", role);
        if (branch != null) {
            user.setBranch(branch);
            user = userRepository.save(user);
        }
        return user;
    }

    protected void grant(UserEntity user, HrPermission... permissions) {
        for (HrPermission permission : permissions) {
            grantRepository.save(new HrUserPermissionEntity(user.getId(), permission, null));
        }
    }

    protected String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }

    protected ResultActions getJson(String path, UserEntity user) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(user)));
    }

    protected ResultActions postJson(String path, Object body, UserEntity user) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putJson(String path, Object body, UserEntity user) throws Exception {
        return mockMvc.perform(put(path).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected Map<String, Object> employeeBody(String firstName, String lastName, BranchEntity branch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("employmentType", "FULL_TIME");
        body.put("hireDate", "2025-01-15");
        body.put("primaryBranchId", branch == null ? null : branch.getId());
        body.put("emergencyContacts", List.of(Map.of("name", "Kojo " + lastName, "relationship", "Brother", "phone", "+233201234567")));
        return body;
    }

    protected UUID createEmployee(UserEntity actor, String firstName, String lastName, BranchEntity branch) throws Exception {
        return UUID.fromString(json(postJson("/hr/employees", employeeBody(firstName, lastName, branch), actor)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()))
                .at("/summary/id").asText());
    }
}
