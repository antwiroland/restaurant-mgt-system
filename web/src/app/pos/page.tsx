"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { addToCart, buildOrderPayload, cartTotal, removeFromCart, setQuantity, type CartLine } from "@/features/pos/cart";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createOrder,
  getMenuItems,
  getPayment,
  getTables,
  initiatePayment,
  verifyPayment,
  type MenuItemRecord,
  type PaymentMethod,
  type PaymentRecord,
  type TableRecord,
} from "@/lib/apiClient";

export default function PosPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [orderType, setOrderType] = useState<"DINE_IN" | "PICKUP" | "DELIVERY">("DINE_IN");
  const [tableId, setTableId] = useState("");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [submittingOrder, setSubmittingOrder] = useState(false);
  const [message, setMessage] = useState("");
  const [orderFieldErrors, setOrderFieldErrors] = useState<{
    cart?: string;
    tableId?: string;
    deliveryAddress?: string;
  }>({});

  const [createdOrderId, setCreatedOrderId] = useState("");
  const [paymentId, setPaymentId] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("MOBILE_MONEY");
  const [momoPhone, setMomoPhone] = useState("");
  const [submittingPayment, setSubmittingPayment] = useState(false);
  const [paymentRecord, setPaymentRecord] = useState<PaymentRecord | null>(null);
  const [paymentFieldErrors, setPaymentFieldErrors] = useState<{
    createdOrderId?: string;
    momoPhone?: string;
  }>({});

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

  useEffect(() => {
    if (!session || !paymentId) return;
    const timer = window.setInterval(() => {
      authenticatedFetch((activeSession) => getPayment(activeSession, paymentId))
        .then((payment) => {
          setPaymentRecord(payment);
        })
        .catch(() => {
          // Poll failures are non-fatal while payment is in-flight.
        });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [authenticatedFetch, paymentId, session]);

  const total = useMemo(() => cartTotal(cart), [cart]);
  const dineInTables = tables.filter((table) => table.status !== "CLOSED");

  async function handleCreateOrder() {
    if (!session) return;
    const nextFieldErrors: { cart?: string; tableId?: string; deliveryAddress?: string } = {};
    if (cart.length === 0) {
      nextFieldErrors.cart = "Add at least one item";
    }
    if (orderType === "DINE_IN" && !tableId) {
      nextFieldErrors.tableId = "Select a table for dine-in";
    }
    if (orderType === "DELIVERY" && !deliveryAddress.trim()) {
      nextFieldErrors.deliveryAddress = "Delivery address is required";
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setOrderFieldErrors(nextFieldErrors);
      return;
    }

    setSubmittingOrder(true);
    setMessage("");
    setOrderFieldErrors({});
    try {
      const payload = buildOrderPayload(orderType, cart, tableId || undefined);
      const created = await authenticatedFetch((activeSession) => createOrder(activeSession, {
        ...payload,
        deliveryAddress: orderType === "DELIVERY" ? deliveryAddress.trim() : undefined,
      }));

      setCreatedOrderId(created.id);
      setPaymentId("");
      setPaymentRecord(null);
      setCart([]);
      setDeliveryAddress("");
      if (orderType !== "DINE_IN") {
        setTableId("");
      }
      setMessage(`Order ${created.id.slice(0, 8)} created. Proceed to payment below.`);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Could not create order");
    } finally {
      setSubmittingOrder(false);
    }
  }

  async function handleInitiatePayment() {
    if (!session) return;
    const nextFieldErrors: { createdOrderId?: string; momoPhone?: string } = {};
    if (!createdOrderId.trim()) {
      nextFieldErrors.createdOrderId = "Order ID is required";
    }
    if (!momoPhone.trim()) {
      nextFieldErrors.momoPhone = "Customer phone is required";
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setPaymentFieldErrors(nextFieldErrors);
      return;
    }

    setSubmittingPayment(true);
    setMessage("");
    setPaymentFieldErrors({});
    try {
      const initiated = await authenticatedFetch((activeSession) =>
        initiatePayment(activeSession, {
          orderId: createdOrderId.trim(),
          method: paymentMethod,
          momoPhone: momoPhone.trim(),
          idempotencyKey: `pos-${createdOrderId}-${Date.now()}`,
        }),
      );
      setPaymentId(initiated.paymentId);
      setMessage(`Payment ${initiated.paymentId.slice(0, 8)} initiated with status ${initiated.status}.`);
      const payment = await authenticatedFetch((activeSession) => getPayment(activeSession, initiated.paymentId));
      setPaymentRecord(payment);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Could not initiate payment");
    } finally {
      setSubmittingPayment(false);
    }
  }

  async function handleVerifyPayment() {
    if (!session || !paymentId) return;
    setSubmittingPayment(true);
    setMessage("");
    try {
      const verified = await authenticatedFetch((activeSession) => verifyPayment(activeSession, paymentId));
      setPaymentRecord(verified);
      setMessage(`Payment status: ${verified.status}`);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Could not verify payment");
    } finally {
      setSubmittingPayment(false);
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
        <p className="kicker">Checkout</p>
        <h2 className="text-xl font-semibold">Create Order</h2>
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
                  <option key={table.id} value={table.id}>{table.number} - {table.status}</option>
                ))}
              </select>
              {orderFieldErrors.tableId ? <span className="text-xs text-[#991b1b]">{orderFieldErrors.tableId}</span> : null}
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
              {orderFieldErrors.deliveryAddress ? <span className="text-xs text-[#991b1b]">{orderFieldErrors.deliveryAddress}</span> : null}
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
            {orderFieldErrors.cart ? <span className="text-xs text-[#991b1b]">{orderFieldErrors.cart}</span> : null}
          </div>

          <div className="rounded-xl bg-[#eef6ea] p-4">
            <p className="kicker">Total</p>
            <p className="mt-2 text-3xl font-semibold">GHS {total.toFixed(2)}</p>
          </div>

          <button className="rounded-full bg-[#132018] px-4 py-3 text-white disabled:opacity-60" disabled={submittingOrder} onClick={() => void handleCreateOrder()}>
            {submittingOrder ? "Creating..." : "Create Order"}
          </button>

          <div className="mt-2 rounded-xl border border-[#cfe0c8] p-3">
            <p className="text-sm font-semibold text-[#132018]">Payment Flow</p>
            <label className="mt-2 grid gap-1 text-sm text-[#35523d]">
              <span>Order ID</span>
              <input
                className="rounded-xl border border-[#cfe0c8] p-2"
                value={createdOrderId}
                onChange={(event) => setCreatedOrderId(event.target.value)}
                placeholder="Order ID from Create Order"
              />
              {paymentFieldErrors.createdOrderId ? <span className="text-xs text-[#991b1b]">{paymentFieldErrors.createdOrderId}</span> : null}
            </label>
            <label className="mt-2 grid gap-1 text-sm text-[#35523d]">
              <span>Payment Method</span>
              <select className="rounded-xl border border-[#cfe0c8] p-2" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}>
                <option value="MOBILE_MONEY">MOBILE_MONEY</option>
                <option value="CARD">CARD</option>
                <option value="CASH">CASH</option>
              </select>
            </label>
            <label className="mt-2 grid gap-1 text-sm text-[#35523d]">
              <span>Customer Phone</span>
              <input
                className="rounded-xl border border-[#cfe0c8] p-2"
                value={momoPhone}
                onChange={(event) => setMomoPhone(event.target.value)}
                placeholder="+233..."
              />
              {paymentFieldErrors.momoPhone ? <span className="text-xs text-[#991b1b]">{paymentFieldErrors.momoPhone}</span> : null}
            </label>
            <div className="mt-3 flex flex-wrap gap-2">
              <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" disabled={submittingPayment} onClick={() => void handleInitiatePayment()}>
                {submittingPayment ? "Working..." : "Initiate Payment"}
              </button>
              <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" disabled={!paymentId || submittingPayment} onClick={() => void handleVerifyPayment()}>
                Verify Payment
              </button>
            </div>
            {paymentRecord ? (
              <div className="mt-3 rounded-xl bg-[#f6fbf4] p-3 text-sm">
                <p><span className="font-semibold">Payment:</span> {paymentRecord.id}</p>
                <p><span className="font-semibold">Status:</span> {paymentRecord.status}</p>
                <p><span className="font-semibold">Amount:</span> {paymentRecord.currency} {paymentRecord.amount}</p>
              </div>
            ) : null}
          </div>

          {message ? <p className="rounded-xl bg-[#f3f4f6] px-4 py-3 text-sm text-[#35523d]">{message}</p> : null}
        </div>
      </aside>
    </main>
  );
}
