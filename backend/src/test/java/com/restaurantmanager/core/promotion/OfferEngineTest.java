package com.restaurantmanager.core.promotion;

import com.restaurantmanager.core.branch.BranchEntity;
import com.restaurantmanager.core.menu.MenuItemEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OfferEngineTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");

    private final MenuItemEntity kelewele = dish("Kelewele");
    private final MenuItemEntity burger = dish("Burger");
    private final MenuItemEntity soda = dish("Soda");
    private final MenuItemEntity fries = dish("Fries");
    private final Map<UUID, String> names = Map.of(kelewele.getId(), "Kelewele", burger.getId(), "Burger",
            soda.getId(), "Soda", fries.getId(), "Fries");

    @Test
    void buyTwoGetOne_threeUnits_cheapestIsFree() {
        MenuOfferEntity offer = buyXGetY(kelewele, 2, 1);
        var result = evaluate(List.of(line(kelewele, "45.00", 2), line(kelewele, "40.00", 1)), offer);
        assertThat(result.discount()).isEqualByComparingTo("40.00");
    }

    @Test
    void buyTwoGetOne_twoUnits_noDiscountButHintToAddOneFree() {
        var result = evaluate(List.of(line(kelewele, "45.00", 2)), buyXGetY(kelewele, 2, 1));
        assertThat(result.discount()).isEqualByComparingTo("0");
        assertThat(result.hints()).singleElement().satisfies(hint -> {
            assertThat(hint.menuItemId()).isEqualTo(kelewele.getId());
            assertThat(hint.quantity()).isEqualTo(1);
            assertThat(hint.message()).isEqualTo("Add 1 more Kelewele: it's free");
        });
    }

    @Test
    void buyTwoGetOne_oneUnit_hintToUnlock() {
        var result = evaluate(List.of(line(kelewele, "45.00", 1)), buyXGetY(kelewele, 2, 1));
        assertThat(result.hints()).singleElement()
                .satisfies(hint -> assertThat(hint.message()).isEqualTo("Add 1 more Kelewele and get 1 free"));
    }

    @Test
    void buyBurgerGetSoda_freeSodaWhenBothInCart_hintWhenSodaMissing() {
        MenuOfferEntity offer = buyXGetOther(burger, 1, soda, 1);
        var withSoda = evaluate(List.of(line(burger, "80.00", 2), line(soda, "15.00", 1)), offer);
        assertThat(withSoda.discount()).isEqualByComparingTo("15.00");
        assertThat(withSoda.hints()).singleElement().satisfies(hint -> {
            assertThat(hint.menuItemId()).isEqualTo(soda.getId());
            assertThat(hint.message()).isEqualTo("Add 1 Soda: free with your Burger");
        });
    }

    @Test
    void percentOff_appliesToEveryUnit() {
        MenuOfferEntity offer = percentOff(burger, "25");
        var result = evaluate(List.of(line(burger, "80.00", 2)), offer);
        assertThat(result.discount()).isEqualByComparingTo("40.00");
        assertThat(result.hints()).isEmpty();
    }

    @Test
    void combo_completeSetPricedAtComboPrice_partialSetHints() {
        MenuOfferEntity offer = combo("Lunch combo", "90.00", burger, fries, soda);
        var complete = evaluate(List.of(line(burger, "80.00", 1), line(fries, "20.00", 1), line(soda, "15.00", 1)), offer);
        assertThat(complete.discount()).isEqualByComparingTo("25.00");

        var partial = evaluate(List.of(line(burger, "80.00", 1), line(fries, "20.00", 1)), offer);
        assertThat(partial.discount()).isEqualByComparingTo("0");
        assertThat(partial.hints()).singleElement().satisfies(hint -> {
            assertThat(hint.menuItemId()).isEqualTo(soda.getId());
            assertThat(hint.message()).isEqualTo("Add 1 Soda to make it the Lunch combo for 90.00");
        });
    }

    @Test
    void comboThatWouldCostMore_isNotApplied() {
        MenuOfferEntity offer = combo("Bad combo", "200.00", burger, fries);
        var result = evaluate(List.of(line(burger, "80.00", 1), line(fries, "20.00", 1)), offer);
        assertThat(result.discount()).isEqualByComparingTo("0");
    }

    @Test
    void aUnitIsNeverDiscountedTwice_bestOfferWins() {
        // 25% off two burgers (40) beats "buy 1 burger get 1 soda" (15) for the burgers; the soda deal then has no
        // burger left to qualify with, so the soda is not also free.
        MenuOfferEntity percent = percentOff(burger, "25");
        MenuOfferEntity sodaDeal = buyXGetOther(burger, 1, soda, 1);
        var result = OfferEngine.evaluate(List.of(line(burger, "80.00", 2), line(soda, "15.00", 1)),
                List.of(sodaDeal, percent), names::get);
        assertThat(result.discount()).isEqualByComparingTo("40.00");
        assertThat(result.applied()).singleElement().satisfies(applied -> assertThat(applied.offerId()).isEqualTo(percent.getId()));
    }

    @Test
    void happyHour_onlyInsideItsDaysAndHours() {
        MenuOfferEntity offer = buyXGetY(kelewele, 1, 1);
        offer.setDaysOfWeek("FRI");
        offer.setDailyStartTime(LocalTime.of(16, 0));
        offer.setDailyEndTime(LocalTime.of(18, 0));
        // 2026-11-13 is a Friday; Accra is UTC+0.
        assertThat(offer.isRunningAt(Instant.parse("2026-11-13T17:00:00Z"), ACCRA)).isTrue();
        assertThat(offer.isRunningAt(Instant.parse("2026-11-13T18:00:00Z"), ACCRA)).isFalse();
        assertThat(offer.isRunningAt(Instant.parse("2026-11-13T15:59:00Z"), ACCRA)).isFalse();
        assertThat(offer.isRunningAt(Instant.parse("2026-11-12T17:00:00Z"), ACCRA)).isFalse();
    }

    @Test
    void overnightWindow_afterMidnightBelongsToThePreviousDay() {
        MenuOfferEntity offer = buyXGetY(kelewele, 1, 1);
        offer.setDaysOfWeek("FRI");
        offer.setDailyStartTime(LocalTime.of(22, 0));
        offer.setDailyEndTime(LocalTime.of(2, 0));
        assertThat(offer.isRunningAt(Instant.parse("2026-11-13T23:00:00Z"), ACCRA)).isTrue();  // Fri 23:00
        assertThat(offer.isRunningAt(Instant.parse("2026-11-14T01:00:00Z"), ACCRA)).isTrue();  // Sat 01:00, Friday's night
        assertThat(offer.isRunningAt(Instant.parse("2026-11-13T01:00:00Z"), ACCRA)).isFalse(); // Fri 01:00, Thursday's night
    }

    @Test
    void branchOffer_onlyAtThatBranch_andNotWhenBranchUnknown() throws Exception {
        MenuOfferEntity offer = buyXGetY(kelewele, 1, 1);
        BranchEntity branch = new BranchEntity();
        setId(branch, UUID.randomUUID());
        offer.setBranch(branch);
        assertThat(offer.appliesToBranch(branch.getId())).isTrue();
        assertThat(offer.appliesToBranch(UUID.randomUUID())).isFalse();
        assertThat(offer.appliesToBranch(null)).isFalse();
        offer.setBranch(null);
        assertThat(offer.appliesToBranch(null)).isTrue();
    }

    private OfferEngine.Result evaluate(List<OfferEngine.CartLine> lines, MenuOfferEntity offer) {
        return OfferEngine.evaluate(lines, List.of(offer), names::get);
    }

    private static OfferEngine.CartLine line(MenuItemEntity dish, String price, int quantity) {
        return new OfferEngine.CartLine(dish.getId(), new BigDecimal(price), quantity);
    }

    private static MenuOfferEntity offer(String name, OfferType type) {
        MenuOfferEntity offer = new MenuOfferEntity();
        offer.prePersist();
        offer.setName(name);
        offer.setOfferType(type);
        return offer;
    }

    private static MenuOfferEntity buyXGetY(MenuItemEntity dish, int buy, int free) {
        MenuOfferEntity offer = offer("Buy " + buy + " get " + free, OfferType.BUY_X_GET_Y);
        offer.setMenuItem(dish);
        offer.setBuyQuantity(buy);
        offer.setFreeQuantity(free);
        return offer;
    }

    private static MenuOfferEntity buyXGetOther(MenuItemEntity bought, int buy, MenuItemEntity given, int free) {
        MenuOfferEntity offer = offer("Meal deal", OfferType.BUY_X_GET_OTHER);
        offer.setMenuItem(bought);
        offer.setBuyQuantity(buy);
        offer.setFreeMenuItem(given);
        offer.setFreeQuantity(free);
        return offer;
    }

    private static MenuOfferEntity percentOff(MenuItemEntity dish, String percent) {
        MenuOfferEntity offer = offer(percent + "% off", OfferType.PERCENT_OFF);
        offer.setMenuItem(dish);
        offer.setPercentOff(new BigDecimal(percent));
        return offer;
    }

    private static MenuOfferEntity combo(String name, String price, MenuItemEntity... dishes) {
        MenuOfferEntity offer = offer(name, OfferType.COMBO);
        offer.setComboPrice(new BigDecimal(price));
        offer.setComboItems(java.util.Arrays.stream(dishes).map(d -> new MenuOfferEntity.ComboItem(d.getId(), 1)).toList());
        return offer;
    }

    private static MenuItemEntity dish(String name) {
        MenuItemEntity dish = new MenuItemEntity();
        try {
            setId(dish, UUID.randomUUID());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        dish.setName(name);
        return dish;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
