package com.restaurantmanager.core.menu;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.dto.AvailabilityUpdateRequest;
import com.restaurantmanager.core.menu.dto.CategoryRequest;
import com.restaurantmanager.core.menu.dto.CategoryResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierGroupRequest;
import com.restaurantmanager.core.menu.dto.MenuModifierGroupResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierOptionRequest;
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
        MenuItemEntity menuItem = findActiveMenuItem(menuItemId);
        return buildModifierGroupResponses(menuItem.getId());
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuModifierGroupResponse createModifierGroup(UUID menuItemId, MenuModifierGroupRequest request) {
        MenuItemEntity menuItem = findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = new MenuModifierGroupEntity();
        group.setMenuItem(menuItem);
        applyModifierGroupRequest(group, request);
        MenuModifierGroupEntity saved = modifierGroupRepository.save(group);
        return toModifierGroupResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuModifierGroupResponse updateModifierGroup(UUID menuItemId, UUID groupId, MenuModifierGroupRequest request) {
        findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = findModifierGroup(menuItemId, groupId);
        applyModifierGroupRequest(group, request);
        MenuModifierGroupEntity saved = modifierGroupRepository.save(group);
        return toModifierGroupResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public void deleteModifierGroup(UUID menuItemId, UUID groupId) {
        findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = findModifierGroup(menuItemId, groupId);
        group.setActive(false);
        modifierGroupRepository.save(group);

        List<MenuModifierOptionEntity> options = modifierOptionRepository
                .findByGroup_IdAndActiveTrueOrderByDisplayOrderAsc(groupId);
        for (MenuModifierOptionEntity option : options) {
            option.setActive(false);
        }
        if (!options.isEmpty()) {
            modifierOptionRepository.saveAll(options);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuModifierOptionResponse createModifierOption(UUID menuItemId,
                                                           UUID groupId,
                                                           MenuModifierOptionRequest request) {
        findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = findModifierGroup(menuItemId, groupId);

        MenuModifierOptionEntity option = new MenuModifierOptionEntity();
        option.setGroup(group);
        applyModifierOptionRequest(option, request);
        MenuModifierOptionEntity saved = modifierOptionRepository.save(option);
        return toModifierOptionResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public MenuModifierOptionResponse updateModifierOption(UUID menuItemId,
                                                           UUID groupId,
                                                           UUID optionId,
                                                           MenuModifierOptionRequest request) {
        findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = findModifierGroup(menuItemId, groupId);
        MenuModifierOptionEntity option = modifierOptionRepository.findById(optionId)
                .orElseThrow(() -> new ApiException(404, "Modifier option not found"));
        if (!option.getGroup().getId().equals(group.getId())) {
            throw new ApiException(404, "Modifier option not found");
        }
        applyModifierOptionRequest(option, request);
        MenuModifierOptionEntity saved = modifierOptionRepository.save(option);
        return toModifierOptionResponse(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID}, allEntries = true)
    public void deleteModifierOption(UUID menuItemId, UUID groupId, UUID optionId) {
        findActiveMenuItem(menuItemId);
        MenuModifierGroupEntity group = findModifierGroup(menuItemId, groupId);
        MenuModifierOptionEntity option = modifierOptionRepository.findById(optionId)
                .orElseThrow(() -> new ApiException(404, "Modifier option not found"));
        if (!option.getGroup().getId().equals(group.getId())) {
            throw new ApiException(404, "Modifier option not found");
        }
        option.setActive(false);
        modifierOptionRepository.save(option);
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

    private void applyModifierGroupRequest(MenuModifierGroupEntity group, MenuModifierGroupRequest request) {
        Bounds bounds = normalizeBounds(request.selectionType(), request.required(), request.minSelect(), request.maxSelect());
        group.setName(request.name().trim());
        group.setSelectionType(request.selectionType());
        group.setRequired(request.required());
        group.setMinSelect(bounds.minSelect());
        group.setMaxSelect(bounds.maxSelect());
        group.setDisplayOrder(request.displayOrder());
        group.setActive(request.active() == null || request.active());
    }

    private void applyModifierOptionRequest(MenuModifierOptionEntity option, MenuModifierOptionRequest request) {
        option.setName(request.name().trim());
        option.setPriceDelta(request.priceDelta());
        option.setDisplayOrder(request.displayOrder());
        option.setActive(request.active() == null || request.active());
    }

    private List<MenuModifierGroupResponse> buildModifierGroupResponses(UUID menuItemId) {
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
                                .map(this::toModifierOptionResponse)
                                .toList()
                ))
                .toList();
    }

    private MenuModifierGroupResponse toModifierGroupResponse(MenuModifierGroupEntity group) {
        List<MenuModifierOptionResponse> options = modifierOptionRepository
                .findByGroup_IdAndActiveTrueOrderByDisplayOrderAsc(group.getId()).stream()
                .map(this::toModifierOptionResponse)
                .toList();
        return new MenuModifierGroupResponse(
                group.getId(),
                group.getName(),
                group.getSelectionType(),
                group.isRequired(),
                group.getMinSelect(),
                group.getMaxSelect(),
                options
        );
    }

    private MenuModifierOptionResponse toModifierOptionResponse(MenuModifierOptionEntity option) {
        return new MenuModifierOptionResponse(option.getId(), option.getName(), option.getPriceDelta());
    }

    private MenuItemEntity findActiveMenuItem(UUID menuItemId) {
        MenuItemEntity menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ApiException(404, "Menu item not found"));
        if (!menuItem.isActive()) {
            throw new ApiException(404, "Menu item not found");
        }
        return menuItem;
    }

    private MenuModifierGroupEntity findModifierGroup(UUID menuItemId, UUID groupId) {
        MenuModifierGroupEntity group = modifierGroupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(404, "Modifier group not found"));
        if (!group.getMenuItem().getId().equals(menuItemId)) {
            throw new ApiException(404, "Modifier group not found");
        }
        return group;
    }

    private Bounds normalizeBounds(ModifierSelectionType selectionType, boolean required, Integer minSelect, Integer maxSelect) {
        int normalizedMin = minSelect != null ? minSelect : (required ? 1 : 0);
        Integer normalizedMax = maxSelect;

        if (required && normalizedMin == 0) {
            throw new ApiException(400, "Required modifier group must allow at least one selection");
        }

        if (selectionType == ModifierSelectionType.SINGLE) {
            if (normalizedMin > 1) {
                throw new ApiException(400, "SINGLE modifier group minSelect cannot exceed 1");
            }
            if (normalizedMax != null && normalizedMax != 1) {
                throw new ApiException(400, "SINGLE modifier group maxSelect must be 1");
            }
            normalizedMax = 1;
        } else if (normalizedMax != null && normalizedMax < normalizedMin) {
            throw new ApiException(400, "maxSelect must be greater than or equal to minSelect");
        }

        return new Bounds(normalizedMin, normalizedMax);
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

    private record Bounds(Integer minSelect, Integer maxSelect) {
    }
}
