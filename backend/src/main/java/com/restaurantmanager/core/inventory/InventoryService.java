package com.restaurantmanager.core.inventory;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.CacheConfig;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import com.restaurantmanager.core.order.OrderEntity;
import com.restaurantmanager.core.order.OrderItemEntity;
import com.restaurantmanager.core.order.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ingredient stock: deducted when orders are placed, restored when unprepared orders are cancelled. Menu items whose
 * ingredients run out are automatically marked unavailable ("86'd") and re-enabled after a restock.
 */
@Service
public class InventoryService {
    private final IngredientRepository ingredientRepository;
    private final RecipeLineRepository recipeRepository;
    private final StockMovementRepository movementRepository;
    private final MenuItemRepository menuItemRepository;
    private final CacheManager cacheManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean blockWhenInsufficient;

    public InventoryService(IngredientRepository ingredientRepository,
                            RecipeLineRepository recipeRepository,
                            StockMovementRepository movementRepository,
                            MenuItemRepository menuItemRepository,
                            CacheManager cacheManager,
                            SimpMessagingTemplate messagingTemplate,
                            ApplicationEventPublisher eventPublisher,
                            @Value("${inventory.block-when-insufficient:true}") boolean blockWhenInsufficient) {
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.movementRepository = movementRepository;
        this.menuItemRepository = menuItemRepository;
        this.cacheManager = cacheManager;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.blockWhenInsufficient = blockWhenInsufficient;
    }

    // ------------------------------------------------------------ order hooks

    @Transactional
    public void deductForOrder(OrderEntity order, List<OrderItemEntity> items) {
        Map<UUID, Integer> quantityByMenuItem = new HashMap<>();
        Map<UUID, String> nameByMenuItem = new HashMap<>();
        for (OrderItemEntity item : items) {
            quantityByMenuItem.merge(item.getMenuItem().getId(), item.getQuantity(), Integer::sum);
            nameByMenuItem.put(item.getMenuItem().getId(), item.getMenuItem().getName());
        }
        List<RecipeLineEntity> recipe = recipeRepository.findByMenuItemIdIn(quantityByMenuItem.keySet());
        if (recipe.isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> required = new LinkedHashMap<>();
        Map<UUID, String> neededFor = new HashMap<>();
        for (RecipeLineEntity line : recipe) {
            BigDecimal need = line.getQuantity().multiply(BigDecimal.valueOf(quantityByMenuItem.get(line.getMenuItemId())));
            required.merge(line.getIngredient().getId(), need, BigDecimal::add);
            neededFor.putIfAbsent(line.getIngredient().getId(), nameByMenuItem.get(line.getMenuItemId()));
        }
        Map<UUID, IngredientEntity> locked = ingredientRepository.lockAll(required.keySet()).stream()
                .collect(Collectors.toMap(IngredientEntity::getId, Function.identity()));
        if (blockWhenInsufficient) {
            for (Map.Entry<UUID, BigDecimal> need : required.entrySet()) {
                IngredientEntity ingredient = locked.get(need.getKey());
                if (ingredient.getStockQuantity().compareTo(need.getValue()) < 0) {
                    throw new ApiException(409, neededFor.get(need.getKey()) + " is out of stock (not enough " + ingredient.getName() + ")");
                }
            }
        }
        for (Map.Entry<UUID, BigDecimal> need : required.entrySet()) {
            move(locked.get(need.getKey()), need.getValue().negate(), StockMovementEntity.Reason.ORDER, order.getId(), null, null);
        }
        order.setStockDeducted(true);
        afterStockChange(locked.values());
    }

    /** Puts ingredients back when an order is cancelled before the kitchen started preparing it. */
    @Transactional
    public void restoreForOrder(OrderEntity order, OrderStatus statusBeforeCancel) {
        if (!order.isStockDeducted()) {
            return;
        }
        if (statusBeforeCancel != OrderStatus.PENDING && statusBeforeCancel != OrderStatus.CONFIRMED) {
            return; // food already prepared: ingredients were consumed
        }
        Map<UUID, BigDecimal> toRestore = new HashMap<>();
        for (StockMovementEntity movement : movementRepository.findByOrderId(order.getId())) {
            toRestore.merge(movement.getIngredientId(), movement.getChangeQuantity().negate(), BigDecimal::add);
        }
        toRestore.values().removeIf(quantity -> quantity.signum() <= 0);
        if (toRestore.isEmpty()) {
            order.setStockDeducted(false);
            return;
        }
        List<IngredientEntity> locked = ingredientRepository.lockAll(toRestore.keySet());
        for (IngredientEntity ingredient : locked) {
            move(ingredient, toRestore.get(ingredient.getId()), StockMovementEntity.Reason.ORDER_CANCELLED, order.getId(), null, null);
        }
        order.setStockDeducted(false);
        afterStockChange(locked);
    }

    // ------------------------------------------------------------ stock management

    @Transactional
    public IngredientView restock(UUID ingredientId, BigDecimal quantity, String note, UUID actorId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(400, "Restock quantity must be positive");
        }
        IngredientEntity ingredient = lockOne(ingredientId);
        move(ingredient, quantity, StockMovementEntity.Reason.RESTOCK, null, note, actorId);
        afterStockChange(List.of(ingredient));
        return IngredientView.from(ingredient);
    }

