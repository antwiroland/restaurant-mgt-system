package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.common.Role;
import com.restaurantmanager.core.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Inventory. Stock is per branch: managers and cashiers who belong to a branch always work on their branch's stock;
 * admins (and staff without a branch) pick one with {@code branchId}, or, when reading, see every branch together.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService inventoryService;
    private final BranchRepository branchRepository;

    public InventoryController(InventoryService inventoryService, BranchRepository branchRepository) {
        this.inventoryService = inventoryService;
        this.branchRepository = branchRepository;
    }

    @GetMapping("/ingredients")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.IngredientView> ingredients(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam(required = false) UUID branchId) {
        return inventoryService.ingredients(readScope(principal, branchId));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.IngredientView> lowStock(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam(required = false) UUID branchId) {
        return inventoryService.lowStock(readScope(principal, branchId));
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView create(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestParam(required = false) UUID branchId,
                                                  @Valid @RequestBody InventoryService.IngredientRequest request) {
        UUID location = request.initialStock() != null && request.initialStock().signum() > 0
                ? writeLocation(principal, branchId)
                : readLocationOrNull(principal, branchId);
        return inventoryService.saveIngredient(null, request, location);
    }

    @PutMapping("/ingredients/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView update(@AuthenticationPrincipal UserPrincipal principal,
                                                  @PathVariable UUID id,
                                                  @RequestParam(required = false) UUID branchId,
                                                  @Valid @RequestBody InventoryService.IngredientRequest request) {
        return inventoryService.saveIngredient(id, request, readLocationOrNull(principal, branchId));
    }

    @PostMapping("/ingredients/{id}/restock")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public InventoryService.IngredientView restock(@PathVariable UUID id, @Valid @RequestBody RestockRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return inventoryService.restock(id, writeLocation(principal, request.branchId()), request.quantity(), request.unitCost(),
                request.supplier(), request.note(), principal.userId());
    }

    @PostMapping("/ingredients/{id}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.IngredientView adjust(@PathVariable UUID id, @Valid @RequestBody AdjustRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return inventoryService.adjust(id, writeLocation(principal, request.branchId()), request.reason(), request.delta(),
                request.countedQuantity(), request.note(), principal.userId());
    }

    @GetMapping("/ingredients/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<InventoryService.MovementView> movements(@AuthenticationPrincipal UserPrincipal principal,
                                                         @PathVariable UUID id,
                                                         @RequestParam(required = false) UUID branchId) {
        return inventoryService.movements(id, readScope(principal, branchId));
    }

    @GetMapping("/menu-items/{menuItemId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.RecipeLineView> recipe(@PathVariable UUID menuItemId) {
        return inventoryService.recipe(menuItemId);
    }

    @PutMapping("/menu-items/{menuItemId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<InventoryService.RecipeLineView> setRecipe(@PathVariable UUID menuItemId,
                                                           @Valid @RequestBody List<InventoryService.@Valid RecipeLineRequest> lines) {
        return inventoryService.setRecipe(menuItemId, lines);
    }

    /** Ingredients a modifier option (e.g. "Large") uses per unit ordered, on top of the dish's recipe. */
    @GetMapping("/modifier-options/{optionId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    public List<InventoryService.RecipeLineView> modifierRecipe(@PathVariable UUID optionId) {
        return inventoryService.modifierRecipe(optionId);
    }

    @PutMapping("/modifier-options/{optionId}/recipe")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<InventoryService.RecipeLineView> setModifierRecipe(@PathVariable UUID optionId,
                                                                   @Valid @RequestBody List<InventoryService.@Valid RecipeLineRequest> lines) {
        return inventoryService.setModifierRecipe(optionId, lines);
    }

    /** Food cost for a period (default the last 30 days): ingredient use, waste and count differences against sales. */
    @GetMapping("/reports/food-cost")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public InventoryService.FoodCostReport foodCost(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestParam(required = false) Instant from,
                                                    @RequestParam(required = false) Instant to,
                                                    @RequestParam(required = false) UUID branchId) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        return inventoryService.foodCost(start, end, readScope(principal, branchId));
    }

    // ------------------------------------------------------------ which stock

    private boolean branchBound(UserPrincipal principal) {
        return (principal.role() == Role.MANAGER || principal.role() == Role.CASHIER) && principal.branchId() != null;
    }

    private boolean anyBranches() {
        return branchRepository.count() > 0;
    }

    private UUID ownOrRequested(UserPrincipal principal, UUID requested) {
        if (branchBound(principal)) {
            if (requested != null && !requested.equals(principal.branchId())) {
                throw new ApiException(403, "You can only work on your own branch's stock");
            }
            return principal.branchId();
        }
        return requested;
    }

    /** Reading: one branch, or every branch together when none is given (and the restaurant has branches). */
    private InventoryService.Scope readScope(UserPrincipal principal, UUID requested) {
        UUID branch = ownOrRequested(principal, requested);
        if (branch != null) {
            return InventoryService.Scope.location(branch);
        }
        return anyBranches() ? InventoryService.Scope.everywhere() : InventoryService.Scope.location(null);
    }

    private UUID readLocationOrNull(UserPrincipal principal, UUID requested) {
        return ownOrRequested(principal, requested);
    }

    /** Changing stock: it must say where, once the restaurant has branches. */
    private UUID writeLocation(UserPrincipal principal, UUID requested) {
        UUID branch = ownOrRequested(principal, requested);
        if (branch == null && anyBranches()) {
            throw new ApiException(400, "Choose the branch whose stock this is");
        }
        return branch;
    }

    public record RestockRequest(@NotNull BigDecimal quantity, String note, BigDecimal unitCost,
                                 @Size(max = 120) String supplier, UUID branchId) {}

    public record AdjustRequest(@NotNull StockMovementEntity.Reason reason, BigDecimal delta, BigDecimal countedQuantity,
                                String note, UUID branchId) {}
}
