package com.restaurantmanager.core.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Works out offer discounts for a cart, and "add one more" suggestions.
 *
 * <p>Each unit in the cart can be discounted by one offer only. Offers are applied best first: the offer worth the
 * most against the units still available is applied, its units are used up, and the rest are re-evaluated. Free
 * units are always the cheapest ones of a dish (so modifiers cannot make the free unit an expensive one).
 *
 * <p>Callers pass only offers that are running now and apply to the order's branch.
 */
public final class OfferEngine {

    private OfferEngine() {
    }

    /** A cart line: a dish, its unit price (including modifiers) and quantity. */
    public record CartLine(UUID menuItemId, BigDecimal unitPrice, int quantity) {
    }

    /** An offer that reduced the bill. */
    public record AppliedOffer(UUID offerId, String name, BigDecimal discount) {
    }

    /**
     * A nudge to complete a deal, e.g. "Add 1 more Kelewele: it's free". {@code menuItemId} is the dish to add.
     */
    public record Hint(UUID offerId, String offerName, UUID menuItemId, int quantity, String message) {
    }

    public record Result(BigDecimal discount, List<AppliedOffer> applied, List<Hint> hints) {
    }

    private record Evaluation(BigDecimal discount, Map<UUID, Integer> cheapestUsed) {
        static final Evaluation NONE = new Evaluation(BigDecimal.ZERO, Map.of());
    }

    public static Result evaluate(List<CartLine> lines, List<MenuOfferEntity> offers, Function<UUID, String> dishName) {
        Map<UUID, List<BigDecimal>> pool = new HashMap<>();
        for (CartLine line : lines) {
            List<BigDecimal> units = pool.computeIfAbsent(line.menuItemId(), id -> new ArrayList<>());
            for (int i = 0; i < line.quantity(); i++) {
                units.add(line.unitPrice());
            }
        }
        pool.values().forEach(units -> units.sort(Comparator.naturalOrder()));
        List<Hint> hints = hints(pool, offers, dishName);

        List<MenuOfferEntity> remaining = new ArrayList<>(offers);
        List<AppliedOffer> applied = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        while (!remaining.isEmpty()) {
            MenuOfferEntity best = null;
            Evaluation bestEval = Evaluation.NONE;
            for (MenuOfferEntity offer : remaining) {
                Evaluation eval = evaluate(offer, pool);
                if (eval.discount().compareTo(bestEval.discount()) > 0) {
                    best = offer;
                    bestEval = eval;
                }
            }
            if (best == null) {
                break;
            }
            bestEval.cheapestUsed().forEach((menuItemId, count) -> {
                List<BigDecimal> units = pool.get(menuItemId);
                units.subList(0, Math.min(count, units.size())).clear();
            });
            remaining.remove(best);
            BigDecimal discount = bestEval.discount().setScale(2, RoundingMode.HALF_UP);
            applied.add(new AppliedOffer(best.getId(), best.getName(), discount));
            total = total.add(discount);
        }
        return new Result(total.setScale(2, RoundingMode.HALF_UP), applied, hints);
    }

    /** "Buy X get Y" on n units: every full group of X+Y has Y free, and units beyond X in a partial group are free. */
    public static int freeItems(int purchased, int buyQty, int freeQty) {
        if (buyQty <= 0 || freeQty <= 0) {
            return 0;
        }
        int groupSize = buyQty + freeQty;
        int fullGroups = purchased / groupSize;
        int remainder = purchased % groupSize;
        return fullGroups * freeQty + Math.max(0, remainder - buyQty);
    }