    /** Records waste or a stock-count correction; {@code countedQuantity} sets the absolute level. */
    @Transactional
    public IngredientView adjust(UUID ingredientId, StockMovementEntity.Reason reason, BigDecimal delta,
                                 BigDecimal countedQuantity, String note, UUID actorId) {
        if (reason != StockMovementEntity.Reason.WASTE && reason != StockMovementEntity.Reason.ADJUSTMENT) {
            throw new ApiException(400, "Reason must be WASTE or ADJUSTMENT");
        }
        IngredientEntity ingredient = lockOne(ingredientId);
        BigDecimal change = countedQuantity != null ? countedQuantity.subtract(ingredient.getStockQuantity()) : delta;
        if (change == null || change.signum() == 0) {
            throw new ApiException(400, "Nothing to adjust");
        }
        if (reason == StockMovementEntity.Reason.WASTE && change.signum() > 0) {
            change = change.negate();
        }
        if (ingredient.getStockQuantity().add(change).signum() < 0) {
            throw new ApiException(400, "Stock cannot go below zero");
        }
        move(ingredient, change, reason, null, note, actorId);
        afterStockChange(List.of(ingredient));
        return IngredientView.from(ingredient);
    }

    @Transactional
    public IngredientView saveIngredient(UUID id, IngredientRequest request) {
        IngredientEntity ingredient = id == null ? new IngredientEntity()
                : ingredientRepository.findById(id).orElseThrow(() -> new ApiException(404, "Ingredient not found"));
        String name = request.name().trim();
        if ((id == null || !name.equalsIgnoreCase(ingredient.getName())) && ingredientRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(409, "An ingredient with this name already exists");
        }
        ingredient.setName(name);
        ingredient.setUnit(request.unit().trim());
        ingredient.setLowStockThreshold(request.lowStockThreshold() == null ? BigDecimal.ZERO : request.lowStockThreshold());
        ingredient.setCostPerUnit(request.costPerUnit());
        if (request.active() != null) {
            ingredient.setActive(request.active());
        }
        boolean created = id == null;
        if (created) {
            ingredient.setStockQuantity(BigDecimal.ZERO);
        }
        IngredientEntity saved = ingredientRepository.save(ingredient);
        if (created && request.initialStock() != null && request.initialStock().signum() > 0) {
            move(saved, request.initialStock(), StockMovementEntity.Reason.RESTOCK, null, "Opening stock", null);
        }
        afterStockChange(List.of(saved));
        return IngredientView.from(saved);
    }

