export type MenuItem = { id: string; name: string; categoryId: string; price: number };
export type CartLine = { item: MenuItem; quantity: number };

export function itemsForCategory(items: MenuItem[], categoryId: string): MenuItem[] {
  return items.filter((item) => item.categoryId === categoryId);
}

export function addItem(cart: CartLine[], item: MenuItem): CartLine[] {
  const existing = cart.find((line) => line.item.id === item.id);
  if (!existing) return [...cart, { item, quantity: 1 }];
  return cart.map((line) => (line.item.id === item.id ? { ...line, quantity: line.quantity + 1 } : line));
}

export function setCartQty(cart: CartLine[], itemId: string, quantity: number): CartLine[] {
  if (quantity <= 0) return cart.filter((line) => line.item.id !== itemId);
  return cart.map((line) => (line.item.id === itemId ? { ...line, quantity } : line));
}
