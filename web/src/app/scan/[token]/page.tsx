"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  createPublicDineInOrder,
  getMenuItemModifiers,
  getPublicMenuItems,
  getTableScan,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
  type TableScanRecord,
} from "@/lib/apiClient";

type CartLine = {
  key: string;
  item: MenuItemRecord;
  quantity: number;
  unitPrice: number;
  modifierOptionIds: string[];
  modifierSummary: string[];
};

type SelectionByGroup = Record<string, string[]>;

function toCurrencyValue(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function buildInitialSelection(groups: MenuModifierGroupRecord[]): SelectionByGroup {
  const initial: SelectionByGroup = {};
  for (const group of groups) {
    if (group.required && group.options.length > 0) {
      if (group.selectionType === "SINGLE") {
        initial[group.id] = [group.options[0].id];
      } else {
        const min = Math.max(group.minSelect ?? 1, 1);
        initial[group.id] = group.options.slice(0, min).map((option) => option.id);
      }
    } else {
      initial[group.id] = [];
    }
  }
  return initial;
}

export default function GuestScanMenuPage() {
  const params = useParams<{ token: string }>();
  const [token, setToken] = useState("");
  const [table, setTable] = useState<TableScanRecord | null>(null);
  const [menu, setMenu] = useState<MenuItemRecord[]>([]);
  const [modifiersByItemId, setModifiersByItemId] = useState<Record<string, MenuModifierGroupRecord[]>>({});
  const [selectionByItemId, setSelectionByItemId] = useState<Record<string, SelectionByGroup>>({});
  const [cart, setCart] = useState<CartLine[]>([]);
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!params?.token) {
      setError("Invalid table QR link");
      return;
    }
    setToken(params.token);
  }, [params]);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    Promise.all([getTableScan(token), getPublicMenuItems()])
      .then(async ([tableResponse, menuResponse]) => {
        const availableMenu = menuResponse.filter((item) => item.available);
        setTable(tableResponse);
        setMenu(availableMenu);

        const modifierEntries = await Promise.all(
          availableMenu.map(async (item) => {
            const groups = await getMenuItemModifiers(item.id);
            return [item.id, groups] as const;
          }),
        );

        const modifierMap = Object.fromEntries(modifierEntries);
        setModifiersByItemId(modifierMap);
        setSelectionByItemId((previous) => {
          const next: Record<string, SelectionByGroup> = { ...previous };
          for (const [itemId, groups] of modifierEntries) {
            if (!next[itemId]) {
              next[itemId] = buildInitialSelection(groups);
            }
          }
          return next;
        });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load table menu"))
      .finally(() => setLoading(false));
  }, [token]);

  const categories = useMemo(() => {
    const map = new Map<string, MenuItemRecord[]>();
    for (const item of menu) {
      const bucket = map.get(item.categoryName) ?? [];
      bucket.push(item);
      map.set(item.categoryName, bucket);
    }
    return Array.from(map.entries());
  }, [menu]);

  const total = useMemo(
    () => cart.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0),
    [cart],
  );

  function setSingleSelection(itemId: string, groupId: string, optionId: string) {
    setSelectionByItemId((previous) => ({
      ...previous,
      [itemId]: {
        ...(previous[itemId] ?? {}),
        [groupId]: optionId ? [optionId] : [],
      },
    }));
  }

  function toggleMultiSelection(itemId: string, group: MenuModifierGroupRecord, optionId: string) {
    setSelectionByItemId((previous) => {
      const selected = previous[itemId]?.[group.id] ?? [];
      const exists = selected.includes(optionId);
      const nextSelected = exists ? selected.filter((id) => id !== optionId) : [...selected, optionId];
      if (!exists && group.maxSelect != null && nextSelected.length > group.maxSelect) {
        setError(`Only ${group.maxSelect} option(s) allowed for ${group.name}.`);
        return previous;
      }

      return {
        ...previous,
        [itemId]: {
          ...(previous[itemId] ?? {}),
          [group.id]: nextSelected,
        },
      };
    });
  }

  function resolveSelection(item: MenuItemRecord): {
    key: string;
    modifierOptionIds: string[];
    modifierSummary: string[];
    unitPrice: number;
  } {
    const groups = modifiersByItemId[item.id] ?? [];
    const selectedByGroup = selectionByItemId[item.id] ?? {};

    const optionIds: string[] = [];
    const summary: string[] = [];
    let modifierDelta = 0;

    for (const group of groups) {
      const selectedIds = selectedByGroup[group.id] ?? [];
      const min = group.minSelect ?? (group.required ? 1 : 0);
      const max = group.maxSelect;

      if (group.required && selectedIds.length === 0) {
        throw new Error(`Please choose ${group.name}.`);
      }
      if (selectedIds.length < min) {
        throw new Error(`Choose at least ${min} option(s) for ${group.name}.`);
      }
      if (max != null && selectedIds.length > max) {
        throw new Error(`Choose at most ${max} option(s) for ${group.name}.`);
      }
      if (group.selectionType === "SINGLE" && selectedIds.length > 1) {
        throw new Error(`Only one option is allowed for ${group.name}.`);
      }

      for (const optionId of selectedIds) {
        const option = group.options.find((candidate) => candidate.id === optionId);
        if (!option) {
          continue;
        }
        optionIds.push(option.id);
        summary.push(`${group.name}: ${option.name}`);
        modifierDelta += toCurrencyValue(option.priceDelta);
      }
    }

    const sortedOptionIds = [...optionIds].sort();
    const key = `${item.id}:${sortedOptionIds.join(",") || "base"}`;

    return {
      key,
      modifierOptionIds: sortedOptionIds,
      modifierSummary: summary,
      unitPrice: toCurrencyValue(item.price) + modifierDelta,
    };
  }

  function addConfiguredItem(item: MenuItemRecord) {
    try {
      setError("");
      const resolved = resolveSelection(item);
      setCart((previous) => {
        const existing = previous.find((line) => line.key === resolved.key);
        if (existing) {
          return previous.map((line) => (line.key === resolved.key ? { ...line, quantity: line.quantity + 1 } : line));
        }
        return [...previous, {
          key: resolved.key,
          item,
          quantity: 1,
          unitPrice: resolved.unitPrice,
          modifierOptionIds: resolved.modifierOptionIds,
          modifierSummary: resolved.modifierSummary,
        }];
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Modifier selection is invalid");
    }
  }

  function updateQuantity(lineKey: string, delta: number) {
    setCart((previous) => {
      const existing = previous.find((line) => line.key === lineKey);
      if (!existing && delta < 0) {
        return previous;
      }
      if (!existing) {
        return previous;
      }
      const nextQty = existing.quantity + delta;
      if (nextQty <= 0) {
        return previous.filter((line) => line.key !== lineKey);
      }
      return previous.map((line) => (line.key === lineKey ? { ...line, quantity: nextQty } : line));
    });
  }

  async function placeOrder() {
    if (cart.length === 0 || !token) {
      return;
    }
    setPlacing(true);
    setError("");
    try {
      const order = await createPublicDineInOrder({
        tableToken: token,
        notes: notes.trim() || undefined,
        items: cart.map((line) => ({
          menuItemId: line.item.id,
          quantity: line.quantity,
          modifierOptionIds: line.modifierOptionIds.length > 0 ? line.modifierOptionIds : undefined,
        })),
      });
      setCart([]);
      setNotes("");
      setMessage(`Order ${order.id.slice(0, 8).toUpperCase()} sent for table ${order.tableNumber}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not place order");
    } finally {
      setPlacing(false);
    }
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Table Order</p>
        <h1 className="mt-2 text-3xl font-semibold">
          {table ? `Table ${table.tableNumber} Menu` : "Loading table..."}
        </h1>
        <p className="mt-2 text-[#35523d]">No app download and no sign-up required. Add items and submit.</p>

        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-3 py-2 text-sm text-[#991b1b]">{error}</p> : null}
        {message ? <p className="mt-3 rounded-xl bg-[#dcfce7] px-3 py-2 text-sm text-[#166534]">{message}</p> : null}

        {loading ? (
          <p className="mt-6 text-sm text-[#35523d]">Loading menu...</p>
        ) : (
          <div className="mt-6 grid grid-cols-1 gap-6 xl:grid-cols-[2fr_1fr]">
            <div className="space-y-5">
              {categories.map(([category, items]) => (
                <section key={category}>
                  <h2 className="mb-2 text-lg font-semibold">{category}</h2>
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                    {items.map((item) => {
                      const groups = modifiersByItemId[item.id] ?? [];
                      const selectedByGroup = selectionByItemId[item.id] ?? {};

                      return (
                        <article key={item.id} className="rounded-xl border border-[#d6e4ce] bg-white p-3">
                          <p className="font-semibold">{item.name}</p>
                          {item.description ? <p className="mt-1 text-sm text-[#35523d]">{item.description}</p> : null}
                          {item.imageUrl ? (
                            <img src={item.imageUrl} alt={item.name} className="mt-2 h-28 w-full rounded-lg object-cover" />
                          ) : null}
                          {groups.map((group) => (
                            <div key={group.id} className="mt-3 rounded-lg border border-[#dfe9d8] p-2">
                              <div className="flex items-center justify-between">
                                <p className="text-xs font-semibold uppercase tracking-wide text-[#35523d]">{group.name}</p>
                                <p className="text-[11px] text-[#4b6a53]">
                                  {group.selectionType === "SINGLE" ? "Pick one" : `Pick ${group.minSelect ?? 0}-${group.maxSelect ?? group.options.length}`}
                                </p>
                              </div>

                              {group.selectionType === "SINGLE" ? (
                                <select
                                  className="mt-2 w-full rounded-lg border border-[#d6e4ce] px-2 py-1 text-sm"
                                  value={(selectedByGroup[group.id] ?? [""])[0] ?? ""}
                                  onChange={(event) => setSingleSelection(item.id, group.id, event.target.value)}
                                >
                                  {!group.required ? <option value="">None</option> : null}
                                  {group.options.map((option) => (
                                    <option key={option.id} value={option.id}>
                                      {option.name} ({toCurrencyValue(option.priceDelta) >= 0 ? "+" : ""}GHS {option.priceDelta})
                                    </option>
                                  ))}
                                </select>
                              ) : (
                                <div className="mt-2 grid grid-cols-2 gap-2">
                                  {group.options.map((option) => {
                                    const selected = (selectedByGroup[group.id] ?? []).includes(option.id);
                                    return (
                                      <button
                                        key={option.id}
                                        type="button"
                                        className={`rounded-lg border px-2 py-1 text-left text-xs ${selected ? "border-[#132018] bg-[#ecf5e7]" : "border-[#d6e4ce]"}`}
                                        onClick={() => toggleMultiSelection(item.id, group, option.id)}
                                      >
                                        <span className="block font-semibold">{option.name}</span>
                                        <span className="block text-[11px] text-[#35523d]">
                                          {toCurrencyValue(option.priceDelta) >= 0 ? "+" : ""}GHS {option.priceDelta}
                                        </span>
                                      </button>
                                    );
                                  })}
                                </div>
                              )}
                            </div>
                          ))}
                          <div className="mt-3 flex items-center justify-between">
                            <p className="font-mono text-sm">Base GHS {item.price}</p>
                            <button
                              type="button"
                              className="rounded-full bg-[#132018] px-4 py-1.5 text-sm text-white"
                              onClick={() => addConfiguredItem(item)}
                            >
                              Add
                            </button>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>

            <aside className="rounded-xl border border-[#d6e4ce] bg-white p-4">
              <h2 className="text-xl font-semibold">Your Table Bill</h2>
              <p className="mt-1 text-sm text-[#35523d]">New items are added to the same table bill.</p>
              <div className="mt-4 space-y-3">
                {cart.length === 0 ? (
                  <p className="text-sm text-[#35523d]">Cart is empty.</p>
                ) : (
                  cart.map((line) => (
                    <div key={line.key} className="rounded-lg border border-[#dfe9d8] p-2 text-sm">
                      <div className="flex items-center justify-between">
                        <span>{line.item.name} x {line.quantity}</span>
                        <span>GHS {(line.unitPrice * line.quantity).toFixed(2)}</span>
                      </div>
                      {line.modifierSummary.length > 0 ? (
                        <p className="mt-1 text-xs text-[#35523d]">{line.modifierSummary.join(", ")}</p>
                      ) : null}
                      <div className="mt-2 flex gap-2">
                        <button
                          type="button"
                          className="rounded-full border border-[#132018] px-2 py-0.5"
                          onClick={() => updateQuantity(line.key, -1)}
                        >
                          -
                        </button>
                        <button
                          type="button"
                          className="rounded-full border border-[#132018] px-2 py-0.5"
                          onClick={() => updateQuantity(line.key, 1)}
                        >
                          +
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
              <label className="mt-4 block text-sm font-semibold" htmlFor="notes">Order note</label>
              <textarea
                id="notes"
                className="mt-1 w-full rounded-lg border border-[#d6e4ce] px-3 py-2 text-sm"
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                placeholder="Optional note for kitchen"
              />
              <div className="mt-4 flex items-center justify-between">
                <span className="font-semibold">Total</span>
                <span className="font-mono text-lg">GHS {total.toFixed(2)}</span>
              </div>
              <button
                type="button"
                disabled={placing || cart.length === 0}
                onClick={placeOrder}
                className="mt-4 w-full rounded-full bg-[#132018] px-4 py-2 text-white disabled:cursor-not-allowed disabled:bg-[#6b7280]"
              >
                {placing ? "Submitting..." : "Place Order"}
              </button>
            </aside>
          </div>
        )}
      </section>
    </main>
  );
}
