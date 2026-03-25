package com.restaurantmanager.core.menu;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.dto.AvailabilityUpdateRequest;
import com.restaurantmanager.core.menu.dto.CategoryRequest;
import com.restaurantmanager.core.menu.dto.CategoryResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierGroupResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierOptionResponse;
import com.restaurantmanager.core.menu.dto.MenuItemRequest;
import com.restaurantmanager.core.menu.dto.MenuItemResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MenuService {
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuModifierGroupRepository modifierGroupRepository;
    private final MenuModifierOptionRepository modifierOptionRepository;

    public MenuService(CategoryRepository categoryRepository,
                       MenuItemRepository menuItemRepository,
                       MenuModifierGroupRepository modifierGroupRepository,
                       MenuModifierOptionRepository modifierOptionRepository) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.modifierGroupRepository = modifierGroupRepository;
        this.modifierOptionRepository = modifierOptionRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MENU_CATEGORIES)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ApiException(409, "Category name already exists");
        }
        CategoryEntity category = new CategoryEntity();
        applyCategoryRequest(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Category not found"));
        applyCategoryRequest(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public void deleteCategory(UUID id) {
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Category not found"));
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheConfig.MENU_ITEMS_PUBLIC,
            key = "T(java.util.Objects).toString(#categoryId,'_') + '|' + T(java.util.Objects).toString(#available,'_') + '|' + T(java.util.Objects).toString(#query,'_')",
            condition = "#principal == null"
    )
    public List<MenuItemResponse> listItems(UUID categoryId,
                                            Boolean available,
                                            String query,
                                            UserPrincipal principal) {
        boolean publicRequest = principal == null;
        return menuItemRepository.findByActiveTrue().stream()
                .filter(item -> categoryId == null || item.getCategory().getId().equals(categoryId))
                .filter(item -> {
                    if (available != null) {
                        return item.isAvailable() == available;
                    }
                    if (publicRequest) {
                        return item.isAvailable();
                    }
                    return true;
                })
                .filter(item -> matchesQuery(item, query))
                .sorted((a, b) -> {
                    int byCategory = Integer.compare(a.getCategory().getDisplayOrder(), b.getCategory().getDisplayOrder());
                    if (byCategory != 0) {
                        return byCategory;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .map(this::toMenuItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.MENU_ITEM_PUBLIC_BY_ID, key = "#id", condition = "#principal == null")
    public MenuItemResponse getItem(UUID id, UserPrincipal principal) {
        MenuItemEntity item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        if (!item.isActive()) {
            throw new ApiException(404, "Menu item not found");
        }
        if (principal == null && !item.isAvailable()) {
            throw new ApiException(404, "Menu item not found");
        }
        return toMenuItemResponse(item);
    }

    @Transactional(readOnly = true)
    public List<MenuModifierGroupResponse> listModifiers(UUID menuItemId) {
        MenuItemEntity menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        if (!menuItem.isActive()) {
            throw new ApiException(404, "Menu item not found");
        }

        List<MenuModifierOptionEntity> options = modifierOptionRepository
                .findByGroup_MenuItem_IdAndActiveTrueOrderByGroup_DisplayOrderAscDisplayOrderAsc(menuItemId);

        return modifierGroupRepository.findByMenuItem_IdAndActiveTrueOrderByDisplayOrderAsc(menuItemId).stream()
                .map(group -> new MenuModifierGroupResponse(
                        group.getId(),
                        group.getName(),
                        group.getSelectionType(),
                        group.isRequired(),
                        group.getMinSelect(),
                        group.getMaxSelect(),
                        options.stream()
                                .filter(option -> option.getGroup().getId().equals(group.getId()))
                                .map(option -> new MenuModifierOptionResponse(option.getId(), option.getName(), option.getPriceDelta()))
                                .toList()
                ))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuItemResponse createMenuItem(MenuItemRequest request) {
        CategoryEntity category = categoryRepository.findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new ApiException(404, "Category not found"));
        MenuItemEntity item = new MenuItemEntity();
        applyMenuItemRequest(item, request, category);
        return toMenuItemResponse(menuItemRepository.save(item));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuItemResponse updateMenuItem(UUID id, MenuItemRequest request) {
        CategoryEntity category = categoryRepository.findByIdAndActiveTrue(request.categoryId())
                .orElseThrow(() -> new ApiException(404, "Category not found"));
        MenuItemEntity item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        applyMenuItemRequest(item, request, category);
        return toMenuItemResponse(menuItemRepository.save(item));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuItemResponse updateAvailability(UUID id, AvailabilityUpdateRequest request) {
        MenuItemEntity item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        item.setAvailable(request.available());
        return toMenuItemResponse(menuItemRepository.save(item));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public void deleteMenuItem(UUID id) {
        MenuItemEntity item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        menuItemRepository.delete(item);
    }

    private boolean matchesQuery(MenuItemEntity item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return item.getName().toLowerCase(Locale.ROOT).contains(q)
                || (item.getDescription() != null && item.getDescription().toLowerCase(Locale.ROOT).contains(q));
    }

    private void applyCategoryRequest(CategoryEntity category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setDisplayOrder(request.displayOrder());
        category.setActive(request.active());
    }

    private void applyMenuItemRequest(MenuItemEntity item, MenuItemRequest request, CategoryEntity category) {
        item.setCategory(category);
        item.setName(request.name().trim());
        item.setDescription(blankToNull(request.description()));
        item.setPrice(request.price());
        item.setImageUrl(blankToNull(request.imageUrl()));
        item.setAvailable(request.available());
        item.setActive(true);
    }

    private CategoryResponse toCategoryResponse(CategoryEntity category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                category.isActive());
    }

    private MenuItemResponse toMenuItemResponse(MenuItemEntity item) {
        return new MenuItemResponse(
                item.getId(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getImageUrl(),
                item.isAvailable(),
                item.isActive());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
