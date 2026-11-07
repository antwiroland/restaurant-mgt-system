import { describe, expect, test } from "vitest";
import { addToCart, buildOrderPayload, cartTotal, lineKey, removeFromCart, setLineNotes, setQuantity } from "@/features/pos/cart";
import { canAccessPath, navForRole } from "@/lib/navigation";

describe("POS cart with modifiers", () => {
  const plain = { id: "jollof", name: "Jollof", price: 140 };
  const hot = { ...plain, price: 155, modifierOptionIds: ["hot", "plantain"], modifierLabel: "Hot · Fried plantain" };
  const hotReordered = { ...hot, modifierOptionIds: ["plantain", "hot"] };

  test("same dish with different options becomes separate lines", () => {
    const cart = addToCart(addToCart([], plain), hot);
    expect(cart).toHaveLength(2);
    expect(cartTotal(cart)).toBe(295);
  });

  test("option order does not create duplicate lines", () => {
    const cart = addToCart(addToCart([], hot), hotReordered);
    expect(cart).toHaveLength(1);
    expect(cart[0].quantity).toBe(2);
  });

  test("quantity, notes and removal target a single line", () => {
    let cart = addToCart(addToCart([], plain), hot);
    cart = setQuantity(cart, lineKey(hot), 3);
    cart = setLineNotes(cart, lineKey(hot), "no bones");
    expect(cart.find((l) => lineKey(l.item) === lineKey(hot))?.quantity).toBe(3);
    expect(cart.find((l) => l.item.id === "jollof" && !l.item.modifierOptionIds)?.quantity).toBe(1);
    cart = removeFromCart(cart, lineKey(plain));
    expect(cart).toHaveLength(1);
  });

  test("payload carries modifiers, line notes and order notes", () => {
    const cart = setLineNotes(addToCart([], hot), lineKey(hot), "  no bones ");
    const payload = buildOrderPayload("PICKUP", cart, "ignored-table", " save10 ", " birthday ");
    expect(payload).toEqual({
      type: "PICKUP",
      items: [{ menuItemId: "jollof", quantity: 1, modifierOptionIds: ["hot", "plantain"], notes: "no bones" }],
      promoCode: "SAVE10",
      notes: "birthday",
    });
  });
});

describe("role-aware navigation", () => {
  test("cashiers get service tools but not owner analytics or admin", () => {
    const hrefs = navForRole("CASHIER").flatMap((g) => g.items.map((i) => i.href));
    expect(hrefs).toContain("/pos");
    expect(hrefs).toContain("/kds");
    expect(hrefs).not.toContain("/dashboard");
    expect(hrefs).not.toContain("/users");
    expect(canAccessPath("CASHIER", "/menu")).toBe(false);
  });

  test("riders only see their deliveries", () => {
    expect(navForRole("RIDER").flatMap((g) => g.items.map((i) => i.href))).toEqual(["/rider"]);
    expect(canAccessPath("RIDER", "/pos")).toBe(false);
  });

  test("users page is admin-only, matching the backend guard", () => {
    expect(canAccessPath("MANAGER", "/users")).toBe(false);
    expect(canAccessPath("ADMIN", "/users")).toBe(true);
    expect(canAccessPath("MANAGER", "/orders/pickup")).toBe(true);
  });
});
