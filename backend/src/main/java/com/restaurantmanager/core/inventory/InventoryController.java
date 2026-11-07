package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/ingredients")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.IngredientView> ingredients() {
        return inventoryService.ingredients();
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.IngredientView> lowStock() {
        return inventoryService.lowStock();
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView create(@Valid @RequestBody InventoryService.IngredientRequest request) {
        return inventoryService.saveIngredient(null, request);
    }

    @PutMapping("/ingredients/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView update(@PathVariable UUID id, @Valid @RequestBody InventoryService.IngredientRequest request) {
        return inventoryService.saveIngredient(id, request);
    }

    @PostMapping("/ingredients/{id}/restock")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public InventoryService.IngredientView restock(@PathVariable UUID id, @Valid @RequestBody RestockRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return inventoryService.restock(id, request.quantity(), request.note(), principal.userId());
    }

    @PostMapping("/ingredients/{id}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView adjust(@PathVariable UUID id, @Valid @RequestBody AdjustRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return inventoryService.adjust(id, request.reason(), request.delta(), request.countedQuantity(), request.note(), principal.userId());
    }

    @GetMapping("/ingredients/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<InventoryService.MovementView> movements(@PathVariable UUID id) {
        return inventoryService.movements(id);
    }

    @GetMapping("/menu-items/{menuItemId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.RecipeLineView> recipe(@PathVariable UUID menuItemId) {
        return inventoryService.recipe(menuItemId);
    }

    @PutMapping("/menu-items/{menuItemId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<InventoryService.RecipeLineView> setRecipe(@PathVariable UUID menuItemId,
                                                           @RequestBody List<InventoryService.@Valid RecipeLineRequest> lines) {
        return inventoryService.setRecipe(menuItemId, lines);
    }

    public record RestockRequest(@NotNull BigDecimal quantity, String note) {}

    public record AdjustRequest(@NotNull StockMovementEntity.Reason reason, BigDecimal delta, BigDecimal countedQuantity, String note) {}
}
