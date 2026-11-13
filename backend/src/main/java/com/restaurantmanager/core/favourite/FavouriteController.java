package com.restaurantmanager.core.favourite;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.menu.dto.MenuItemResponse;
import com.restaurantmanager.core.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A customer's favourite dishes, shared between the web site and the mobile app. */
@RestController
@RequestMapping("/users/me/favourites")
@PreAuthorize("hasRole('CUSTOMER')")
public class FavouriteController {
    private final FavouriteRepository favourites;
    private final MenuItemRepository menuItems;

    public FavouriteController(FavouriteRepository favourites, MenuItemRepository menuItems) {
        this.favourites = favourites;
        this.menuItems = menuItems;
    }

    /** Favourite dishes, most recently added first. Dishes removed from the menu are left out. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<MenuItemResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        List<UUID> ids = favourites.findById_UserIdOrderByCreatedAtDesc(principal.userId()).stream()
                .map(f -> f.getId().getMenuItemId()).toList();
        Map<UUID, MenuItemEntity> byId = menuItems.findAllById(ids).stream()
                .collect(Collectors.toMap(MenuItemEntity::getId, Function.identity()));
        return ids.stream().map(byId::get)
                .filter(item -> item != null && item.isActive())
                .map(item -> new MenuItemResponse(item.getId(), item.getCategory().getId(), item.getCategory().getName(),
                        item.getName(), item.getDescription(), item.getPrice(), item.getImageUrl(), item.isAvailable(), item.isActive(), List.of()))
                .toList();
    }

    @PutMapping("/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void add(@PathVariable UUID menuItemId, @AuthenticationPrincipal UserPrincipal principal) {
        if (!menuItems.existsById(menuItemId)) {
            throw new ApiException(404, "Menu item not found");
        }
        FavouriteEntity.Key key = new FavouriteEntity.Key(principal.userId(), menuItemId);
        if (!favourites.existsById(key)) {
            favourites.save(new FavouriteEntity(principal.userId(), menuItemId));
        }
    }

    @DeleteMapping("/{menuItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable UUID menuItemId, @AuthenticationPrincipal UserPrincipal principal) {
        favourites.deleteById(new FavouriteEntity.Key(principal.userId(), menuItemId));
    }
}
