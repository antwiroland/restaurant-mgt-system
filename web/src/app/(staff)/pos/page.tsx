"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
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

const ORDER_TYPES = [
  { value: "DINE_IN", label: "DINE IN", available: true },
  { value: "PICKUP", label: "PICKUP", available: true },
  { value: "DELIVERY", label: "DELIVERY", available: false, reason: "Delivery ordering is not available yet." },
] as const;
const PAYMENT_METHODS: PaymentMethod[] = ["MOBILE_MONEY", "CARD", "CASH"];

export default function PosPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [menuQuery, setMenuQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");

  const [orderType, setOrderType] = useState<(typeof ORDER_TYPES)[number]["value"]>("DINE_IN");
  const [tableId, setTableId] = useState("");
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
  const [paymentHighlight, setPaymentHighlight] = useState(false);
  const [paymentFieldErrors, setPaymentFieldErrors] = useState<{
    createdOrderId?: string;
    momoPhone?: string;
  }>({});
  const paymentSectionRef = useRef<HTMLDivElement | null>(null);

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
  }, [session]);

  useEffect(() => {
    if (!session || !paymentId) return;
    const timer = window.setInterval(() => {
      authenticatedFetch((activeSession) => getPayment(activeSession, paymentId))
        .then((payment) => {
          setPaymentRecord(payment);
        })
        .catch(() => {
          // Keep current view while polling.
        });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [paymentId, session]);

  const categories = useMemo(() => {
    return ["ALL", ...Array.from(new Set(menuItems.map((item) => item.categoryName))).sort((a, b) => a.localeCompare(b))];
  }, [menuItems]);

  const filteredMenuItems = useMemo(() => {
    const normalizedQuery = menuQuery.trim().toLowerCase();
    return menuItems.filter((item) => {
      const matchesCategory = categoryFilter === "ALL" || item.categoryName === categoryFilter;
      const matchesQuery = !normalizedQuery
        || item.name.toLowerCase().includes(normalizedQuery)
        || item.categoryName.toLowerCase().includes(normalizedQuery)
        || (item.description?.toLowerCase().includes(normalizedQuery) ?? false);
      return matchesCategory && matchesQuery;
    }).sort((a, b) => a.categoryName.localeCompare(b.categoryName) || a.name.localeCompare(b.name));
  }, [menuItems, menuQuery, categoryFilter]);

  const total = useMemo(() => cartTotal(cart), [cart]);
  const dineInTables = tables.filter((table) => table.status !== "CLOSED");

  async function handleCreateOrder() {
    if (!session) return;
    const nextFieldErrors: { cart?: string; tableId?: string; deliveryAddress?: string } = {};
    if (orderType === "DELIVERY") {
      setMessage("Delivery ordering is not available yet. Use dine-in or pickup.");
      return;
    }
    if (cart.length === 0) nextFieldErrors.cart = "Add at least one item";
    if (orderType === "DINE_IN" && !tableId) nextFieldErrors.tableId = "Select a table for dine-in";

    if (Object.keys(nextFieldErrors).length > 0) {
      setOrderFieldErrors(nextFieldErrors);
      return;
    }

    setSubmittingOrder(true);
    setMessage("");
    setOrderFieldErrors({});

    try {
      const payload = buildOrderPayload(orderType, cart, tableId || undefined);
      const created = await authenticatedFetch((activeSession) => createOrder(activeSession, payload));

      setCreatedOrderId(created.id);
      setPaymentId("");
      setPaymentRecord(null);
      setCart([]);
      if (orderType !== "DINE_IN") setTableId("");
      setMessage(`Order ${created.id.slice(0, 8)} created. Proceed to payment.`);

      setPaymentHighlight(true);
      window.setTimeout(() => setPaymentHighlight(false), 1400);
      paymentSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Could not create order");
    } finally {
      setSubmittingOrder(false);
    }
  }

  async function handleInitiatePayment() {
    if (!session) return;
    const nextFieldErrors: { createdOrderId?: string; momoPhone?: string } = {};
    if (!createdOrderId.trim()) nextFieldErrors.createdOrderId = "Order ID is required";
    if (paymentMethod === "MOBILE_MONEY" && !momoPhone.trim()) nextFieldErrors.momoPhone = "Customer phone is required for mobile money";

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
    return (
      <main className="shell grid gap-4 md:grid-cols-[1.4fr,1fr]">
        <section className="panel grid gap-3">
          <Skeleton className="h-6 w-40" />
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} className="h-20 w-full" />
          ))}
        </section>
        <aside className="panel grid gap-3">
          <Skeleton className="h-6 w-36" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-48 w-full" />
        </aside>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">POS</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 lg:grid-cols-[1.45fr,1fr]">
      <section className="panel">
        <p className="kicker">POS</p>
        <h1 className="text-2xl font-semibold">Menu</h1>

        <div className="mt-4 grid gap-3">
          <input
            className="input"
            placeholder="Search items..."
            value={menuQuery}
            onChange={(event) => setMenuQuery(event.target.value)}
          />

          <div className="-mx-4 overflow-x-auto border-y border-line bg-surface px-4 py-2">
            <div className="flex min-w-max gap-2">
              {categories.map((category) => (
                <button
                  key={category}
                  type="button"
                  className={`btn btn-sm ${categoryFilter === category ? "btn-primary" : "btn-secondary"}`}
                  onClick={() => setCategoryFilter(category)}
                >
                  {category}
                </button>
              ))}
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {filteredMenuItems.map((item) => (
              <button
                key={item.id}
                className="card card-interactive min-h-[120px] text-left"
                onClick={() => setCart((current) => addToCart(current, {
                  id: item.id,
                  name: item.name,
                  price: Number.parseFloat(item.price),
                }))}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="text-base font-semibold text-ink">{item.name}</p>
                  <span className="badge badge-info">GHS {item.price}</span>
                </div>
                <p className="mt-2 text-sm text-ink-soft">{item.categoryName}</p>
                {item.description ? <p className="mt-1 text-xs text-ink-soft line-clamp-2">{item.description}</p> : null}
              </button>
            ))}
            {filteredMenuItems.length === 0 ? (
              <div className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">
                No items match the current filters.
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <aside className="panel flex flex-col">
        <p className="kicker">Order</p>
        <h2 className="text-xl font-semibold">Checkout</h2>

        <div className="mt-4 flex-1 space-y-4 overflow-y-auto pr-1">
          <div className="grid gap-2">
            <p className="field-label">Order Type</p>
            <div className="grid grid-cols-3 gap-2">
              {ORDER_TYPES.map((type) => (
                <button
                  key={type.value}
                  type="button"
                  className={`btn btn-md ${orderType === type.value ? "btn-primary" : "btn-secondary"} ${!type.available ? "opacity-60" : ""}`}
                  onClick={() => {
                    if (!type.available) {
                      setMessage(type.reason ?? "This order type is unavailable.");
                      return;
                    }
                    setOrderType(type.value);
                    setMessage("");
                  }}
                  aria-disabled={!type.available}
                >
                  {type.label}
                </button>
              ))}
            </div>
            {!ORDER_TYPES.find((type) => type.value === orderType)?.available ? (
              <p className="alert alert-info">Delivery ordering is currently disabled in the staff web app.</p>
            ) : null}
            <p className="text-xs text-ink-soft">Delivery remains visible only as a roadmap item. The web flow is intentionally blocked until the full customer/dispatch experience is ready.</p>
          </div>

          {orderType === "DINE_IN" ? (
            <label className="field">
              <span className="field-label">Table</span>
              <select className="select" value={tableId} onChange={(event) => setTableId(event.target.value)}>
                <option value="">Select a table</option>
                {dineInTables.map((table) => (
                  <option key={table.id} value={table.id}>{table.number} - {table.status}</option>
                ))}
              </select>
              {orderFieldErrors.tableId ? <span className="field-error">{orderFieldErrors.tableId}</span> : null}
            </label>
          ) : null}

          <div className="grid gap-2">
            <p className="field-label">Cart</p>
            {cart.length === 0 ? (
              <p className="rounded-xl border border-dashed border-line p-4 text-sm text-ink-soft">Select items from the menu to build an order.</p>
            ) : (
              cart.map((line) => (
                <div key={line.item.id} className="card p-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-medium text-ink">{line.item.name}</span>
                    <span className="text-sm text-ink-soft">GHS {(line.item.price * line.quantity).toFixed(2)}</span>
                  </div>
                  <div className="mt-3 flex items-center gap-2">
                    <button className="btn btn-secondary btn-md w-12 text-lg" onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity - 1))}>-</button>
                    <span className="min-w-8 text-center text-base font-semibold">{line.quantity}</span>
                    <button className="btn btn-secondary btn-md w-12 text-lg" onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity + 1))}>+</button>
                    <button className="ml-auto btn btn-danger btn-sm" onClick={() => setCart((current) => removeFromCart(current, line.item.id))}>Remove</button>
                  </div>
                </div>
              ))
            )}
            {orderFieldErrors.cart ? <span className="field-error">{orderFieldErrors.cart}</span> : null}
          </div>

          <div
            ref={paymentSectionRef}
            className={`rounded-xl border p-3 transition ${paymentHighlight ? "border-line-focus bg-brand-subtle" : "border-line bg-surface"}`}
          >
            <p className="text-sm font-semibold text-ink">Payment</p>
            <label className="field mt-2">
              <span className="field-label">Order ID</span>
              <input className="input" value={createdOrderId} onChange={(event) => setCreatedOrderId(event.target.value)} placeholder="Order ID from Create Order" />
              {paymentFieldErrors.createdOrderId ? <span className="field-error">{paymentFieldErrors.createdOrderId}</span> : null}
            </label>

            <div className="mt-3 grid gap-2">
              <p className="field-label">Payment Method</p>
              <div className="grid grid-cols-3 gap-2">
                {PAYMENT_METHODS.map((method) => (
                  <button
                    key={method}
                    type="button"
                    className={`btn btn-md ${paymentMethod === method ? "btn-primary" : "btn-secondary"}`}
                    onClick={() => setPaymentMethod(method)}
                  >
                    {method === "MOBILE_MONEY" ? "MoMo" : method}
                  </button>
                ))}
              </div>
            </div>

            <label className="field mt-3">
              <span className="field-label">Customer Phone</span>
              <input className="input" value={momoPhone} onChange={(event) => setMomoPhone(event.target.value)} placeholder="+233..." />
              {paymentFieldErrors.momoPhone ? <span className="field-error">{paymentFieldErrors.momoPhone}</span> : null}
            </label>

            <div className="mt-3 flex flex-wrap gap-2">
              <button className="btn btn-secondary btn-md" disabled={submittingPayment} onClick={() => void handleInitiatePayment()}>
                {submittingPayment ? <Spinner className="text-current" /> : null}
                Initiate Payment
              </button>
              <button className="btn btn-secondary btn-md" disabled={!paymentId || submittingPayment} onClick={() => void handleVerifyPayment()}>
                Verify Payment
              </button>
            </div>

            {paymentRecord ? (
              <div className="mt-3 rounded-xl bg-muted p-3 text-sm">
                <p><span className="font-semibold">Payment:</span> {paymentRecord.id}</p>
                <p><span className="font-semibold">Status:</span> {paymentRecord.status}</p>
                <p><span className="font-semibold">Amount:</span> {paymentRecord.currency} {paymentRecord.amount}</p>
              </div>
            ) : null}
          </div>
        </div>

        <div className="sticky bottom-0 mt-4 -mx-4 border-t border-line bg-surface px-4 pt-3 pb-1">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="kicker">Total</p>
              <p className="text-2xl font-semibold text-ink">GHS {total.toFixed(2)}</p>
            </div>
            <button className="btn btn-primary btn-lg" disabled={submittingOrder} onClick={() => void handleCreateOrder()}>
              {submittingOrder ? <Spinner className="text-current" /> : null}
              Create Order
            </button>
          </div>
        </div>

        {message ? <p className="alert alert-info mt-3">{message}</p> : null}
      </aside>
    </main>
  );
}
