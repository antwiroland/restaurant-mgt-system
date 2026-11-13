package com.restaurantmanager.core.promotion;

import com.restaurantmanager.core.branch.BranchRepository;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.menu.MenuItemEntity;
import com.restaurantmanager.core.menu.MenuItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Menu offers: managed by admins and managers, applied automatically at order time, and listed publicly so the
 * menu can advertise them.
 */
@RestController
@RequestMapping("/promotions/offers")
public class MenuOfferController {
    private final MenuOfferRepository offerRepository;
    private final MenuItemRepository menuItemRepository;
    private final BranchRepository branchRepository;
    private final ZoneId zone;

    public MenuOfferController(MenuOfferRepository offerRepository, MenuItemRepository menuItemRepository,
                               BranchRepository branchRepository,
                               @Value("${app.timezone:Africa/Accra}") String timeZone) {
        this.offerRepository = offerRepository;
        this.menuItemRepository = menuItemRepository;
        this.branchRepository = branchRepository;
        this.zone = ZoneId.of(timeZone);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CASHIER')")
    @Transactional(readOnly = true)
    public List<OfferResponse> list() {
        List<MenuOfferEntity> offers = offerRepository.findAllWithMenuItem();
        Map<UUID, String> names = dishNames(offers);
        return offers.stream().map(offer -> toResponse(offer, names)).toList();
    }

    /**
     * Offers customers can see: active and not past their end date (including ones outside their hours right now,
     * with {@code runningNow} false). With {@code branchId}, only offers for that branch or every branch.
     */
    @GetMapping("/public")
    @Transactional(readOnly = true)
    public List<PublicOfferResponse> listPublic(@RequestParam(required = false) UUID branchId) {
        Instant now = Instant.now();
        List<MenuOfferEntity> offers = offerRepository.findActiveForPricing().stream()
                .filter(offer -> offer.isAdvertisableAt(now))
                .filter(offer -> branchId == null || offer.appliesToBranch(branchId))
                .toList();
        Map<UUID, String> names = dishNames(offers);
        return offers.stream()
                .map(offer -> new PublicOfferResponse(
                        offer.getId(),
                        offer.getName(),
                        offer.getDescription(),
                        offer.getOfferType(),
                        summary(offer, names),
                        schedule(offer),
                        offer.isRunningAt(now, zone),
                        offer.menuItemIds(),
                        offer.getBranch() == null ? null : offer.getBranch().getId(),
                        offer.getBranch() == null ? null : offer.getBranch().getName(),
                        offer.getStartsAt(),
                        offer.getEndsAt()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse create(@Valid @RequestBody OfferUpsertRequest request) {
        MenuOfferEntity offer = new MenuOfferEntity();
        apply(offer, request);
        MenuOfferEntity saved = offerRepository.save(offer);
        return toResponse(saved, dishNames(List.of(saved)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse update(@PathVariable UUID id, @Valid @RequestBody OfferUpsertRequest request) {
        MenuOfferEntity offer = require(id);
        apply(offer, request);
        MenuOfferEntity saved = offerRepository.save(offer);
        return toResponse(saved, dishNames(List.of(saved)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public OfferResponse setStatus(@PathVariable UUID id, @RequestBody OfferStatusRequest request) {
        MenuOfferEntity offer = require(id);
        offer.setActive(request.active());
        MenuOfferEntity saved = offerRepository.save(offer);
        return toResponse(saved, dishNames(List.of(saved)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public void delete(@PathVariable UUID id) {
        offerRepository.delete(require(id));
    }

    private MenuOfferEntity require(UUID id) {
        return offerRepository.findById(id).orElseThrow(() -> new ApiException(404, "Offer not found"));
    }

    private void apply(MenuOfferEntity offer, OfferUpsertRequest request) {
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(400, "endsAt must be after startsAt");
        }
        if ((request.dailyStartTime() == null) != (request.dailyEndTime() == null)) {
            throw new ApiException(400, "Give both a daily start and end time, or neither");
        }
        if (request.dailyStartTime() != null && request.dailyStartTime().equals(request.dailyEndTime())) {
            throw new ApiException(400, "The daily start and end time must differ");
        }
        OfferType type = request.offerType() == null ? OfferType.BUY_X_GET_Y : request.offerType();

        offer.setName(request.name().trim());
        offer.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        offer.setOfferType(type);
        offer.setMenuItem(null);
        offer.setFreeMenuItem(null);
        offer.setBuyQuantity(null);
        offer.setFreeQuantity(null);
        offer.setPercentOff(null);
        offer.setComboPrice(null);
        offer.setComboItems(List.of());

        switch (type) {
            case BUY_X_GET_Y -> {
                offer.setMenuItem(dish(request.menuItemId(), "menuItemId"));
                offer.setBuyQuantity(atLeastOne(request.buyQuantity(), "buyQuantity"));
                offer.setFreeQuantity(atLeastOne(request.freeQuantity(), "freeQuantity"));
            }
            case BUY_X_GET_OTHER -> {
                offer.setMenuItem(dish(request.menuItemId(), "menuItemId"));
                offer.setFreeMenuItem(dish(request.freeMenuItemId(), "freeMenuItemId"));
                if (offer.getMenuItem().getId().equals(offer.getFreeMenuItem().getId())) {
                    throw new ApiException(400, "For the same dish free, use a buy X get Y offer");
                }
                offer.setBuyQuantity(atLeastOne(request.buyQuantity(), "buyQuantity"));
                offer.setFreeQuantity(atLeastOne(request.freeQuantity(), "freeQuantity"));
            }
            case PERCENT_OFF -> {
                offer.setMenuItem(dish(request.menuItemId(), "menuItemId"));
                BigDecimal percent = request.percentOff();
                if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new ApiException(400, "percentOff must be more than 0 and at most 100");
                }
                offer.setPercentOff(percent.setScale(2, RoundingMode.HALF_UP));
            }
            case COMBO -> {
                List<ComboItemRequest> items = request.comboItems() == null ? List.of() : request.comboItems();
                if (items.size() < 2) {
                    throw new ApiException(400, "A combo needs at least two different dishes");
                }
                Set<UUID> seen = new HashSet<>();
                List<MenuOfferEntity.ComboItem> comboItems = new ArrayList<>();
                for (ComboItemRequest item : items) {
                    dish(item.menuItemId(), "comboItems.menuItemId");
                    if (!seen.add(item.menuItemId())) {
                        throw new ApiException(400, "Each dish can appear once in a combo; use its quantity");
                    }
                    comboItems.add(new MenuOfferEntity.ComboItem(item.menuItemId(), atLeastOne(item.quantity(), "comboItems.quantity")));
                }
                if (request.comboPrice() == null || request.comboPrice().signum() < 0) {
                    throw new ApiException(400, "comboPrice is required");
                }
                offer.setComboItems(comboItems);
                offer.setComboPrice(request.comboPrice().setScale(2, RoundingMode.HALF_UP));
            }
        }

        offer.setStartsAt(request.startsAt());
        offer.setEndsAt(request.endsAt());
        offer.setDaysOfWeek(normalizeDays(request.daysOfWeek()));
        offer.setDailyStartTime(request.dailyStartTime());
        offer.setDailyEndTime(request.dailyEndTime());
        offer.setBranch(request.branchId() == null ? null : branchRepository.findById(request.branchId())
                .orElseThrow(() -> new ApiException(404, "Branch not found")));
        offer.setActive(request.active() == null || request.active());
    }

    private MenuItemEntity dish(UUID id, String field) {
        if (id == null) {
            throw new ApiException(400, field + " is required for this kind of offer");
        }
        return menuItemRepository.findById(id).orElseThrow(() -> new ApiException(404, "Menu item not found"));
    }

    private static int atLeastOne(Integer value, String field) {
        if (value == null || value < 1) {
            throw new ApiException(400, field + " must be at least 1");
        }
        return value;
    }

    private static String normalizeDays(List<String> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String day : days) {
            String code = day == null ? "" : day.trim().toUpperCase(Locale.ROOT);
            DayOfWeek match = null;
            for (DayOfWeek candidate : DayOfWeek.values()) {
                if (code.length() >= 3 && candidate.name().startsWith(code.substring(0, 3))) {
                    match = candidate;
                }
            }
            if (match == null) {
                throw new ApiException(400, "Unknown day: " + day + " (use MON..SUN)");
            }
            String short3 = match.name().substring(0, 3);
            if (!codes.contains(short3)) {
                codes.add(short3);
            }
        }
        return codes.size() == 7 ? null : String.join(",", codes);
    }

    private Map<UUID, String> dishNames(List<MenuOfferEntity> offers) {
        Set<UUID> ids = new HashSet<>();
        offers.forEach(offer -> ids.addAll(offer.menuItemIds()));
        Map<UUID, String> names = new HashMap<>();
        menuItemRepository.findAllById(ids).forEach(dish -> names.put(dish.getId(), dish.getName()));
        return names;
    }

    /** Plain-language description of the deal, e.g. "Buy 2 Kelewele, get 1 free". */
    static String summary(MenuOfferEntity offer, Map<UUID, String> names) {
        String dish = offer.getMenuItem() == null ? "" : names.getOrDefault(offer.getMenuItem().getId(), offer.getMenuItem().getName());
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y -> "Buy " + offer.getBuyQuantity() + " " + dish + ", get " + offer.getFreeQuantity() + " free";
            case BUY_X_GET_OTHER -> "Buy " + offer.getBuyQuantity() + " " + dish + ", get " + offer.getFreeQuantity() + " "
                    + names.getOrDefault(offer.getFreeMenuItem().getId(), offer.getFreeMenuItem().getName()) + " free";
            case PERCENT_OFF -> offer.getPercentOff().stripTrailingZeros().toPlainString() + "% off " + dish;
            case COMBO -> offer.getComboItems().stream()
                    .map(item -> (item.getQuantity() > 1 ? item.getQuantity() + " " : "") + names.getOrDefault(item.getMenuItemId(), "item"))
                    .collect(Collectors.joining(" + ")) + " for " + offer.getComboPrice().toPlainString();
        };
    }

    /** When it runs, e.g. "Mon, Tue, Fri · 16:00–18:00", or null when it runs all day every day. */
    static String schedule(MenuOfferEntity offer) {
        List<String> parts = new ArrayList<>();
        Set<DayOfWeek> days = offer.days();
        if (!days.isEmpty()) {
            parts.add(days.stream().sorted()
                    .map(day -> day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .collect(Collectors.joining(", ")));
        }
        if (offer.getDailyStartTime() != null && offer.getDailyEndTime() != null) {
            parts.add(time(offer.getDailyStartTime()) + "–" + time(offer.getDailyEndTime()));
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String time(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private OfferResponse toResponse(MenuOfferEntity e, Map<UUID, String> names) {
        return new OfferResponse(
                e.getId(), e.getName(), e.getDescription(), e.getOfferType(),
                e.getMenuItem() == null ? null : e.getMenuItem().getId(),
                e.getMenuItem() == null ? null : e.getMenuItem().getName(),
                e.getBuyQuantity(), e.getFreeQuantity(),
                e.getFreeMenuItem() == null ? null : e.getFreeMenuItem().getId(),
                e.getFreeMenuItem() == null ? null : e.getFreeMenuItem().getName(),
                e.getPercentOff(), e.getComboPrice(),
                e.getComboItems().stream()
                        .map(item -> new ComboItemResponse(item.getMenuItemId(), names.getOrDefault(item.getMenuItemId(), "item"), item.getQuantity()))
                        .toList(),
                e.days().stream().sorted().map(day -> day.name().substring(0, 3)).toList(),
                e.getDailyStartTime(), e.getDailyEndTime(),
                e.getBranch() == null ? null : e.getBranch().getId(),
                e.getBranch() == null ? null : e.getBranch().getName(),
                e.getStartsAt(), e.getEndsAt(), e.isActive(), e.isRunningAt(Instant.now(), zone),
                summary(e, names), schedule(e), e.getCreatedAt());
    }

    public record ComboItemRequest(@NotNull UUID menuItemId, Integer quantity) {}

    public record OfferUpsertRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            /** Defaults to BUY_X_GET_Y. */
            OfferType offerType,
            UUID menuItemId,
            @Min(1) Integer buyQuantity,
            @Min(1) Integer freeQuantity,
            UUID freeMenuItemId,
            BigDecimal percentOff,
            BigDecimal comboPrice,
            List<@Valid ComboItemRequest> comboItems,
            Instant startsAt,
            Instant endsAt,
            /** MON..SUN; empty = every day. */
            List<String> daysOfWeek,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            /** Null = every branch. */
            UUID branchId,
            Boolean active
    ) {}

    public record OfferStatusRequest(boolean active) {}

    public record ComboItemResponse(UUID menuItemId, String menuItemName, int quantity) {}

    public record OfferResponse(UUID id, String name, String description, OfferType offerType,
                                UUID menuItemId, String menuItemName, int buyQuantity, int freeQuantity,
                                UUID freeMenuItemId, String freeMenuItemName, BigDecimal percentOff, BigDecimal comboPrice,
                                List<ComboItemResponse> comboItems, List<String> daysOfWeek,
                                LocalTime dailyStartTime, LocalTime dailyEndTime, UUID branchId, String branchName,
                                Instant startsAt, Instant endsAt, boolean active, boolean running,
                                String summary, String schedule, Instant createdAt) {}

    public record PublicOfferResponse(UUID id, String name, String description, OfferType offerType, String summary,
                                      String schedule, boolean runningNow, List<UUID> menuItemIds,
                                      UUID branchId, String branchName, Instant startsAt, Instant endsAt) {}
}
