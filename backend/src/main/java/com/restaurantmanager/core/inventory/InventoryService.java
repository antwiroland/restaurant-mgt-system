package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.menu.MenuModifierOptionRepository;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderItemModifierEntity;
import com.restaurantmanager.core.order.OrderItemModifierRepository;
import com.restaurantmanager.core.order.OrderRepository;
import com.restaurantmanager.core.order.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ingredient stock, kept per location: each branch has its own stock (a null branch is the restaurant's own stock
 * when it has no branches). A branch that has never received or counted an ingredient does not track it, so its
 * orders use none of it and nothing is sold out there because of it.
 *
 * <p>Orders use the ingredients of each dish's recipe plus those of the modifier options chosen ("Large" uses more
 * rice). Inactive ingredients are ignored everywhere. Each movement keeps the cost per unit at the time, for food
 * cost.
 */
@Service
public class InventoryService {
    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final RecipeLineRepository recipeRepository;
    private final ModifierRecipeLineRepository modifierRecipeRepository;
    private final StockMovementRepository movementRepository;
    private final MenuItemStockoutRepository stockoutRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuModifierOptionRepository modifierOptionRepository;
    private final OrderItemModifierRepository orderItemModifierRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final CacheManager cacheManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean blockWhenInsufficient;

    public InventoryService(IngredientRepository ingredientRepository,
                            IngredientStockRepository stockRepository,
                            RecipeLineRepository recipeRepository,
                            ModifierRecipeLineRepository modifierRecipeRepository,
                            StockMovementRepository movementRepository,
                            MenuItemStockoutRepository stockoutRepository,
                            MenuItemRepository menuItemRepository,
                            MenuModifierOptionRepository modifierOptionRepository,
                            OrderItemModifierRepository orderItemModifierRepository,
                            OrderRepository orderRepository,
                            BranchRepository branchRepository,
                            CacheManager cacheManager,
                            SimpMessagingTemplate messagingTemplate,
                            ApplicationEventPublisher eventPublisher,
                            @Value("${inventory.block-when-insufficient:true}") boolean blockWhenInsufficient) {
        this.ingredientRepository = ingredientRepository;
        this.stockRepository = stockRepository;
        this.recipeRepository = recipeRepository;
        this.modifierRecipeRepository = modifierRecipeRepository;
        this.movementRepository = movementRepository;
        this.stockoutRepository = stockoutRepository;
        this.menuItemRepository = menuItemRepository;
        this.modifierOptionRepository = modifierOptionRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
        this.orderRepository = orderRepository;
        this.branchRepository = branchRepository;
        this.cacheManager = cacheManager;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.blockWhenInsufficient = blockWhenInsufficient;
    }

    /** Which stock a request is about: one location (branchId may be null), or every location. */
    public record Scope(boolean all, UUID branchId) {
        public static Scope location(UUID branchId) {
            return new Scope(false, branchId);
        }

        public static Scope everywhere() {
            return new Scope(true, null);
        }

        boolean includes(UUID location) {
            return all || Objects.equals(branchId, location);
        }
    }

    // ------------------------------------------------------------ order hooks

