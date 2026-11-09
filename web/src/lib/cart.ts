"use client";

import { useSyncExternalStore } from "react";

/** A line in the web pickup/delivery cart. Prices are display-only; the server re-prices on quote/order. */
export type CartLine = {
  key: string;
  menuItemId: string;
  name: string;
  imageUrl?: string | null;
  unitPrice: number;
  quantity: number;
  notes?: string;
  modifierOptionIds: string[];
  modifierLabels: string[];
};

const STORAGE_KEY = "web-cart-v1";
const EMPTY: CartLine[] = [];
let cache: CartLine[] | null = null;
const listeners = new Set<() => void>();

function read(): CartLine[] {
  if (cache) return cache;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    cache = raw ? (JSON.parse(raw) as CartLine[]) : [];
  } catch {
    cache = [];
  }
  return cache;
}

function write(lines: CartLine[]) {
  cache = lines;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
  } catch {
    /* cart lives for this tab only */
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) {
      cache = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function useCart(): CartLine[] {
  return useSyncExternalStore(subscribe, read, () => EMPTY);
}

function lineKey(menuItemId: string, optionIds: string[], notes?: string) {
  return [menuItemId, [...optionIds].sort().join("+"), notes?.trim() ?? ""].join("|");
}

export const cart = {
  add(line: Omit<CartLine, "key">) {
    const key = lineKey(line.menuItemId, line.modifierOptionIds, line.notes);
    const lines = read();
    const existing = lines.find((l) => l.key === key);
    write(existing
      ? lines.map((l) => (l.key === key ? { ...l, quantity: Math.min(99, l.quantity + line.quantity) } : l))
      : [...lines, { ...line, key }]);
  },
  setQuantity(key: string, quantity: number) {
    write(quantity <= 0 ? read().filter((l) => l.key !== key) : read().map((l) => (l.key === key ? { ...l, quantity: Math.min(99, quantity) } : l)));
  },
  clear() {
    write([]);
  },
};

export function cartCount(lines: CartLine[]) {
  return lines.reduce((sum, l) => sum + l.quantity, 0);
}

export function cartSubtotal(lines: CartLine[]) {
  return lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0);
}
