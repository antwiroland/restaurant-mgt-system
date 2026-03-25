package com.restaurantmanager.core.menu;

import com.restaurantmanager.core.menu.dto.AvailabilityUpdateRequest;
import com.restaurantmanager.core.menu.dto.CategoryRequest;
import com.restaurantmanager.core.menu.dto.CategoryResponse;
import com.restaurantmanager.core.menu.dto.MenuItemRequest;
import com.restaurantmanager.core.menu.dto.MenuItemResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierGroupRequest;
import com.restaurantmanager.core.menu.dto.MenuModifierGroupResponse;
import com.restaurantmanager.core.menu.dto.MenuModifierOptionRequest;
import com.restaurantmanager.core.menu.dto.MenuModifierOptionResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/menu")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return menuService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CategoryResponse updateCategory(@PathVariable UUID id,
                                           @Valid @RequestBody CategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(@PathVariable UUID id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/items")
    public List<MenuItemResponse> listItems(@RequestParam(required = false) UUID categoryId,
                                            @RequestParam(required = false) Boolean available,
                                            @RequestParam(name = "q", required = false) String query,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return menuService.listItems(categoryId, available, query, principal);
    }

    @GetMapping("/items/{id}")
    public MenuItemResponse getItem(@PathVariable UUID id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return menuService.getItem(id, principal);
    }

    @GetMapping("/items/{id}/modifiers")
    public List<MenuModifierGroupResponse> listModifiers(@PathVariable UUID id) {
        return menuService.listModifiers(id);
    }

    @PostMapping("/items/{id}/modifiers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuModifierGroupResponse createModifierGroup(@PathVariable UUID id,
                                                         @Valid @RequestBody MenuModifierGroupRequest request) {
        return menuService.createModifierGroup(id, request);
    }

    @PutMapping("/items/{menuItemId}/modifiers/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuModifierGroupResponse updateModifierGroup(@PathVariable UUID menuItemId,
                                                         @PathVariable UUID groupId,
                                                         @Valid @RequestBody MenuModifierGroupRequest request) {
        return menuService.updateModifierGroup(menuItemId, groupId, request);
    }

    @DeleteMapping("/items/{menuItemId}/modifiers/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteModifierGroup(@PathVariable UUID menuItemId, @PathVariable UUID groupId) {
        menuService.deleteModifierGroup(menuItemId, groupId);
    }

    @PostMapping("/items/{menuItemId}/modifiers/{groupId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuModifierOptionResponse createModifierOption(@PathVariable UUID menuItemId,
                                                           @PathVariable UUID groupId,
                                                           @Valid @RequestBody MenuModifierOptionRequest request) {
        return menuService.createModifierOption(menuItemId, groupId, request);
    }

    @PutMapping("/items/{menuItemId}/modifiers/{groupId}/options/{optionId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuModifierOptionResponse updateModifierOption(@PathVariable UUID menuItemId,
                                                           @PathVariable UUID groupId,
                                                           @PathVariable UUID optionId,
                                                           @Valid @RequestBody MenuModifierOptionRequest request) {
        return menuService.updateModifierOption(menuItemId, groupId, optionId, request);
    }

    @DeleteMapping("/items/{menuItemId}/modifiers/{groupId}/options/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteModifierOption(@PathVariable UUID menuItemId,
                                     @PathVariable UUID groupId,
                                     @PathVariable UUID optionId) {
        menuService.deleteModifierOption(menuItemId, groupId, optionId);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuItemResponse createItem(@Valid @RequestBody MenuItemRequest request) {
        return menuService.createMenuItem(request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MenuItemResponse updateItem(@PathVariable UUID id,
                                       @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateMenuItem(id, request);
    }

    @PatchMapping("/items/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public MenuItemResponse updateAvailability(@PathVariable UUID id,
                                               @Valid @RequestBody AvailabilityUpdateRequest request) {
        return menuService.updateAvailability(id, request);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteItem(@PathVariable UUID id) {
        menuService.deleteMenuItem(id);
    }
}