    @Transactional
    public void deductForOrder(OrderEntity order, List<OrderItemEntity> items) {
        UUID location = order.getBranch() == null ? null : order.getBranch().getId();
        Map<UUID, BigDecimal> required = new LinkedHashMap<>();
        Map<UUID, String> neededFor = new HashMap<>();

        Map<UUID, Integer> quantityByMenuItem = new HashMap<>();
        Map<UUID, String> nameByMenuItem = new HashMap<>();
        for (OrderItemEntity item : items) {
            quantityByMenuItem.merge(item.getMenuItem().getId(), item.getQuantity(), Integer::sum);
            nameByMenuItem.put(item.getMenuItem().getId(), item.getMenuItem().getName());
        }
        for (RecipeLineEntity line : recipeRepository.findByMenuItemIdIn(quantityByMenuItem.keySet())) {
            if (!line.getIngredient().isActive()) continue;
            BigDecimal need = line.getQuantity().multiply(BigDecimal.valueOf(quantityByMenuItem.get(line.getMenuItemId())));
            required.merge(line.getIngredient().getId(), need, BigDecimal::add);
            neededFor.putIfAbsent(line.getIngredient().getId(), nameByMenuItem.get(line.getMenuItemId()));
        }

        // Modifier options chosen on each line ("Large", "Add protein") use their own ingredients per unit.
        Map<UUID, OrderItemEntity> itemById = items.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(OrderItemEntity::getId, item -> item, (a, b) -> a));
        if (!itemById.isEmpty()) {
            List<OrderItemModifierEntity> chosen = orderItemModifierRepository.findByOrderItem_IdInOrderByCreatedAtAsc(itemById.keySet());
            Map<UUID, List<ModifierRecipeLineEntity>> byOption = modifierRecipeRepository.findByModifierOptionIdIn(chosen.stream()
                            .filter(m -> m.getModifierOption() != null)
                            .map(m -> m.getModifierOption().getId())
                            .collect(Collectors.toSet())).stream()
                    .collect(Collectors.groupingBy(ModifierRecipeLineEntity::getModifierOptionId));
            for (OrderItemModifierEntity modifier : chosen) {
                if (modifier.getModifierOption() == null) continue;
                OrderItemEntity item = itemById.get(modifier.getOrderItem().getId());
                for (ModifierRecipeLineEntity line : byOption.getOrDefault(modifier.getModifierOption().getId(), List.of())) {
                    if (!line.getIngredient().isActive()) continue;
                    required.merge(line.getIngredient().getId(), line.getQuantity().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
                    neededFor.putIfAbsent(line.getIngredient().getId(),
                            item.getMenuItem().getName() + " (" + modifier.getOptionNameSnapshot() + ")");
                }
            }
        }
        if (required.isEmpty()) {
            return;
        }

        // Only ingredients this location tracks; locked so two orders cannot use the same stock.
        List<IngredientStockEntity> rows = stockRepository.findByIngredient_IdInAndBranchIdOrderByIngredient_IdAsc(required.keySet(), location);
        if (rows.isEmpty()) {
            return;
        }
        if (blockWhenInsufficient) {
            for (IngredientStockEntity row : rows) {
                if (row.getStockQuantity().compareTo(required.get(row.getIngredient().getId())) < 0) {
                    throw new ApiException(409, neededFor.get(row.getIngredient().getId()) + " is out of stock (not enough "
                            + row.getIngredient().getName() + ")");
                }
            }
        }
        for (IngredientStockEntity row : rows) {
            IngredientEntity ingredient = row.getIngredient();
            move(row, required.get(ingredient.getId()).negate(), StockMovementEntity.Reason.ORDER, order.getId(), null, null,
                    ingredient.getCostPerUnit(), null);
        }
        order.setStockDeducted(true);
        afterStockChange(rows, location);
    }

    /** Puts ingredients back, where they came from, when an order is cancelled before the kitchen started on it. */
    @Transactional
    public void restoreForOrder(OrderEntity order, OrderStatus statusBeforeCancel) {
        if (!order.isStockDeducted()) {
            return;
        }
        if (statusBeforeCancel != OrderStatus.PENDING && statusBeforeCancel != OrderStatus.CONFIRMED) {
            return; // food already prepared: ingredients were consumed
        }
        Map<UUID, Map<UUID, BigDecimal>> byLocation = new HashMap<>();
        Map<UUID, BigDecimal> costByIngredient = new HashMap<>();
        for (StockMovementEntity movement : movementRepository.findByOrderId(order.getId())) {
            byLocation.computeIfAbsent(movement.getBranchId(), k -> new HashMap<>())
                    .merge(movement.getIngredientId(), movement.getChangeQuantity().negate(), BigDecimal::add);
            if (movement.getUnitCost() != null) costByIngredient.put(movement.getIngredientId(), movement.getUnitCost());
        }
        for (Map.Entry<UUID, Map<UUID, BigDecimal>> entry : byLocation.entrySet()) {
            Map<UUID, BigDecimal> toRestore = entry.getValue();
            toRestore.values().removeIf(quantity -> quantity.signum() <= 0);
            if (toRestore.isEmpty()) continue;
            List<IngredientStockEntity> rows = stockRepository.findByIngredient_IdInAndBranchIdOrderByIngredient_IdAsc(toRestore.keySet(), entry.getKey());
            for (IngredientStockEntity row : rows) {
                UUID ingredientId = row.getIngredient().getId();
                move(row, toRestore.get(ingredientId), StockMovementEntity.Reason.ORDER_CANCELLED, order.getId(), null, null,
                        costByIngredient.get(ingredientId), null);
            }
            afterStockChange(rows, entry.getKey());
        }
        order.setStockDeducted(false);
    }

    // ------------------------------------------------------------ stock management