    @Transactional
    public List<RecipeLineView> setRecipe(UUID menuItemId, List<RecipeLineRequest> lines) {
        menuItemRepository.findById(menuItemId).orElseThrow(() -> new ApiException(404, "Menu item not found"));
        recipeRepository.deleteAll(recipeRepository.findByMenuItemId(menuItemId));
        recipeRepository.flush();
        Set<UUID> seen = new HashSet<>();
        List<RecipeLineEntity> rows = new ArrayList<>();
        for (RecipeLineRequest line : lines) {
            if (!seen.add(line.ingredientId())) {
                throw new ApiException(400, "Each ingredient may appear once in a recipe");
            }
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new ApiException(400, "Recipe quantities must be positive");
            }
            RecipeLineEntity row = new RecipeLineEntity();
            row.setMenuItemId(menuItemId);
            row.setIngredient(ingredientRepository.findById(line.ingredientId())
                    .orElseThrow(() -> new ApiException(404, "Ingredient not found")));
            row.setQuantity(line.quantity());
            rows.add(row);
        }
        List<RecipeLineEntity> saved = recipeRepository.saveAll(rows);
        reevaluateMenuItems(List.of(menuItemId));
        return saved.stream().map(RecipeLineView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RecipeLineView> recipe(UUID menuItemId) {
        return recipeRepository.findByMenuItemId(menuItemId).stream().map(RecipeLineView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<IngredientView> ingredients() {
        return ingredientRepository.findAllByOrderByNameAsc().stream().map(IngredientView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<IngredientView> lowStock() {
        return ingredientRepository.findAllByOrderByNameAsc().stream()
                .filter(IngredientEntity::isActive)
                .filter(IngredientEntity::isLow)
                .map(IngredientView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID ingredientId) {
        return movementRepository.findByIngredientIdOrderByCreatedAtDesc(ingredientId).stream().map(MovementView::from).toList();
    }

    // ------------------------------------------------------------ internals

    private IngredientEntity lockOne(UUID ingredientId) {
        List<IngredientEntity> locked = ingredientRepository.lockAll(List.of(ingredientId));
        if (locked.isEmpty()) {
            throw new ApiException(404, "Ingredient not found");
        }
        return locked.get(0);
    }

    private void move(IngredientEntity ingredient, BigDecimal change, StockMovementEntity.Reason reason,
                      UUID orderId, String note, UUID actorId) {
        ingredient.setStockQuantity(ingredient.getStockQuantity().add(change));
        ingredientRepository.save(ingredient);
        StockMovementEntity movement = new StockMovementEntity();
        movement.setIngredientId(ingredient.getId());
        movement.setChangeQuantity(change);
        movement.setBalanceAfter(ingredient.getStockQuantity());
        movement.setReason(reason);
        movement.setOrderId(orderId);
        movement.setNote(note == null || note.isBlank() ? null : note.trim());
        movement.setActorUserId(actorId);
        movementRepository.save(movement);
    }

    private void afterStockChange(Collection<IngredientEntity> ingredients) {
        for (IngredientEntity ingredient : ingredients) {
            boolean low = ingredient.isActive() && ingredient.isLow();
            if (low && !ingredient.isLowStockAlerted()) {
                ingredient.setLowStockAlerted(true);
                ingredientRepository.save(ingredient);
                LowStockEvent event = new LowStockEvent(ingredient.getId(), ingredient.getName(), ingredient.getUnit(),
                        ingredient.getStockQuantity(), ingredient.getLowStockThreshold());
                messagingTemplate.convertAndSend("/topic/inventory.alerts", event);
                eventPublisher.publishEvent(event);
            } else if (!low && ingredient.isLowStockAlerted()) {
                ingredient.setLowStockAlerted(false);
                ingredientRepository.save(ingredient);
            }
        }
        reevaluateMenuItems(recipeRepository.menuItemsUsing(ingredients.stream().map(IngredientEntity::getId).toList()));
    }

    /** 86 items that can no longer be made; bring back items that were auto-86'd once they can be made again. */
    private void reevaluateMenuItems(Collection<UUID> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return;
        }
        Map<UUID, List<RecipeLineEntity>> recipes = recipeRepository.findByMenuItemIdIn(menuItemIds).stream()
                .collect(Collectors.groupingBy(RecipeLineEntity::getMenuItemId));
        boolean changed = false;
        for (MenuItemEntity item : menuItemRepository.findAllById(menuItemIds)) {
            List<RecipeLineEntity> lines = recipes.getOrDefault(item.getId(), List.of());
            boolean canMake = lines.stream().allMatch(line ->
                    line.getIngredient().getStockQuantity().compareTo(line.getQuantity()) >= 0);
            if (!canMake && item.isAvailable()) {
                item.setAvailable(false);
                item.setAuto86(true);
                menuItemRepository.save(item);
                changed = true;
                messagingTemplate.convertAndSend("/topic/inventory.alerts",
                        Map.of("event", "ITEM_86", "menuItemId", item.getId(), "name", item.getName(), "at", Instant.now().toString()));
            } else if (canMake && item.isAuto86()) {
                item.setAvailable(true);
                item.setAuto86(false);
                menuItemRepository.save(item);
                changed = true;
                messagingTemplate.convertAndSend("/topic/inventory.alerts",
                        Map.of("event", "ITEM_BACK", "menuItemId", item.getId(), "name", item.getName(), "at", Instant.now().toString()));
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

    // ------------------------------------------------------------ DTOs

    public record LowStockEvent(UUID ingredientId, String name, String unit, BigDecimal stockQuantity, BigDecimal threshold) {}

    public record IngredientRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
                                    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String unit,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal lowStockThreshold,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal costPerUnit,
                                    @jakarta.validation.constraints.DecimalMin("0") BigDecimal initialStock,
                                    Boolean active) {}

    public record RecipeLineRequest(@jakarta.validation.constraints.NotNull UUID ingredientId,
                                    @jakarta.validation.constraints.NotNull BigDecimal quantity) {}

    public record IngredientView(UUID id, String name, String unit, BigDecimal stockQuantity, BigDecimal lowStockThreshold,
                                 BigDecimal costPerUnit, boolean low, boolean active, Instant updatedAt) {
        static IngredientView from(IngredientEntity e) {
            return new IngredientView(e.getId(), e.getName(), e.getUnit(), e.getStockQuantity(), e.getLowStockThreshold(),
                    e.getCostPerUnit(), e.isActive() && e.isLow(), e.isActive(), e.getUpdatedAt());
        }
    }

    public record RecipeLineView(UUID ingredientId, String ingredientName, String unit, BigDecimal quantity) {
        static RecipeLineView from(RecipeLineEntity e) {
            return new RecipeLineView(e.getIngredient().getId(), e.getIngredient().getName(), e.getIngredient().getUnit(), e.getQuantity());
        }
    }

    public record MovementView(UUID id, BigDecimal changeQuantity, BigDecimal balanceAfter, StockMovementEntity.Reason reason,
                               UUID orderId, String note, UUID actorUserId, Instant createdAt) {
        static MovementView from(StockMovementEntity e) {
            return new MovementView(e.getId(), e.getChangeQuantity(), e.getBalanceAfter(), e.getReason(), e.getOrderId(),
                    e.getNote(), e.getActorUserId(), e.getCreatedAt());
        }
    }
}