    private static Evaluation evaluate(MenuOfferEntity offer, Map<UUID, List<BigDecimal>> pool) {
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y -> buyXGetY(offer, pool);
            case BUY_X_GET_OTHER -> buyXGetOther(offer, pool);
            case PERCENT_OFF -> percentOff(offer, pool);
            case COMBO -> combo(offer, pool);
        };
    }

    private static Evaluation buyXGetY(MenuOfferEntity offer, Map<UUID, List<BigDecimal>> pool) {
        if (offer.getMenuItem() == null) return Evaluation.NONE;
        UUID dish = offer.getMenuItem().getId();
        List<BigDecimal> units = units(pool, dish);
        int buy = offer.getBuyQuantity();
        int free = freeItems(units.size(), buy, offer.getFreeQuantity());
        if (free == 0) return Evaluation.NONE;
        int group = buy + offer.getFreeQuantity();
        int remainder = units.size() % group;
        int used = (units.size() / group) * group + (remainder > buy ? remainder : 0);
        return new Evaluation(sumCheapest(units, free), Map.of(dish, used));
    }

    private static Evaluation buyXGetOther(MenuOfferEntity offer, Map<UUID, List<BigDecimal>> pool) {
        if (offer.getMenuItem() == null || offer.getFreeMenuItem() == null) return Evaluation.NONE;
        UUID bought = offer.getMenuItem().getId();
        UUID given = offer.getFreeMenuItem().getId();
        if (bought.equals(given)) return buyXGetY(offer, pool);
        int buy = offer.getBuyQuantity();
        int freePerSet = offer.getFreeQuantity();
        if (buy <= 0 || freePerSet <= 0) return Evaluation.NONE;
        int sets = units(pool, bought).size() / buy;
        List<BigDecimal> givenUnits = units(pool, given);
        int free = Math.min(sets * freePerSet, givenUnits.size());
        if (free == 0) return Evaluation.NONE;
        int setsUsed = (free + freePerSet - 1) / freePerSet;
        Map<UUID, Integer> used = new HashMap<>();
        used.put(bought, setsUsed * buy);
        used.put(given, free);
        return new Evaluation(sumCheapest(givenUnits, free), used);
    }

    private static Evaluation percentOff(MenuOfferEntity offer, Map<UUID, List<BigDecimal>> pool) {
        if (offer.getMenuItem() == null || offer.getPercentOff() == null) return Evaluation.NONE;
        UUID dish = offer.getMenuItem().getId();
        List<BigDecimal> units = units(pool, dish);
        if (units.isEmpty()) return Evaluation.NONE;
        BigDecimal value = sumCheapest(units, units.size())
                .multiply(offer.getPercentOff())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Evaluation(value, Map.of(dish, units.size()));
    }

    private static Evaluation combo(MenuOfferEntity offer, Map<UUID, List<BigDecimal>> pool) {
        List<MenuOfferEntity.ComboItem> items = offer.getComboItems();
        if (items.isEmpty() || offer.getComboPrice() == null) return Evaluation.NONE;
        int sets = Integer.MAX_VALUE;
        for (MenuOfferEntity.ComboItem item : items) {
            sets = Math.min(sets, units(pool, item.getMenuItemId()).size() / item.getQuantity());
        }
        if (sets == 0) return Evaluation.NONE;
        // Price each set from the cheapest units still available, so the saving is never overstated.
        BigDecimal saving = BigDecimal.ZERO;
        Map<UUID, Integer> used = new HashMap<>();
        for (int set = 0; set < sets; set++) {
            BigDecimal setValue = BigDecimal.ZERO;
            for (MenuOfferEntity.ComboItem item : items) {
                List<BigDecimal> units = units(pool, item.getMenuItemId());
                int from = used.getOrDefault(item.getMenuItemId(), 0);
                for (int i = from; i < from + item.getQuantity(); i++) {
                    setValue = setValue.add(units.get(i));
                }
            }
            BigDecimal setSaving = setValue.subtract(offer.getComboPrice());
            if (setSaving.signum() <= 0) {
                break;
            }
            saving = saving.add(setSaving);
            for (MenuOfferEntity.ComboItem item : items) {
                used.merge(item.getMenuItemId(), item.getQuantity(), Integer::sum);
            }
        }
        return saving.signum() > 0 ? new Evaluation(saving, used) : Evaluation.NONE;
    }

    /** Suggestions against the whole cart: one more unit (or a missing dish) that would unlock or claim a deal. */
    private static List<Hint> hints(Map<UUID, List<BigDecimal>> pool, List<MenuOfferEntity> offers, Function<UUID, String> dishName) {
        Map<String, Hint> hints = new LinkedHashMap<>();
        for (MenuOfferEntity offer : offers) {
            switch (offer.getOfferType()) {
                case BUY_X_GET_Y -> {
                    if (offer.getMenuItem() == null) break;
                    UUID dish = offer.getMenuItem().getId();
                    int n = units(pool, dish).size();
                    int buy = offer.getBuyQuantity();
                    int group = buy + offer.getFreeQuantity();
                    if (n == 0 || buy <= 0 || group <= buy) break;
                    int remainder = n % group;
                    String name = dishName.apply(dish);
                    if (remainder >= buy) {
                        int claimable = group - remainder;
                        add(hints, new Hint(offer.getId(), offer.getName(), dish, claimable,
                                "Add " + claimable + " more " + name + ": " + (claimable == 1 ? "it's" : "they're") + " free"));
                    } else if (buy > 1 && remainder == buy - 1) {
                        // One short of a deal. (With buy 1 this would nag on every cart, so not then.)
                        add(hints, new Hint(offer.getId(), offer.getName(), dish, 1,
                                "Add 1 more " + name + " and get " + offer.getFreeQuantity() + " free"));
                    }
                }
                case BUY_X_GET_OTHER -> {
                    if (offer.getMenuItem() == null || offer.getFreeMenuItem() == null) break;
                    UUID bought = offer.getMenuItem().getId();
                    UUID given = offer.getFreeMenuItem().getId();
                    int buy = offer.getBuyQuantity();
                    if (buy <= 0 || bought.equals(given)) break;
                    int nBought = units(pool, bought).size();
                    int nGiven = units(pool, given).size();
                    int entitled = (nBought / buy) * offer.getFreeQuantity();
                    if (entitled > nGiven) {
                        int claimable = entitled - nGiven;
                        add(hints, new Hint(offer.getId(), offer.getName(), given, claimable,
                                "Add " + claimable + " " + dishName.apply(given) + ": free with your " + dishName.apply(bought)));
                    } else if (buy > 1 && nBought % buy == buy - 1) {
                        add(hints, new Hint(offer.getId(), offer.getName(), bought, 1,
                                "Add 1 more " + dishName.apply(bought) + " and get " + offer.getFreeQuantity() + " "
                                        + dishName.apply(given) + " free"));
                    }
                }
                case COMBO -> {
                    List<MenuOfferEntity.ComboItem> items = offer.getComboItems();
                    if (items.isEmpty() || offer.getComboPrice() == null) break;
                    // One more set: what is missing, if the cart already has part of it.
                    int sets = Integer.MAX_VALUE;
                    for (MenuOfferEntity.ComboItem item : items) {
                        sets = Math.min(sets, units(pool, item.getMenuItemId()).size() / item.getQuantity());
                    }
                    int target = sets + 1;
                    boolean started = false;
                    List<MenuOfferEntity.ComboItem> missing = new ArrayList<>();
                    for (MenuOfferEntity.ComboItem item : items) {
                        int have = units(pool, item.getMenuItemId()).size();
                        if (have > sets * item.getQuantity()) started = true;
                        if (have < target * item.getQuantity()) {
                            missing.add(new MenuOfferEntity.ComboItem(item.getMenuItemId(), target * item.getQuantity() - have));
                        }
                    }
                    if (!started || missing.isEmpty() || missing.size() == items.size()) break;
                    StringBuilder what = new StringBuilder();
                    for (int i = 0; i < missing.size(); i++) {
                        if (i > 0) what.append(i == missing.size() - 1 ? " and " : ", ");
                        what.append(missing.get(i).getQuantity()).append(' ').append(dishName.apply(missing.get(i).getMenuItemId()));
                    }
                    MenuOfferEntity.ComboItem first = missing.get(0);
                    add(hints, new Hint(offer.getId(), offer.getName(), first.getMenuItemId(), first.getQuantity(),
                            "Add " + what + " to make it the " + offer.getName() + " for "
                                    + offer.getComboPrice().setScale(2, RoundingMode.HALF_UP).toPlainString()));
                }
                case PERCENT_OFF -> {
                    // Already applied to every unit: nothing to add.
                }
            }
        }
        return List.copyOf(hints.values());
    }

    private static void add(Map<String, Hint> hints, Hint hint) {
        hints.putIfAbsent(hint.offerId() + ":" + hint.menuItemId(), hint);
    }

    private static List<BigDecimal> units(Map<UUID, List<BigDecimal>> pool, UUID menuItemId) {
        return pool.getOrDefault(menuItemId, List.of());
    }

    private static BigDecimal sumCheapest(List<BigDecimal> ascending, int count) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < Math.min(count, ascending.size()); i++) {
            sum = sum.add(ascending.get(i));
        }
        return sum;
    }
}