    /** A delivery at a location. Starts tracking the ingredient there if it was not tracked yet. */
    @Transactional
    public IngredientView restock(UUID ingredientId, UUID location, BigDecimal quantity, BigDecimal unitCost, String supplier,
                                  String note, UUID actorId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(400, "Restock quantity must be positive");
        }
        if (unitCost != null && unitCost.signum() < 0) {
            throw new ApiException(400, "Unit cost cannot be negative");
        }
        IngredientStockEntity row = lockOrCreate(ingredientId, location);
        IngredientEntity ingredient = row.getIngredient();
        if (unitCost != null) {
            // The latest purchase price becomes the ingredient's cost from now on.
            ingredient.setCostPerUnit(unitCost);
        }
        move(row, quantity, StockMovementEntity.Reason.RESTOCK, null, note, actorId,
                unitCost != null ? unitCost : ingredient.getCostPerUnit(), supplier);
        afterStockChange(List.of(row), location);
        return view(ingredient, row, location);
    }

    /** Records waste or a stock count at a location; {@code countedQuantity} sets the level there. */
    @Transactional
    public IngredientView adjust(UUID ingredientId, UUID location, StockMovementEntity.Reason reason, BigDecimal delta,
                                 BigDecimal countedQuantity, String note, UUID actorId) {
        if (reason != StockMovementEntity.Reason.WASTE && reason != StockMovementEntity.Reason.ADJUSTMENT) {
            throw new ApiException(400, "Reason must be WASTE or ADJUSTMENT");
        }
        IngredientStockEntity row = lockOrCreate(ingredientId, location);
        BigDecimal change = countedQuantity != null ? countedQuantity.subtract(row.getStockQuantity()) : delta;
        if (change == null || change.signum() == 0) {
            if (countedQuantity != null) {
                // A count that matches still marks the ingredient as tracked at this location.
                return view(row.getIngredient(), row, location);
            }
            throw new ApiException(400, "Nothing to adjust");
        }
        if (reason == StockMovementEntity.Reason.WASTE && change.signum() > 0) {
            change = change.negate();
        }
        if (row.getStockQuantity().add(change).signum() < 0) {
            throw new ApiException(400, "Stock cannot go below zero");
        }
        move(row, change, reason, null, note, actorId, row.getIngredient().getCostPerUnit(), null);
        afterStockChange(List.of(row), location);
        return view(row.getIngredient(), row, location);
    }

    /** Creates or edits an ingredient. Opening stock (on create) goes to {@code location}. */
    @Transactional
    public IngredientView saveIngredient(UUID id, IngredientRequest request, UUID location) {
        IngredientEntity ingredient = id == null ? new IngredientEntity()
                : ingredientRepository.findById(id).orElseThrow(() -> new ApiException(404, "Ingredient not found"));
        String name = request.name().trim();
        if ((id == null || !name.equalsIgnoreCase(ingredient.getName())) && ingredientRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(409, "An ingredient with this name already exists");
        }
        boolean wasActive = id == null || ingredient.isActive();
        ingredient.setName(name);
        ingredient.setUnit(request.unit().trim());
        ingredient.setLowStockThreshold(request.lowStockThreshold() == null ? BigDecimal.ZERO : request.lowStockThreshold());
        ingredient.setCostPerUnit(request.costPerUnit());
        if (request.active() != null) {
            ingredient.setActive(request.active());
        }
        if (id == null) {
            ingredient.setStockQuantity(BigDecimal.ZERO);
        }
        IngredientEntity saved = ingredientRepository.save(ingredient);
        IngredientStockEntity row = null;
        if (id == null && request.initialStock() != null && request.initialStock().signum() > 0) {
            row = lockOrCreate(saved.getId(), location);
            move(row, request.initialStock(), StockMovementEntity.Reason.RESTOCK, null, "Opening stock", null, saved.getCostPerUnit(), null);
        }
        List<IngredientStockEntity> rows = stockRepository.findByIngredient_Id(saved.getId());
        for (UUID loc : rows.stream().map(IngredientStockEntity::getBranchId).collect(Collectors.toSet())) {
            afterStockChange(rows.stream().filter(r -> Objects.equals(r.getBranchId(), loc)).toList(), loc);
        }
        if (wasActive != saved.isActive()) {
            // Switching an ingredient on or off changes which dishes can be made.
            reevaluateEverywhere(recipeRepository.menuItemsUsing(List.of(saved.getId())));
        }
        return view(saved, row != null ? row : rows.stream().filter(r -> Objects.equals(r.getBranchId(), location)).findFirst().orElse(null), location);
    }

    @Transactional
    public List<RecipeLineView> setRecipe(UUID menuItemId, List<RecipeLineRequest> lines) {
        menuItemRepository.findById(menuItemId).orElseThrow(() -> new ApiException(404, "Menu item not found"));
        recipeRepository.deleteAll(recipeRepository.findByMenuItemId(menuItemId));
        recipeRepository.flush();
        List<RecipeLineEntity> rows = new ArrayList<>();
        for (RecipeLineRequest line : validLines(lines)) {
            RecipeLineEntity row = new RecipeLineEntity();
            row.setMenuItemId(menuItemId);
            row.setIngredient(ingredientRepository.findById(line.ingredientId())
                    .orElseThrow(() -> new ApiException(404, "Ingredient not found")));
            row.setQuantity(line.quantity());
            rows.add(row);
        }
        List<RecipeLineEntity> saved = recipeRepository.saveAll(rows);
        reevaluateEverywhere(List.of(menuItemId));
        return saved.stream().map(RecipeLineView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeLineView> recipe(UUID menuItemId) {
        return recipeRepository.findByMenuItemId(menuItemId).stream().map(RecipeLineView::from).toList();
    }

    /** Ingredients a modifier option uses per unit ordered, on top of the dish's recipe. */
    @Transactional
    public List<RecipeLineView> setModifierRecipe(UUID modifierOptionId, List<RecipeLineRequest> lines) {
        modifierOptionRepository.findById(modifierOptionId).orElseThrow(() -> new ApiException(404, "Modifier option not found"));
        modifierRecipeRepository.deleteAll(modifierRecipeRepository.findByModifierOptionId(modifierOptionId));
        modifierRecipeRepository.flush();
        List<ModifierRecipeLineEntity> rows = new ArrayList<>();
        for (RecipeLineRequest line : validLines(lines)) {
            ModifierRecipeLineEntity row = new ModifierRecipeLineEntity();
            row.setModifierOptionId(modifierOptionId);
            row.setIngredient(ingredientRepository.findById(line.ingredientId())
                    .orElseThrow(() -> new ApiException(404, "Ingredient not found")));
            row.setQuantity(line.quantity());
            rows.add(row);
        }
        return modifierRecipeRepository.saveAll(rows).stream().map(RecipeLineView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeLineView> modifierRecipe(UUID modifierOptionId) {
        return modifierRecipeRepository.findByModifierOptionId(modifierOptionId).stream().map(RecipeLineView::from).toList();
    }

    /** Ingredients with their stock in this scope (one location, or the total across locations). */
    @Transactional(readOnly = true)
    public List<IngredientView> ingredients(Scope scope) {
        Map<UUID, List<IngredientStockEntity>> rowsByIngredient = stockRepository.findAll().stream()
                .filter(row -> scope.includes(row.getBranchId()))
                .collect(Collectors.groupingBy(row -> row.getIngredient().getId()));
        return ingredientRepository.findAllByOrderByNameAsc().stream()
                .map(ingredient -> scope.all()
                        ? totalView(ingredient, rowsByIngredient.getOrDefault(ingredient.getId(), List.of()))
                        : view(ingredient, rowsByIngredient.getOrDefault(ingredient.getId(), List.of()).stream().findFirst().orElse(null), scope.branchId()))
                .toList();
    }

    /** Low stock per location in this scope (an ingredient low at two branches appears twice). */
    @Transactional(readOnly = true)
    public List<IngredientView> lowStock(Scope scope) {
        return stockRepository.findAll().stream()
                .filter(row -> scope.includes(row.getBranchId()))
                .filter(row -> row.getIngredient().isActive() && row.isLow())
                .sorted(Comparator.comparing((IngredientStockEntity row) -> row.getIngredient().getName()))
                .map(row -> view(row.getIngredient(), row, row.getBranchId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID ingredientId, Scope scope) {
        Map<UUID, String> branchNames = branchNames();
        return movementRepository.findByIngredientIdOrderByCreatedAtDesc(ingredientId).stream()
                .filter(movement -> scope.includes(movement.getBranchId()))
                .map(movement -> MovementView.from(movement, branchNames.get(movement.getBranchId())))
                .toList();
    }

    /**
     * Food cost for a period: ingredients used by orders (net of cancellations), waste and count differences, all
     * at the cost recorded when they moved, against net sales; and each dish's recipe cost and margin today.
     */
    @Transactional(readOnly = true)
    public FoodCostReport foodCost(Instant from, Instant to, Scope scope) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ApiException(400, "Give a from date before the to date");
        }
        Map<UUID, IngredientEntity> ingredients = ingredientRepository.findAll().stream()
                .collect(Collectors.toMap(IngredientEntity::getId, i -> i));
        Map<UUID, FoodCostLineBuilder> lines = new LinkedHashMap<>();
        for (StockMovementEntity movement : movementRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (!scope.includes(movement.getBranchId())) continue;
            FoodCostLineBuilder line = lines.computeIfAbsent(movement.getIngredientId(), id -> new FoodCostLineBuilder(ingredients.get(id)));
            line.add(movement);
        }
        List<FoodCostLine> ingredientLines = lines.values().stream()
                .filter(line -> line.ingredient != null)
                .map(FoodCostLineBuilder::build)
                .sorted(Comparator.comparing(FoodCostLine::usedCost).reversed())
                .toList();

        BigDecimal netSales = orderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatusNot(from, to, OrderStatus.CANCELLED).stream()
                .filter(order -> order.getVoidedAt() == null)
                .filter(order -> scope.includes(order.getBranch() == null ? null : order.getBranch().getId()))
                .map(OrderEntity::discountedSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal usedCost = sum(ingredientLines.stream().map(FoodCostLine::usedCost).toList());
        BigDecimal wasteCost = sum(ingredientLines.stream().map(FoodCostLine::wasteCost).toList());
        BigDecimal countCost = sum(ingredientLines.stream().map(FoodCostLine::countDifferenceCost).toList());
        BigDecimal purchases = sum(ingredientLines.stream().map(FoodCostLine::purchasesCost).toList());
        BigDecimal foodCostPercent = netSales.signum() == 0 ? null
                : usedCost.multiply(BigDecimal.valueOf(100)).divide(netSales, 1, RoundingMode.HALF_UP);
        int missingCost = (int) ingredientLines.stream().filter(FoodCostLine::costMissing).count();

        return new FoodCostReport(from, to, scope.all() ? null : scope.branchId(), netSales, usedCost, wasteCost, countCost, purchases,
                foodCostPercent, missingCost, ingredientLines, dishCosts());
    }

    // ------------------------------------------------------------ internals

    private List<RecipeLineRequest> validLines(List<RecipeLineRequest> lines) {
        Set<UUID> seen = new HashSet<>();
        for (RecipeLineRequest line : lines) {
            if (!seen.add(line.ingredientId())) {
                throw new ApiException(400, "Each ingredient may appear once in a recipe");
            }
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new ApiException(400, "Recipe quantities must be positive");
            }
        }
        return lines;
    }

    private IngredientStockEntity lockOrCreate(UUID ingredientId, UUID location) {
        IngredientEntity ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new ApiException(404, "Ingredient not found"));
        if (location != null && !branchRepository.existsById(location)) {
            throw new ApiException(404, "Branch not found");
        }
        List<IngredientStockEntity> locked = stockRepository.findByIngredient_IdInAndBranchIdOrderByIngredient_IdAsc(List.of(ingredientId), location);
        if (!locked.isEmpty()) {
            return locked.get(0);
        }
        IngredientStockEntity row = new IngredientStockEntity();
        row.setIngredient(ingredient);
        row.setBranchId(location);
        row.setStockQuantity(BigDecimal.ZERO);
        return stockRepository.saveAndFlush(row);
    }

    private void move(IngredientStockEntity row, BigDecimal change, StockMovementEntity.Reason reason, UUID orderId, String note,
                      UUID actorId, BigDecimal unitCost, String supplier) {
        row.setStockQuantity(row.getStockQuantity().add(change));
        stockRepository.save(row);
        IngredientEntity ingredient = row.getIngredient();
        // The ingredient keeps the total across locations.
        ingredient.setStockQuantity(ingredient.getStockQuantity().add(change));
        ingredientRepository.save(ingredient);
        StockMovementEntity movement = new StockMovementEntity();
        movement.setIngredientId(ingredient.getId());
        movement.setBranchId(row.getBranchId());
        movement.setChangeQuantity(change);
        movement.setBalanceAfter(row.getStockQuantity());
        movement.setReason(reason);
        movement.setOrderId(orderId);
        movement.setNote(note == null || note.isBlank() ? null : note.trim());
        movement.setActorUserId(actorId);
        movement.setUnitCost(unitCost);
        movement.setSupplier(supplier == null || supplier.isBlank() ? null : supplier.trim());
        movementRepository.save(movement);
    }

    private void afterStockChange(Collection<IngredientStockEntity> rows, UUID location) {
        String where = location == null ? null : branchNames().get(location);
        for (IngredientStockEntity row : rows) {
            IngredientEntity ingredient = row.getIngredient();
            boolean low = ingredient.isActive() && row.isLow();
            if (low && !row.isLowStockAlerted()) {
                row.setLowStockAlerted(true);
                stockRepository.save(row);
                LowStockEvent event = new LowStockEvent(ingredient.getId(), ingredient.getName(), ingredient.getUnit(),
                        row.getStockQuantity(), ingredient.getLowStockThreshold(), location, where);
                messagingTemplate.convertAndSend("/topic/inventory.alerts", event);
                eventPublisher.publishEvent(event);
            } else if (!low && row.isLowStockAlerted()) {
                row.setLowStockAlerted(false);
                stockRepository.save(row);
            }
        }
        reevaluateMenuItems(recipeRepository.menuItemsUsing(rows.stream().map(r -> r.getIngredient().getId()).toList()), location);
    }

    private void reevaluateEverywhere(Collection<UUID> menuItemIds) {
        Set<UUID> locations = new HashSet<>(stockRepository.trackedLocations());
        stockoutRepository.findAll().stream()
                .filter(stockout -> menuItemIds.contains(stockout.getMenuItemId()))
                .forEach(stockout -> locations.add(stockout.getBranchId()));
        for (UUID location : locations) {
            reevaluateMenuItems(menuItemIds, location);
        }
    }

    /**
     * Marks dishes sold out at a location when a tracked, active ingredient is too low to make one, and clears that
     * once they can be made again. A dish switched off by hand ({@code available = false}) is never touched.
     */
    private void reevaluateMenuItems(Collection<UUID> menuItemIds, UUID location) {
        if (menuItemIds.isEmpty()) {
            return;
        }
        Map<UUID, List<RecipeLineEntity>> recipes = recipeRepository.findByMenuItemIdIn(menuItemIds).stream()
                .collect(Collectors.groupingBy(RecipeLineEntity::getMenuItemId));
        Set<UUID> ingredientIds = recipes.values().stream().flatMap(List::stream)
                .map(line -> line.getIngredient().getId()).collect(Collectors.toSet());
        Map<UUID, BigDecimal> stockHere = stockRepository.findByBranchId(location).stream()
                .filter(row -> ingredientIds.contains(row.getIngredient().getId()))
                .collect(Collectors.toMap(row -> row.getIngredient().getId(), IngredientStockEntity::getStockQuantity));
        Map<UUID, MenuItemStockoutEntity> soldOut = stockoutRepository.findByMenuItemIdInAndBranchId(menuItemIds, location).stream()
                .collect(Collectors.toMap(MenuItemStockoutEntity::getMenuItemId, s -> s, (a, b) -> a));
        String where = location == null ? null : branchNames().get(location);
        boolean changed = false;
        for (MenuItemEntity item : menuItemRepository.findAllById(menuItemIds)) {
            boolean canMake = recipes.getOrDefault(item.getId(), List.of()).stream()
                    .filter(line -> line.getIngredient().isActive())
                    .filter(line -> stockHere.containsKey(line.getIngredient().getId()))
                    .allMatch(line -> stockHere.get(line.getIngredient().getId()).compareTo(line.getQuantity()) >= 0);
            MenuItemStockoutEntity existing = soldOut.get(item.getId());
            if (!canMake && existing == null) {
                MenuItemStockoutEntity stockout = new MenuItemStockoutEntity();
                stockout.setMenuItemId(item.getId());
                stockout.setBranchId(location);
                stockoutRepository.save(stockout);
                changed = true;
                messagingTemplate.convertAndSend("/topic/inventory.alerts", alert("ITEM_86", item, location, where));
            } else if (canMake && existing != null) {
                stockoutRepository.delete(existing);
                changed = true;
                messagingTemplate.convertAndSend("/topic/inventory.alerts", alert("ITEM_BACK", item, location, where));
            }
        }
        if (changed) {
            for (String cache : List.of(CacheConfig.MENU_CATEGORIES, CacheConfig.MENU_ITEMS_PUBLIC, CacheConfig.MENU_ITEM_PUBLIC_BY_ID)) {
                var c = cacheManager.getCache(cache);
                if (c != null) {
                    c.clear();
                }
            }
        }
    }

    private static Map<String, Object> alert(String event, MenuItemEntity item, UUID location, String where) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("menuItemId", item.getId());
        payload.put("name", item.getName());
        payload.put("branchId", location);
        payload.put("branchName", where);
        payload.put("at", Instant.now().toString());
        return payload;
    }

    private Map<UUID, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(BranchEntity::getId, BranchEntity::getName));
    }

    private IngredientView view(IngredientEntity ingredient, IngredientStockEntity row, UUID location) {
        String branchName = location == null ? null : branchNames().get(location);
        BigDecimal stock = row == null ? BigDecimal.ZERO : row.getStockQuantity();
        boolean low = row != null && ingredient.isActive() && row.isLow();
        return new IngredientView(ingredient.getId(), ingredient.getName(), ingredient.getUnit(), stock,
                ingredient.getLowStockThreshold(), ingredient.getCostPerUnit(), low, ingredient.isActive(),
                row == null ? ingredient.getUpdatedAt() : row.getUpdatedAt(), row != null, location, branchName,
                ingredient.getStockQuantity());
    }

    private IngredientView totalView(IngredientEntity ingredient, List<IngredientStockEntity> rows) {
        BigDecimal total = rows.stream().map(IngredientStockEntity::getStockQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean low = ingredient.isActive() && rows.stream().anyMatch(IngredientStockEntity::isLow);
        return new IngredientView(ingredient.getId(), ingredient.getName(), ingredient.getUnit(), total,
                ingredient.getLowStockThreshold(), ingredient.getCostPerUnit(), low, ingredient.isActive(),
                ingredient.getUpdatedAt(), !rows.isEmpty(), null, null, total);
    }

    /** Each dish's ingredient cost from its recipe at today's costs, against its price. */
    private List<DishCost> dishCosts() {
        Map<UUID, List<RecipeLineEntity>> recipes = recipeRepository.findAll().stream()
                .collect(Collectors.groupingBy(RecipeLineEntity::getMenuItemId));
        List<DishCost> dishes = new ArrayList<>();
        for (MenuItemEntity item : menuItemRepository.findAllById(recipes.keySet())) {
            if (!item.isActive()) continue;
            List<RecipeLineEntity> lines = recipes.get(item.getId());
            boolean missing = lines.stream().anyMatch(line -> line.getIngredient().getCostPerUnit() == null);
            BigDecimal cost = missing ? null : lines.stream()
                    .map(line -> line.getQuantity().multiply(line.getIngredient().getCostPerUnit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal margin = cost == null || item.getPrice().signum() == 0 ? null
                    : item.getPrice().subtract(cost).multiply(BigDecimal.valueOf(100)).divide(item.getPrice(), 1, RoundingMode.HALF_UP);
            dishes.add(new DishCost(item.getId(), item.getName(), item.getPrice(), cost, margin));
        }
        dishes.sort(Comparator.comparing(DishCost::name));
        return dishes;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** Totals one ingredient's movements in a period by kind, at the cost recorded on each movement. */
    private static final class FoodCostLineBuilder {
        final IngredientEntity ingredient;
        BigDecimal usedQty = BigDecimal.ZERO;
        BigDecimal usedCost = BigDecimal.ZERO;
        BigDecimal wasteQty = BigDecimal.ZERO;
        BigDecimal wasteCost = BigDecimal.ZERO;
        BigDecimal countQty = BigDecimal.ZERO;
        BigDecimal countCost = BigDecimal.ZERO;
        BigDecimal purchasedQty = BigDecimal.ZERO;
        BigDecimal purchasesCost = BigDecimal.ZERO;
        boolean costMissing;

        FoodCostLineBuilder(IngredientEntity ingredient) {
            this.ingredient = ingredient;
        }

        void add(StockMovementEntity movement) {
            BigDecimal qty = movement.getChangeQuantity();
            BigDecimal cost = movement.getUnitCost() == null ? BigDecimal.ZERO : qty.multiply(movement.getUnitCost());
            if (movement.getUnitCost() == null) costMissing = true;
            switch (movement.getReason()) {
                case ORDER, ORDER_CANCELLED -> {
                    usedQty = usedQty.subtract(qty);
                    usedCost = usedCost.subtract(cost);
                }
                case WASTE -> {
                    wasteQty = wasteQty.subtract(qty);
                    wasteCost = wasteCost.subtract(cost);
                }
                case ADJUSTMENT -> {
                    // Positive: more counted than recorded; negative: missing stock (a cost).
                    countQty = countQty.add(qty);
                    countCost = countCost.subtract(cost);
                }
                case RESTOCK -> {
                    purchasedQty = purchasedQty.add(qty);
                    purchasesCost = purchasesCost.add(cost);
                }
            }
        }

        FoodCostLine build() {
            return new FoodCostLine(ingredient.getId(), ingredient.getName(), ingredient.getUnit(),
                    usedQty, usedCost.setScale(2, RoundingMode.HALF_UP), wasteQty, wasteCost.setScale(2, RoundingMode.HALF_UP),
                    countQty, countCost.setScale(2, RoundingMode.HALF_UP), purchasedQty, purchasesCost.setScale(2, RoundingMode.HALF_UP),
                    costMissing);
        }
    }

    // ------------------------------------------------------------ DTOs

    public record LowStockEvent(UUID ingredientId, String name, String unit, BigDecimal stockQuantity, BigDecimal threshold,
                                UUID branchId, String branchName) {}

    public record IngredientRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                                    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String unit,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal lowStockThreshold,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal costPerUnit,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal initialStock,
                                    Boolean active) {}

    public record RecipeLineRequest(@jakarta.validation.constraints.NotNull UUID ingredientId,
                                    @jakarta.validation.constraints.NotNull BigDecimal quantity) {}

    /**
     * An ingredient's stock in a scope. {@code tracked} is false when the location has never received or counted it
     * (its orders then use none of it). {@code totalStock} is across every location.
     */
    public record IngredientView(UUID id, String name, String unit, BigDecimal stockQuantity, BigDecimal lowStockThreshold,
                                 BigDecimal costPerUnit, boolean low, boolean active, Instant updatedAt,
                                 boolean tracked, UUID branchId, String branchName, BigDecimal totalStock) {}

    public record RecipeLineView(UUID ingredientId, String ingredientName, String unit, BigDecimal quantity, BigDecimal costPerUnit) {
        static RecipeLineView from(RecipeLineEntity e) {
            return new RecipeLineView(e.getIngredient().getId(), e.getIngredient().getName(), e.getIngredient().getUnit(),
                    e.getQuantity(), e.getIngredient().getCostPerUnit());
        }

        static RecipeLineView from(ModifierRecipeLineEntity e) {
            return new RecipeLineView(e.getIngredient().getId(), e.getIngredient().getName(), e.getIngredient().getUnit(),
                    e.getQuantity(), e.getIngredient().getCostPerUnit());
        }
    }

    public record MovementView(UUID id, BigDecimal changeQuantity, BigDecimal balanceAfter, StockMovementEntity.Reason reason,
                               UUID orderId, String note, UUID actorUserId, Instant createdAt,
                               UUID branchId, String branchName, BigDecimal unitCost, String supplier) {
        static MovementView from(StockMovementEntity e, String branchName) {
            return new MovementView(e.getId(), e.getChangeQuantity(), e.getBalanceAfter(), e.getReason(), e.getOrderId(),
                    e.getNote(), e.getActorUserId(), e.getCreatedAt(), e.getBranchId(), branchName, e.getUnitCost(), e.getSupplier());
        }
    }

    public record FoodCostLine(UUID ingredientId, String name, String unit,
                               BigDecimal usedQuantity, BigDecimal usedCost,
                               BigDecimal wasteQuantity, BigDecimal wasteCost,
                               BigDecimal countDifference, BigDecimal countDifferenceCost,
                               BigDecimal purchasedQuantity, BigDecimal purchasesCost,
                               boolean costMissing) {}

    public record DishCost(UUID menuItemId, String name, BigDecimal price, BigDecimal recipeCost, BigDecimal marginPercent) {}

    /** {@code foodCostPercent} = ingredients used by orders / net sales; null without sales. */
    public record FoodCostReport(Instant from, Instant to, UUID branchId, BigDecimal netSales, BigDecimal usedCost,
                                 BigDecimal wasteCost, BigDecimal countDifferenceCost, BigDecimal purchasesCost,
                                 BigDecimal foodCostPercent, int ingredientsMissingCost,
                                 List<FoodCostLine> ingredients, List<DishCost> dishes) {}
}
