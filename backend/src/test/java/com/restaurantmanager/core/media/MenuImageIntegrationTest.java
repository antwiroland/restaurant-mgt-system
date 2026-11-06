package com.restaurantmanager.core.media;

import com.restaurantmanager.core.BaseIntegrationTest;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.menu.CategoryEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuImageIntegrationTest extends BaseIntegrationTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private UserEntity manager;
    private MenuItemEntity item;

    @BeforeEach
    void setUpData() {
        manager = createUser("Manager", "+233320000001", "img-manager@x.com", "secret123", Role.MANAGER);
        CategoryEntity category = new CategoryEntity();
        category.setName("Img-" + Instant.now().toEpochMilli());
        category.setDisplayOrder(1);
        category.setActive(true);
        category = categoryRepository.save(category);
        item = new MenuItemEntity();
        item.setCategory(category);
        item.setName("Red Red");
        item.setPrice(new BigDecimal("35.00"));
        item.setAvailable(true);
        item.setActive(true);
        item = menuItemRepository.save(item);
    }

    @Test
    void givenPng_whenUploaded_thenStoredLocallyServedAndLinkedToItem() throws Exception {
        String body = mockMvc.perform(multipart("/menu/items/" + item.getId() + "/image")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(body).get("imageUrl").asText();
        assertTrue(url.endsWith(".png"));
        assertEquals(url, menuItemRepository.findById(item.getId()).orElseThrow().getImageUrl());

        String path = url.substring(url.indexOf("/media/"));
        mockMvc.perform(get(path)).andExpect(status().isOk());

        mockMvc.perform(delete("/menu/items/" + item.getId() + "/image")
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isNoContent());
        assertNull(menuItemRepository.findById(item.getId()).orElseThrow().getImageUrl());
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void givenFileThatIsNotAnImage_whenUploaded_then400EvenIfDeclaredAsImage() throws Exception {
        mockMvc.perform(multipart("/menu/items/" + item.getId() + "/image")
                        .file(new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", "Bearer " + accessToken(manager)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenCashier_whenUploading_then403() throws Exception {
        UserEntity cashier = createUser("Cashier", "+233320000002", "img-cashier@x.com", "secret123", Role.CASHIER);
        mockMvc.perform(multipart("/menu/items/" + item.getId() + "/image")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + accessToken(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cloudinarySignature_matchesDocumentedExample() {
        MediaProps props = new MediaProps();
        props.getCloudinary().setCloudName("demo");
        props.getCloudinary().setApiKey("1234");
        props.getCloudinary().setApiSecret("abcd");
        CloudinaryMediaStorage storage = new CloudinaryMediaStorage(props);
        Map<String, String> params = new TreeMap<>();
        params.put("eager", "w_400,h_300,c_pad|w_260,h_200,c_crop");
        params.put("public_id", "sample_image");
        params.put("timestamp", "1315060510");

        assertEquals("bfd09f95f331f558cbd1320e67aa8d488770583e", storage.sign(params));
    }
}
