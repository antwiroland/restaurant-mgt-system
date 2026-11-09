/**
 * A sellable item as placed on a ticket. `modifierOptionIds` / `modifierLabel`
 * describe chosen options (size, spice, add-ons); `price` is the unit price
 * INCLUDING option price deltas. `id` is always the menu item id.
 */
export type MenuItem = {
  id: string;
  name: string;
  price: number;
  modifierOptionIds?: string[];
  modifierLabel?: string;
};
export type CartLine = { item: MenuItem; quantity: number; notes?: string };
export type OrderType = "DINE_IN" | "PICKUP" | "DELIVERY";

/**
 * Identity of a ticket line. The same dish with different options is a
 * separate line; with no options the key is simply the menu item id.
 */
export function lineKey(item: MenuItem): string {
  const options = item.modifierOptionIds?.length ? [...item.modifierOptionIds].sort().join(",") : "";
  return options ? `${item.id}::${options}` : item.id;
}

export function addToCart(cart: CartLine[], item: MenuItem, quantity = 1): CartLine[] {
  const key = lineKey(item);
  const existing = cart.find((line) => lineKey(line.item) === key);
  if (!existing) return [...cart, { item, quantity }];
  return cart.map((line) =>
    lineKey(line.item) === key ? { ...line, quantity: line.quantity + quantity } : line,
  );
}

/** `key` is a {@link lineKey} (equal to the item id for items without options). */
export function setQuantity(cart: CartLine[], key: string, quantity: number): CartLine[] {
  if (quantity <= 0) return cart.filter((line) => lineKey(line.item) !== key);
  return cart.map((line) =>
    lineKey(line.item) === key ? { ...line, quantity } : line,
  );
}

export function setLineNotes(cart: CartLine[], key: string, notes: string): CartLine[] {
  return cart.map((line) => (lineKey(line.item) === key ? { ...line, notes } : line));
}

export function removeFromCart(cart: CartLine[], key: string): CartLine[] {
  return cart.filter((line) => lineKey(line.item) !== key);
}

export function cartTotal(cart: CartLine[]): number {
  return cart.reduce((sum, line) => sum + line.item.price * line.quantity, 0);
}

export type OrderPayloadItem = {
  menuItemId: string;
  quantity: number;
  notes?: string;
  modifierOptionIds?: string[];
};

export type OrderPayload = {
  type: OrderType;
  items: OrderPayloadItem[];
  tableId?: string;
  promoCode?: string;
  notes?: string;
};

export function buildOrderPayload(
  type: OrderType,
  cart: CartLine[],
  tableId?: string,
  promoCode?: string,
  notes?: string,
): OrderPayload {
  const payload: OrderPayload = {
    type,
    items: cart.map((line) => {
      const entry: OrderPayloadItem = { menuItemId: line.item.id, quantity: line.quantity };
      if (line.item.modifierOptionIds?.length) entry.modifierOptionIds = line.item.modifierOptionIds;
      if (line.notes?.trim()) entry.notes = line.notes.trim();
      return entry;
    }),
  };
  if (type === "DINE_IN" && tableId) {
    payload.tableId = tableId;
  }
  const code = promoCode?.trim().toUpperCase();
  if (code) {
    payload.promoCode = code;
  }
  if (notes?.trim()) {
    payload.notes = notes.trim();
  }
  return payload;
}

export function paymentPendingMessage(): string {
  return "Waiting for MoMo approval";
}
