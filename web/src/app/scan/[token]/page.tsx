"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  createPublicDineInOrder,
  getPublicMenuItems,
  getTableScan,
  type MenuItemRecord,
  type TableScanRecord,
} from "@/lib/apiClient";

type CartLine = {
  item: MenuItemRecord;
  quantity: number;
};

export default function GuestScanMenuPage() {
  const params = useParams<{ token: string }>();
  const [token, setToken] = useState("");
  const [table, setTable] = useState<TableScanRecord | null>(null);
  const [menu, setMenu] = useState<MenuItemRecord[]>([]);
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
      .then(([tableResponse, menuResponse]) => {
        setTable(tableResponse);
        setMenu(menuResponse.filter((item) => item.available));
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
    () => cart.reduce((sum, line) => sum + Number.parseFloat(line.item.price) * line.quantity, 0),
    [cart],
  );

  function updateQuantity(item: MenuItemRecord, delta: number) {
    setCart((previous) => {
      const existing = previous.find((line) => line.item.id === item.id);
      if (!existing && delta < 0) {
        return previous;
      }
      if (!existing) {
        return [...previous, { item, quantity: 1 }];
      }
      const nextQty = existing.quantity + delta;
      if (nextQty <= 0) {
        return previous.filter((line) => line.item.id !== item.id);
      }
      return previous.map((line) => (line.item.id === item.id ? { ...line, quantity: nextQty } : line));
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
        items: cart.map((line) => ({ menuItemId: line.item.id, quantity: line.quantity })),
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
                    {items.map((item) => (
                      <article key={item.id} className="rounded-xl border border-[#d6e4ce] bg-white p-3">
                        <p className="font-semibold">{item.name}</p>
                        {item.description ? <p className="mt-1 text-sm text-[#35523d]">{item.description}</p> : null}
                        {item.imageUrl ? (
                          <img src={item.imageUrl} alt={item.name} className="mt-2 h-28 w-full rounded-lg object-cover" />
                        ) : null}
                        <div className="mt-3 flex items-center justify-between">
                          <p className="font-mono text-sm">GHS {item.price}</p>
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              className="rounded-full border border-[#132018] px-3 py-1"
                              onClick={() => updateQuantity(item, -1)}
                            >
                              -
                            </button>
                            <button
                              type="button"
                              className="rounded-full bg-[#132018] px-3 py-1 text-white"
                              onClick={() => updateQuantity(item, 1)}
                            >
                              +
                            </button>
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                </section>
              ))}
            </div>

            <aside className="rounded-xl border border-[#d6e4ce] bg-white p-4">
              <h2 className="text-xl font-semibold">Your Table Bill</h2>
              <p className="mt-1 text-sm text-[#35523d]">New items are added to the same table bill.</p>
              <div className="mt-4 space-y-2">
                {cart.length === 0 ? (
                  <p className="text-sm text-[#35523d]">Cart is empty.</p>
                ) : (
                  cart.map((line) => (
                    <div key={line.item.id} className="flex items-center justify-between text-sm">
                      <span>{line.item.name} x {line.quantity}</span>
                      <span>GHS {(Number.parseFloat(line.item.price) * line.quantity).toFixed(2)}</span>
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
