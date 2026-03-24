export type MenuItem = { id: string; name: string; price: number };
export type CartLine = { item: MenuItem; quantity: number };
export type OrderType = "DINE_IN" | "PICKUP" | "DELIVERY";

export function addToCart(cart: CartLine[], item: MenuItem): CartLine[] {
  const existing = cart.find((line) => line.item.id === item.id);
  if (!existing) return [...cart, { item, quantity: 1 }];
  return cart.map((line) =>
    line.item.id === item.id ? { ...line, quantity: line.quantity + 1 } : line,
  );
}

export function setQuantity(cart: CartLine[], itemId: string, quantity: number): CartLine[] {
  if (quantity <= 0) return cart.filter((line) => line.item.id !== itemId);
  return cart.map((line) =>
    line.item.id === itemId ? { ...line, quantity } : line,
  );
}

export function removeFromCart(cart: CartLine[], itemId: string): CartLine[] {
  return cart.filter((line) => line.item.id !== itemId);
}

export function cartTotal(cart: CartLine[]): number {
  return cart.reduce((sum, line) => sum + line.item.price * line.quantity, 0);
}

export type OrderPayload = {
  type: OrderType;
  items: { menuItemId: string; quantity: number }[];
  tableId?: string;
};

export function buildOrderPayload(type: OrderType, cart: CartLine[], tableId?: string): OrderPayload {
  const payload: OrderPayload = {
    type,
    items: cart.map((line) => ({ menuItemId: line.item.id, quantity: line.quantity })),
  };
  if (type === "DINE_IN" && tableId) {
    payload.tableId = tableId;
  }
  return payload;
}

export function paymentPendingMessage(): string {
  return "Waiting for MoMo approval";
}
