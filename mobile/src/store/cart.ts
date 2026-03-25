import { create } from 'zustand';
import type { MenuItem, OrderItem, PromoValidation } from '../types/api';

export type CartLine = {
  key: string;
  item: MenuItem;
  quantity: number;
  notes?: string;
  modifierOptionIds?: string[];
  modifierSummary?: string[];
  unitPrice?: string;
};

type CartState = {
  lines: CartLine[];
  tableId: string | null;
  tableNumber: string | null;
  tableToken: string | null;
  tableStatus: string | null;
  promoCode: string | null;
  promoType: PromoValidation['discountType'] | null;
  promoValue: string | null;
  addItem: (item: MenuItem, options?: { notes?: string; modifierOptionIds?: string[]; modifierSummary?: string[]; unitPrice?: string }) => void;
  removeItem: (lineKey: string) => void;
  setQty: (lineKey: string, qty: number) => void;
  setTable: (tableId: string, tableNumber: string, tableToken: string, tableStatus: string) => void;
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
  tableToken: null,
  tableStatus: null,
  promoCode: null,
  promoType: null,
  promoValue: null,

  addItem: (item, options) =>
    set((s) => {
      const sortedModifierIds = [...(options?.modifierOptionIds ?? [])].sort();
      const key = `${item.id}:${sortedModifierIds.join(',') || 'base'}`;
      const existing = s.lines.find((l) => l.key === key);
      if (existing) {
        return {
          lines: s.lines.map((l) =>
            l.key === key ? { ...l, quantity: l.quantity + 1 } : l
          ),
        };
      }
      return {
        lines: [
          ...s.lines,
          {
            key,
            item,
            quantity: 1,
            notes: options?.notes,
            modifierOptionIds: sortedModifierIds.length > 0 ? sortedModifierIds : undefined,
            modifierSummary: options?.modifierSummary,
            unitPrice: options?.unitPrice ?? item.price,
          },
        ],
      };
    }),

  removeItem: (lineKey) =>
    set((s) => ({ lines: s.lines.filter((l) => l.key !== lineKey) })),

  setQty: (lineKey, qty) =>
    set((s) => {
      if (qty <= 0) return { lines: s.lines.filter((l) => l.key !== lineKey) };
      return { lines: s.lines.map((l) => (l.key === lineKey ? { ...l, quantity: qty } : l)) };
    }),

  setTable: (tableId, tableNumber, tableToken, tableStatus) => set({ tableId, tableNumber, tableToken, tableStatus }),

  hydrateFromOrder: (items, opts) =>
    set({
      lines: items.map((item, index) => ({
        key: `${item.menuItemId}:base:${index}`,
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
        unitPrice: item.unitPrice,
      })),
      tableId: opts?.tableId ?? null,
      tableNumber: opts?.tableNumber ?? null,
      tableToken: null,
      tableStatus: null,
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
    tableToken: null,
    tableStatus: null,
    promoCode: null,
    promoType: null,
    promoValue: null,
  }),

  subtotal: () => get().lines.reduce((sum, l) => sum + parseFloat(l.unitPrice ?? l.item.price) * l.quantity, 0),

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
