package com.restaurantmanager.core.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuModifierGroupEntity;
import com.restaurantmanager.core.menu.MenuModifierOptionEntity;
import com.restaurantmanager.core.menu.ModifierSelectionType;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editing a group cart, modifiers, leaving, cancelling, expiry and who may view a session. */
class GroupOrderSessionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GroupOrderSessionRepository groupSessionRepository;
    @Autowired
    private OrderService orderService;

    @Test
    void givenOwnItem_whenUpdateQuantityAndNotes_thenCartTotalChanges() throws Exception {
        Group g = newGroup("31");
        MenuItemEntity dish = createMenuItem("Upd", "20.00");
        String itemId = addItem(g, g.host, g.hostPid, dish.getId(), null);

        mockMvc.perform(patch(base(g) + "/items/" + itemId)
                        .header("Authorization", bearer(g.host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"notes\":\"no onions\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupTotal").value(60.00))
                .andExpect(jsonPath("$.participants[0].items[0].notes").value("no onions"));
    }

    @Test
    void givenSomeoneElsesItem_whenUpdate_then403() throws Exception {
        Group g = newGroup("32");
        String itemId = addItem(g, g.guest, g.guestPid, createMenuItem("Other", "10.00").getId(), null);

        mockMvc.perform(patch(base(g) + "/items/" + itemId)
                        .header("Authorization", bearer(g.host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenOwnItem_whenRemove_thenGoneFromCart() throws Exception {
        Group g = newGroup("33");
        String itemId = addItem(g, g.guest, g.guestPid, createMenuItem("Rm", "15.00").getId(), null);

        mockMvc.perform(delete(base(g) + "/items/" + itemId).header("Authorization", bearer(g.guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupTotal").value(0));
    }

    @Test
    void givenHost_whenRemoveGuestItem_thenAllowed_butGuestCannotRemoveHostItem() throws Exception {
        Group g = newGroup("34");
        MenuItemEntity dish = createMenuItem("Both", "12.00");
        String guestItem = addItem(g, g.guest, g.guestPid, dish.getId(), null);
        String hostItem = addItem(g, g.host, g.hostPid, dish.getId(), null);

        mockMvc.perform(delete(base(g) + "/items/" + guestItem).header("Authorization", bearer(g.host)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(base(g) + "/items/" + hostItem).header("Authorization", bearer(g.guest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenItemWithModifiers_whenAddedAndFinalized_thenPricedAndCarriedIntoOrder() throws Exception {
        Group g = newGroup("35");
        MenuItemEntity dish = createMenuItem("Jollof", "50.00");
        MenuModifierOptionEntity large = createRequiredOption(dish, "Size", "Large", "10.00");

        String body = mockMvc.perform(post(base(g) + "/items")
                        .header("Authorization", bearer(g.host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + g.hostPid + "\",\"items\":[{\"menuItemId\":\"" + dish.getId()
                                + "\",\"quantity\":2,\"modifierOptionIds\":[\"" + large.getId() + "\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupTotal").value(120.00))
                .andExpect(jsonPath("$.participants[0].items[0].price").value(60.00))
                .andExpect(jsonPath("$.participants[0].items[0].modifiers[0].optionName").value("Large"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(large.getId().toString());

        mockMvc.perform(post(base(g) + "/finalize")
                        .header("Authorization", bearer(g.host))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PICKUP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Large"))
                .andExpect(jsonPath("$.subtotal").value(120.00));
    }

    @Test
    void givenRequiredModifierMissing_whenAddItem_then400() throws Exception {
        Group g = newGroup("36");
        MenuItemEntity dish = createMenuItem("NeedsSize", "30.00");
        createRequiredOption(dish, "Size", "Small", "0.00");

        mockMvc.perform(post(base(g) + "/items")
                        .header("Authorization", bearer(g.host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + g.hostPid + "\",\"items\":[{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenGuest_whenLeaves_thenTheyAndTheirItemsAreGone() throws Exception {
        Group g = newGroup("37");
        addItem(g, g.guest, g.guestPid, createMenuItem("Leave", "18.00").getId(), null);

        mockMvc.perform(post(base(g) + "/leave").header("Authorization", bearer(g.guest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(base(g)).header("Authorization", bearer(g.host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andExpect(jsonPath("$.groupTotal").value(0));
        mockMvc.perform(get(base(g)).header("Authorization", bearer(g.guest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenHost_whenLeaves_then409() throws Exception {
        Group g = newGroup("38");
        mockMvc.perform(post(base(g) + "/leave").header("Authorization", bearer(g.host)))
                .andExpect(status().isConflict());
    }

    @Test
    void givenHost_whenCancels_thenCancelledAndCartLocked() throws Exception {
        Group g = newGroup("39");
        MenuItemEntity dish = createMenuItem("Cxl", "11.00");

        mockMvc.perform(post(base(g) + "/cancel").header("Authorization", bearer(g.guest)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base(g) + "/cancel").header("Authorization", bearer(g.host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post(base(g) + "/items")
                        .header("Authorization", bearer(g.guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + g.guestPid + "\",\"items\":[{\"menuItemId\":\"" + dish.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void givenOutsider_whenViewSession_then403_butStaffCanView() throws Exception {
        Group g = newGroup("40");
        UserEntity outsider = createUser("Out", "+233279000040", "out40@x.com", "secret123", Role.CUSTOMER);
        UserEntity cashier = createUser("Cash", "+233279000140", "cash40@x.com", "secret123", Role.CASHIER);

        mockMvc.perform(get(base(g)).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base(g)).header("Authorization", bearer(cashier)))
                .andExpect(status().isOk());
    }

    @Test
    void givenIdleOpenSession_whenExpiryRuns_thenCancelled() throws Exception {
        Group g = newGroup("41");
        Group fresh = newGroup("42");
        GroupOrderSessionEntity idle = groupSessionRepository.findBySessionCodeIgnoreCase(g.code).orElseThrow();
        idle.setUpdatedAt(Instant.now().minus(25, ChronoUnit.HOURS));
        groupSessionRepository.save(idle);

        orderService.expireIdleGroupSessions(Instant.now().minus(24, ChronoUnit.HOURS));

        assertThat(groupSessionRepository.findBySessionCodeIgnoreCase(g.code).orElseThrow().getStatus())
                .isEqualTo(GroupSessionStatus.CANCELLED);
        assertThat(groupSessionRepository.findBySessionCodeIgnoreCase(fresh.code).orElseThrow().getStatus())
                .isEqualTo(GroupSessionStatus.OPEN);
    }

    private record Group(String code, UserEntity host, String hostPid, UserEntity guest, String guestPid) {
    }

    private Group newGroup(String n) throws Exception {
        UserEntity host = createUser("Host" + n, "+2332710000" + n, "host" + n + "@x.com", "secret123", Role.CUSTOMER);
        UserEntity guest = createUser("Guest" + n, "+2332720000" + n, "guest" + n + "@x.com", "secret123", Role.CUSTOMER);
        JsonNode created = json(mockMvc.perform(post("/orders/group/sessions")
                        .header("Authorization", bearer(host))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String code = created.get("sessionCode").asText();
        JsonNode joined = json(mockMvc.perform(post("/orders/group/sessions/" + code + "/join")
                        .header("Authorization", bearer(guest))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new Group(code, host, created.get("hostParticipantId").asText(), guest, joined.get("participantId").asText());
    }

    /** Adds one line and returns its item id. */
    private String addItem(Group g, UserEntity who, String participantId, UUID menuItemId, String notes) throws Exception {
        JsonNode view = json(mockMvc.perform(post(base(g) + "/items")
                        .header("Authorization", bearer(who))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + participantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId
                                + "\",\"quantity\":1" + (notes == null ? "" : ",\"notes\":\"" + notes + "\"") + "}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String itemId = null;
        for (JsonNode participant : view.get("participants")) {
            if (participant.get("participantId").asText().equals(participantId)) {
                JsonNode items = participant.get("items");
                itemId = items.get(items.size() - 1).get("itemId").asText();
            }
        }
        assertThat(itemId).isNotNull();
        return itemId;
    }

    private String base(Group g) {
        return "/orders/group/sessions/" + g.code;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + accessToken(user);
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private MenuItemEntity createMenuItem(String name, String price) {
        CategoryEntity category = new CategoryEntity();
        category.setName("Group-" + name + "-" + System.nanoTime());
        category.setDisplayOrder(1);
        category.setActive(true);
        CategoryEntity savedCategory = categoryRepository.save(category);
        MenuItemEntity item = new MenuItemEntity();
        item.setCategory(savedCategory);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setActive(true);
        return menuItemRepository.save(item);
    }

    private MenuModifierOptionEntity createRequiredOption(MenuItemEntity dish, String groupName, String optionName, String delta) {
        MenuModifierGroupEntity group = new MenuModifierGroupEntity();
        group.setMenuItem(dish);
        group.setName(groupName);
        group.setSelectionType(ModifierSelectionType.SINGLE);
        group.setRequired(true);
        group.setDisplayOrder(1);
        group.setActive(true);
        MenuModifierGroupEntity savedGroup = menuModifierGroupRepository.save(group);
        MenuModifierOptionEntity option = new MenuModifierOptionEntity();
        option.setGroup(savedGroup);
        option.setName(optionName);
        option.setPriceDelta(new BigDecimal(delta));
        option.setDisplayOrder(1);
        option.setActive(true);
        return menuModifierOptionRepository.save(option);
    }
}
