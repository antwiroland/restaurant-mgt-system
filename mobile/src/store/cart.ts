import { create } from 'zustand';
import type { MenuItem, OrderItem, PromoValidation } from '../types/api';

export type CartLine = {
  item: MenuItem;
  quantity: number;
  notes?: string;
};

type CartState = {
  lines: CartLine[];
  tableId: string | null;
  tableNumber: string | null;
  promoCode: string | null;
  promoType: PromoValidation['discountType'] | null;
  promoValue: string | null;
  addItem: (item: MenuItem, notes?: string) => void;
  removeItem: (itemId: string) => void;
  setQty: (itemId: string, qty: number) => void;
  setTable: (tableId: string, tableNumber: string) => void;
  hydrateFromOrder: (items: OrderItem[], opts?: { tableId?: string | null; tableNumber?: string | null }) => void;
  setPromo: (promo: PromoValidation) => void;
  clearPromo: () => void;
  clear: () => void;
  subtotal: () => number;
  discountAmount: () => number;
  total: () => number;
  itemCount: () => number;
};

export const useCartStore = create<CartState>((set, get) => ({
  lines: [],
  tableId: null,
  tableNumber: null,
  promoCode: null,
  promoType: null,
  promoValue: null,

  addItem: (item, notes) =>
    set((s) => {
      const existing = s.lines.find((l) => l.item.id === item.id);
      if (existing) {
        return {
          lines: s.lines.map((l) =>
            l.item.id === item.id ? { ...l, quantity: l.quantity + 1 } : l
          ),
        };
      }
      return { lines: [...s.lines, { item, quantity: 1, notes }] };
    }),

  removeItem: (itemId) =>
    set((s) => ({ lines: s.lines.filter((l) => l.item.id !== itemId) })),

  setQty: (itemId, qty) =>
    set((s) => {
      if (qty <= 0) return { lines: s.lines.filter((l) => l.item.id !== itemId) };
      return { lines: s.lines.map((l) => (l.item.id === itemId ? { ...l, quantity: qty } : l)) };
    }),

  setTable: (tableId, tableNumber) => set({ tableId, tableNumber }),

  hydrateFromOrder: (items, opts) =>
    set({
      lines: items.map((item) => ({
        item: {
          id: item.menuItemId,
          name: item.menuItemName,
          description: '',
          price: item.unitPrice,
          available: true,
          categoryId: '',
        },
        quantity: item.quantity,
        notes: item.notes,
      })),
      tableId: opts?.tableId ?? null,
      tableNumber: opts?.tableNumber ?? null,
      promoCode: null,
      promoType: null,
      promoValue: null,
    }),

  setPromo: (promo) => set({
    promoCode: promo.code,
    promoType: promo.discountType,
    promoValue: promo.discountValue,
  }),

  clearPromo: () => set({ promoCode: null, promoType: null, promoValue: null }),

  clear: () => set({
    lines: [],
    tableId: null,
    tableNumber: null,
    promoCode: null,
    promoType: null,
    promoValue: null,
  }),

  subtotal: () => get().lines.reduce((sum, l) => sum + parseFloat(l.item.price) * l.quantity, 0),

  discountAmount: () => {
    const subtotal = get().subtotal();
    const promoValue = get().promoValue;
    if (!promoValue) {
      return 0;
    }

    const numericValue = parseFloat(promoValue);
    if (Number.isNaN(numericValue) || numericValue <= 0) {
      return 0;
    }

    if (get().promoType === 'PERCENTAGE') {
      return Math.min(subtotal, (subtotal * numericValue) / 100);
    }

    return Math.min(subtotal, numericValue);
  },

  total: () => Math.max(0, get().subtotal() - get().discountAmount()),

  itemCount: () => get().lines.reduce((sum, l) => sum + l.quantity, 0),
}));
