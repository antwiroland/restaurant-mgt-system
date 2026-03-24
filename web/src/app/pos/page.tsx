"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { addToCart, buildOrderPayload, cartTotal, removeFromCart, setQuantity, type CartLine } from "@/features/pos/cart";
import { useStaffSession } from "@/components/SessionProvider";
import { createOrder, getMenuItems, getTables, type MenuItemRecord, type TableRecord } from "@/lib/apiClient";

export default function PosPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [orderType, setOrderType] = useState<"DINE_IN" | "PICKUP" | "DELIVERY">("DINE_IN");
  const [tableId, setTableId] = useState("");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const [nextMenuItems, nextTables] = await Promise.all([
        getMenuItems(activeSession),
        getTables(activeSession),
      ]);
      setMenuItems(nextMenuItems.filter((item) => item.active && item.available));
      setTables(nextTables);
    }).catch((err) => {
      setMessage(err instanceof Error ? err.message : "Could not load POS data");
    });
  }, [authenticatedFetch, session]);

  const total = useMemo(() => cartTotal(cart), [cart]);
  const dineInTables = tables.filter((table) => table.status !== "CLOSED");

  async function handleSubmit() {
    if (!session) return;
    if (cart.length === 0) {
      setMessage("Add at least one item before submitting the order.");
      return;
    }
    if (orderType === "DINE_IN" && !tableId) {
      setMessage("Choose a table for dine-in orders.");
      return;
    }
    if (orderType === "DELIVERY" && !deliveryAddress.trim()) {
      setMessage("Enter a delivery address.");
      return;
    }

    setSubmitting(true);
    setMessage("");
    try {
      const payload = buildOrderPayload(orderType, cart, tableId || undefined);
      const created = await authenticatedFetch((activeSession) => createOrder(activeSession, {
        ...payload,
        deliveryAddress: orderType === "DELIVERY" ? deliveryAddress.trim() : undefined,
      }));
      setCart([]);
      setDeliveryAddress("");
      if (orderType !== "DINE_IN") {
        setTableId("");
      }
      setMessage(`Order ${created.id.slice(0, 8)} created successfully.`);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Could not create order");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel">Loading POS...</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">POS</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 md:grid-cols-[1.4fr,1fr]">
      <section className="panel">
        <p className="kicker">POS</p>
        <h1 className="text-2xl font-semibold">Menu Browser</h1>
        <div className="mt-4 grid gap-2">
          {menuItems.map((item) => (
            <button
              key={item.id}
              className="rounded-xl border border-[#cfe0c8] p-3 text-left transition hover:border-[#132018]"
              onClick={() => setCart((current) => addToCart(current, {
                id: item.id,
                name: item.name,
                price: Number.parseFloat(item.price),
              }))}
            >
              <div className="flex items-center justify-between gap-3">
                <span className="font-medium">{item.name}</span>
                <span>GHS {item.price}</span>
              </div>
              <p className="mt-1 text-sm text-[#35523d]">{item.categoryName}</p>
            </button>
          ))}
        </div>
      </section>

      <aside className="panel">
        <p className="kicker">Cart</p>
        <h2 className="text-xl font-semibold">Current Order</h2>
        <div className="mt-4 grid gap-3">
          <label className="grid gap-2 text-sm text-[#35523d]">
            <span>Order Type</span>
            <select className="rounded-xl border border-[#cfe0c8] p-3" value={orderType} onChange={(event) => setOrderType(event.target.value as "DINE_IN" | "PICKUP" | "DELIVERY")}>
              <option value="DINE_IN">Dine In</option>
              <option value="PICKUP">Pickup</option>
              <option value="DELIVERY">Delivery</option>
            </select>
          </label>

          {orderType === "DINE_IN" ? (
            <label className="grid gap-2 text-sm text-[#35523d]">
              <span>Table</span>
              <select className="rounded-xl border border-[#cfe0c8] p-3" value={tableId} onChange={(event) => setTableId(event.target.value)}>
                <option value="">Select a table</option>
                {dineInTables.map((table) => (
                  <option key={table.id} value={table.id}>{table.number} · {table.status}</option>
                ))}
              </select>
            </label>
          ) : null}

          {orderType === "DELIVERY" ? (
            <label className="grid gap-2 text-sm text-[#35523d]">
              <span>Delivery Address</span>
              <textarea
                className="min-h-28 rounded-xl border border-[#cfe0c8] p-3"
                value={deliveryAddress}
                onChange={(event) => setDeliveryAddress(event.target.value)}
              />
            </label>
          ) : null}

          <div className="grid gap-2">
            {cart.length === 0 ? (
              <p className="rounded-xl border border-dashed border-[#cfe0c8] p-4 text-sm text-[#35523d]">Select items from the menu to build an order.</p>
            ) : (
              cart.map((line) => (
                <div key={line.item.id} className="rounded-xl border border-[#cfe0c8] p-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-medium">{line.item.name}</span>
                    <span>GHS {(line.item.price * line.quantity).toFixed(2)}</span>
                  </div>
                  <div className="mt-3 flex items-center gap-2">
                    <button className="rounded-full border border-[#132018] px-3 py-1" onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity - 1))}>-</button>
                    <span className="min-w-8 text-center">{line.quantity}</span>
                    <button className="rounded-full border border-[#132018] px-3 py-1" onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity + 1))}>+</button>
                    <button className="ml-auto text-sm text-[#9f1239]" onClick={() => setCart((current) => removeFromCart(current, line.item.id))}>Remove</button>
                  </div>
                </div>
              ))
            )}
          </div>

          <div className="rounded-xl bg-[#eef6ea] p-4">
            <p className="kicker">Total</p>
            <p className="mt-2 text-3xl font-semibold">GHS {total.toFixed(2)}</p>
          </div>

          {message ? <p className="rounded-xl bg-[#f3f4f6] px-4 py-3 text-sm text-[#35523d]">{message}</p> : null}

          <button className="rounded-full bg-[#132018] px-4 py-3 text-white disabled:opacity-60" disabled={submitting} onClick={() => void handleSubmit()}>
            {submitting ? "Submitting..." : "Submit Order"}
          </button>
        </div>
      </aside>
    </main>
  );
}
