"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import {
  addToCart,
  buildOrderPayload,
  cartTotal,
  removeFromCart,
  setQuantity,
  type CartLine,
} from "@/features/pos/cart";
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
  { value: "DINE_IN", label: "Dine In", available: true, note: "Assign ticket to a live table." },
  { value: "PICKUP", label: "Pickup", available: true, note: "Quick counter handoff." },
  { value: "DELIVERY", label: "Delivery", available: false, note: "Dispatch workflow not enabled yet." },
] as const;

const PAYMENT_METHODS: Array<{ value: PaymentMethod; label: string; hint: string }> = [
  { value: "MOBILE_MONEY", label: "MoMo", hint: "Customer approves on phone" },
  { value: "CARD", label: "Card", hint: "Use terminal or gateway" },
  { value: "CASH", label: "Cash", hint: "Tender collected at counter" },
];

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
  }, [authenticatedFetch, session]);

  useEffect(() => {
    if (!session || !paymentId) return;
    const timer = window.setInterval(() => {
      authenticatedFetch((activeSession) => getPayment(activeSession, paymentId))
        .then((payment) => {
          setPaymentRecord(payment);
        })
        .catch(() => {
          // Keep the current view if polling fails temporarily.
        });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [authenticatedFetch, paymentId, session]);

  const categories = useMemo(() => {
    return ["ALL", ...Array.from(new Set(menuItems.map((item) => item.categoryName))).sort((a, b) => a.localeCompare(b))];
  }, [menuItems]);

  const filteredMenuItems = useMemo(() => {
    const normalizedQuery = menuQuery.trim().toLowerCase();
    return menuItems
      .filter((item) => {
        const matchesCategory = categoryFilter === "ALL" || item.categoryName === categoryFilter;
        const matchesQuery = !normalizedQuery
          || item.name.toLowerCase().includes(normalizedQuery)
          || item.categoryName.toLowerCase().includes(normalizedQuery)
          || (item.description?.toLowerCase().includes(normalizedQuery) ?? false);
        return matchesCategory && matchesQuery;
      })
      .sort((a, b) => a.categoryName.localeCompare(b.categoryName) || a.name.localeCompare(b.name));
  }, [categoryFilter, menuItems, menuQuery]);

  const groupedMenuItems = useMemo(() => {
    const groups = new Map<string, MenuItemRecord[]>();
    for (const item of filteredMenuItems) {
      const existing = groups.get(item.categoryName) ?? [];
      existing.push(item);
      groups.set(item.categoryName, existing);
    }
    return Array.from(groups.entries());
  }, [filteredMenuItems]);

  const total = useMemo(() => cartTotal(cart), [cart]);
  const cartCount = useMemo(() => cart.reduce((sum, line) => sum + line.quantity, 0), [cart]);
  const dineInTables = useMemo(() => tables.filter((table) => table.status !== "CLOSED"), [tables]);
  const availableTables = useMemo(() => tables.filter((table) => table.status === "AVAILABLE").length, [tables]);
  const selectedTable = useMemo(() => tables.find((table) => table.id === tableId) ?? null, [tableId, tables]);
  const activeOrderType = ORDER_TYPES.find((type) => type.value === orderType) ?? ORDER_TYPES[0];
  const paymentToneClass = paymentRecord ? paymentStatusClass(paymentRecord.status) : "badge-neutral";

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
      <main className="shell grid gap-4 xl:grid-cols-[1.35fr,0.9fr,0.75fr]">
        {Array.from({ length: 3 }).map((_, index) => (
          <section key={index} className="panel grid gap-3">
            <Skeleton className="h-6 w-40" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-32 w-full" />
          </section>
        ))}
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
    <main className="shell gap-5">
      <section className="panel overflow-hidden p-0">
        <div className="border-b border-line bg-[linear-gradient(135deg,rgba(5,150,105,0.14),rgba(217,119,6,0.1))] px-5 py-5">
          <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
            <div className="space-y-2">
              <p className="kicker">POS Terminal</p>
              <div>
                <h1 className="text-3xl font-semibold tracking-tight text-ink">Counter Service Workstation</h1>
                <p className="max-w-2xl text-sm text-ink-soft">
                  Build tickets on the left, manage the live order in the center, then collect payment in the cashier rail.
                </p>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-3 xl:min-w-[540px]">
              <SummaryCard label="Items Ready to Sell" value={String(menuItems.length)} hint={`${categories.length - 1} menu categories`} tone="emerald" />
              <SummaryCard label="Open Tables" value={`${availableTables}/${tables.length || 0}`} hint="Available right now" tone="sky" />
              <SummaryCard label="Current Ticket" value={`${cartCount} item${cartCount === 1 ? "" : "s"}`} hint={`GHS ${total.toFixed(2)}`} tone="amber" />
            </div>
          </div>
        </div>
        {message ? (
          <div className="border-t border-line px-5 py-3">
            <p className="alert alert-info">{message}</p>
          </div>
        ) : null}
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,0.9fr)_minmax(300px,0.75fr)]">
        <section className="panel flex min-h-[72dvh] flex-col overflow-hidden p-0">
          <div className="border-b border-line bg-surface px-5 py-4">
            <div className="flex flex-col gap-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="kicker">Menu Browser</p>
                  <h2 className="text-xl font-semibold text-ink">Tap to add items</h2>
                </div>
                <div className="rounded-2xl border border-line bg-sunken px-3 py-2 text-right">
                  <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">Showing</p>
                  <p className="text-base font-semibold text-ink">{filteredMenuItems.length} items</p>
                </div>
              </div>
              <div className="flex flex-col gap-3">
                <input
                  className="input"
                  placeholder="Search menu, category, or description"
                  value={menuQuery}
                  onChange={(event) => setMenuQuery(event.target.value)}
                />
                <div className="flex flex-wrap gap-2">
                  {categories.map((category) => (
                    <button
                      key={category}
                      type="button"
                      className={`btn btn-sm shrink-0 ${categoryFilter === category ? "btn-primary" : "btn-secondary"}`}
                      onClick={() => setCategoryFilter(category)}
                    >
                      {category}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-5 py-5">
            {groupedMenuItems.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-line p-6 text-center">
                <p className="text-lg font-semibold text-ink">No items match this search</p>
                <p className="mt-2 text-sm text-ink-soft">Try another category or clear the search terms.</p>
              </div>
            ) : (
              <div className="space-y-6">
                {groupedMenuItems.map(([category, items]) => (
                  <section key={category} className="space-y-3">
                    <div className="flex items-center justify-between gap-3 border-b border-line pb-2">
                      <div>
                        <h3 className="text-lg font-semibold text-ink">{category}</h3>
                        <p className="text-xs uppercase tracking-[0.16em] text-ink-soft">{items.length} available</p>
                      </div>
                    </div>
                    <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
                      {items.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          className="card card-interactive flex min-h-[168px] flex-col justify-between text-left"
                          onClick={() => setCart((current) => addToCart(current, {
                            id: item.id,
                            name: item.name,
                            price: Number.parseFloat(item.price),
                          }))}
                        >
                          <div className="space-y-2">
                            <div className="flex items-start justify-between gap-3">
                              <p className="text-lg font-semibold leading-snug text-ink">{item.name}</p>
                              <span className="badge badge-info whitespace-nowrap">GHS {item.price}</span>
                            </div>
                            {item.description ? (
                              <p className="text-sm leading-relaxed text-ink-soft line-clamp-3">{item.description}</p>
                            ) : (
                              <p className="text-sm text-ink-soft">Fast add item for {category.toLowerCase()} service.</p>
                            )}
                          </div>
                          <div className="mt-4 flex items-center justify-between gap-3 border-t border-line pt-3">
                            <span className="text-xs uppercase tracking-[0.18em] text-ink-soft">Add to ticket</span>
                            <span className="text-base font-semibold text-brand">Tap item</span>
                          </div>
                        </button>
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            )}
          </div>
        </section>

        <section className="panel flex min-h-[72dvh] flex-col overflow-hidden p-0">
          <div className="border-b border-line bg-surface px-5 py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="kicker">Current Ticket</p>
                <h2 className="text-xl font-semibold text-ink">Build the order</h2>
              </div>
              <div className="rounded-2xl border border-line bg-sunken px-3 py-2 text-right">
                <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">Subtotal</p>
                <p className="text-xl font-semibold text-ink">GHS {total.toFixed(2)}</p>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-5 py-5">
            <div className="space-y-5">
              <div className="rounded-2xl border border-line bg-sunken p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="kicker">Service Mode</p>
                    <h3 className="text-lg font-semibold text-ink">{activeOrderType.label}</h3>
                    <p className="mt-1 text-sm text-ink-soft">{activeOrderType.note}</p>
                  </div>
                  <span className="badge badge-neutral">{cartCount} item{cartCount === 1 ? "" : "s"}</span>
                </div>

                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  {ORDER_TYPES.map((type) => (
                    <button
                      key={type.value}
                      type="button"
                      className={`card min-w-0 text-left ${orderType === type.value ? "border-brand bg-brand-subtle ring-1 ring-brand/30" : "border-line bg-surface"} ${!type.available ? "opacity-60" : ""}`}
                      onClick={() => {
                        if (!type.available) {
                          setMessage(type.note);
                          return;
                        }
                        setOrderType(type.value);
                        setMessage("");
                      }}
                      aria-disabled={!type.available}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-base font-semibold text-ink">{type.label}</p>
                          <p className="mt-1 text-sm text-ink-soft">{type.note}</p>
                        </div>
                        <span
                          className={`mt-1 h-4 w-4 shrink-0 rounded-full border ${orderType === type.value ? "border-brand bg-brand" : "border-line-strong bg-white"}`}
                          aria-hidden="true"
                        />
                      </div>
                    </button>
                  ))}
                </div>

                {!activeOrderType.available ? (
                  <p className="alert alert-info mt-4">Delivery remains visible as a roadmap item. Staff checkout is intentionally blocked for now.</p>
                ) : null}
              </div>

              {orderType === "DINE_IN" ? (
                <div className="rounded-2xl border border-line bg-surface p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="kicker">Table Assignment</p>
                      <h3 className="text-lg font-semibold text-ink">Select a live table</h3>
                    </div>
                    <span className={`badge ${selectedTable ? tableStatusClass(selectedTable.status) : "badge-neutral"}`}>
                      {selectedTable ? selectedTable.status : "Required"}
                    </span>
                  </div>
                  <label className="field mt-4">
                    <span className="field-label">Table</span>
                    <select className="select" value={tableId} onChange={(event) => setTableId(event.target.value)}>
                      <option value="">Select a table</option>
                      {dineInTables.map((table) => (
                        <option key={table.id} value={table.id}>
                          {`${table.number} | ${table.zone} | ${table.status}`}
                        </option>
                      ))}
                    </select>
                    {orderFieldErrors.tableId ? <span className="field-error">{orderFieldErrors.tableId}</span> : null}
                  </label>
                  {selectedTable ? (
                    <div className="mt-4 grid gap-3 sm:grid-cols-3">
                      <InfoTile label="Number" value={selectedTable.number} />
                      <InfoTile label="Zone" value={selectedTable.zone} />
                      <InfoTile label="Capacity" value={`${selectedTable.capacity} seats`} />
                    </div>
                  ) : null}
                </div>
              ) : null}

              <div className="space-y-3">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="kicker">Ticket Lines</p>
                    <h3 className="text-lg font-semibold text-ink">Order items</h3>
                  </div>
                  {cart.length > 0 ? <span className="badge badge-neutral">{cartCount} units</span> : null}
                </div>
                {cart.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-line p-6 text-center">
                    <p className="text-lg font-semibold text-ink">Ticket is empty</p>
                    <p className="mt-2 text-sm text-ink-soft">Choose items from the menu browser to start an order.</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {cart.map((line) => (
                      <div key={line.item.id} className="rounded-2xl border border-line bg-surface p-4">
                        <div className="flex items-start justify-between gap-3">
                          <div className="space-y-1">
                            <p className="text-base font-semibold text-ink">{line.item.name}</p>
                            <p className="text-sm text-ink-soft">GHS {line.item.price.toFixed(2)} each</p>
                          </div>
                          <div className="text-right">
                            <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">Line Total</p>
                            <p className="text-lg font-semibold text-ink">GHS {(line.item.price * line.quantity).toFixed(2)}</p>
                          </div>
                        </div>
                        <div className="mt-4 flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              className="btn btn-secondary btn-lg w-11 text-lg"
                              onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity - 1))}
                            >
                              -
                            </button>
                            <span className="w-10 rounded-xl border border-line bg-sunken py-3 text-center text-lg font-semibold text-ink">
                              {line.quantity}
                            </span>
                            <button
                              type="button"
                              className="btn btn-secondary btn-lg w-11 text-lg"
                              onClick={() => setCart((current) => setQuantity(current, line.item.id, line.quantity + 1))}
                            >
                              +
                            </button>
                          </div>
                          <button
                            type="button"
                            className="btn btn-danger btn-sm"
                            onClick={() => setCart((current) => removeFromCart(current, line.item.id))}
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                {orderFieldErrors.cart ? <span className="field-error">{orderFieldErrors.cart}</span> : null}
              </div>
            </div>
          </div>

          <div className="border-t border-line bg-[linear-gradient(180deg,#ffffff,rgba(236,253,245,0.8))] px-5 py-4">
            <div className="flex flex-col gap-4">
              <div className="flex items-end justify-between gap-3">
                <div className="grid gap-3 sm:grid-cols-2">
                  <InfoTile label="Service" value={activeOrderType.label} />
                  <InfoTile label="Table" value={orderType === "DINE_IN" ? selectedTable?.number ?? "Not selected" : "Not required"} />
                </div>
                <div className="text-right">
                  <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">Ticket total</p>
                  <p className="text-3xl font-semibold tracking-tight text-ink">GHS {total.toFixed(2)}</p>
                </div>
              </div>
              <button className="btn btn-accent btn-lg w-full" disabled={submittingOrder} onClick={() => void handleCreateOrder()}>
                {submittingOrder ? <Spinner className="text-current" /> : null}
                Create Order
              </button>
            </div>
          </div>
        </section>

        <aside
          ref={paymentSectionRef}
          className={`panel flex min-h-[72dvh] flex-col overflow-hidden p-0 transition ${paymentHighlight ? "border-line-focus shadow-card" : ""}`}
        >
          <div className="border-b border-line bg-surface px-5 py-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="kicker">Cashier Rail</p>
                <h2 className="text-xl font-semibold text-ink">Payment Terminal</h2>
                <p className="mt-1 text-sm text-ink-soft">Attach payment to the latest order and verify status before handing off.</p>
              </div>
              <span className={`badge ${paymentToneClass}`}>{paymentRecord?.status ?? "Awaiting"}</span>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-5 py-5">
            <div className="space-y-5">
              <div className="rounded-2xl border border-line bg-sunken p-4">
                <p className="kicker">Active Order</p>
                <div className="mt-3 space-y-3">
                  <label className="field">
                    <span className="field-label">Order ID</span>
                    <input
                      className="input"
                      value={createdOrderId}
                      onChange={(event) => setCreatedOrderId(event.target.value)}
                      placeholder="Order ID from Create Order"
                    />
                    {paymentFieldErrors.createdOrderId ? <span className="field-error">{paymentFieldErrors.createdOrderId}</span> : null}
                  </label>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <InfoTile label="Linked order" value={createdOrderId ? createdOrderId.slice(0, 8) : "Not set"} />
                    <InfoTile label="Ticket amount" value={`GHS ${total.toFixed(2)}`} />
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                <div>
                  <p className="kicker">Tender Type</p>
                  <h3 className="text-lg font-semibold text-ink">Choose payment method</h3>
                </div>
                <div className="grid gap-2">
                  {PAYMENT_METHODS.map((method) => (
                    <button
                      key={method.value}
                      type="button"
                      className={`card text-left ${paymentMethod === method.value ? "border-brand bg-brand-subtle" : "border-line bg-surface"}`}
                      onClick={() => setPaymentMethod(method.value)}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-base font-semibold text-ink">{method.label}</p>
                          <p className="mt-1 text-sm text-ink-soft">{method.hint}</p>
                        </div>
                        <span className={`mt-1 h-4 w-4 rounded-full border ${paymentMethod === method.value ? "border-brand bg-brand" : "border-line-strong bg-white"}`} />
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {paymentMethod === "MOBILE_MONEY" ? (
                <label className="field">
                  <span className="field-label">Customer phone</span>
                  <input
                    className="input"
                    inputMode="tel"
                    value={momoPhone}
                    onChange={(event) => setMomoPhone(event.target.value)}
                    placeholder="+233..."
                  />
                  <span className="field-hint">Use the payer's mobile money number.</span>
                  {paymentFieldErrors.momoPhone ? <span className="field-error">{paymentFieldErrors.momoPhone}</span> : null}
                </label>
              ) : null}

              <div className="rounded-2xl border border-line bg-surface p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="kicker">Provider Status</p>
                    <h3 className="text-lg font-semibold text-ink">Live payment state</h3>
                  </div>
                  <span className={`badge ${paymentToneClass}`}>{paymentRecord?.status ?? "No payment"}</span>
                </div>

                {paymentRecord ? (
                  <div className="mt-4 grid gap-3">
                    <InfoTile label="Payment ID" value={paymentRecord.id.slice(0, 8)} />
                    <InfoTile label="Amount" value={`${paymentRecord.currency} ${paymentRecord.amount}`} />
                    <InfoTile label="Method" value={paymentRecord.method === "MOBILE_MONEY" ? "MoMo" : paymentRecord.method} />
                    <InfoTile label="Created" value={formatDateTime(paymentRecord.createdAt)} />
                    {paymentRecord.providerMessage ? (
                      <div className="rounded-xl border border-info-border bg-info-subtle p-3 text-sm text-info-on">
                        {paymentRecord.providerMessage}
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <p className="mt-4 text-sm text-ink-soft">No payment has been initiated for the current order yet.</p>
                )}
              </div>

              <div className="rounded-2xl border border-line bg-sunken p-4">
                <p className="kicker">Cashier Notes</p>
                <ul className="mt-3 space-y-2 text-sm text-ink-soft">
                  <li>1. Create the ticket after confirming items and table.</li>
                  <li>2. Initiate payment from this rail using the created order ID.</li>
                  <li>3. Verify pending payments before closing the guest interaction.</li>
                </ul>
              </div>
            </div>
          </div>

          <div className="border-t border-line bg-[linear-gradient(180deg,#ffffff,rgba(240,249,255,0.8))] px-5 py-4">
            <div className="grid gap-3">
              <button className="btn btn-primary btn-lg btn-block" disabled={submittingPayment} onClick={() => void handleInitiatePayment()}>
                {submittingPayment ? <Spinner className="text-current" /> : null}
                Initiate Payment
              </button>
              <button className="btn btn-secondary btn-lg btn-block" disabled={!paymentId || submittingPayment} onClick={() => void handleVerifyPayment()}>
                Verify Payment
              </button>
            </div>
          </div>
        </aside>
      </section>
    </main>
  );
}

function SummaryCard({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint: string;
  tone: "emerald" | "sky" | "amber";
}) {
  const toneClasses = {
    emerald: "border-brand/20 bg-white/80",
    sky: "border-info-border bg-white/80",
    amber: "border-warning-border bg-white/80",
  } as const;

  return (
    <div className={`rounded-2xl border p-4 shadow-xs backdrop-blur ${toneClasses[tone]}`}>
      <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">{label}</p>
      <p className="mt-2 text-3xl font-semibold tracking-tight text-ink">{value}</p>
      <p className="mt-1 text-sm text-ink-soft">{hint}</p>
    </div>
  );
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl border border-line bg-white px-3 py-3">
      <p className="text-xs uppercase tracking-[0.18em] text-ink-soft">{label}</p>
      <p className="mt-1 break-words text-sm font-semibold text-ink">{value}</p>
    </div>
  );
}

function tableStatusClass(status: TableRecord["status"]): string {
  if (status === "AVAILABLE") return "badge-available";
  if (status === "OCCUPIED") return "badge-occupied";
  if (status === "RESERVED") return "badge-reserved";
  return "badge-closed";
}

function paymentStatusClass(status: PaymentRecord["status"]): string {
  if (status === "SUCCESS") return "badge-success";
  if (status === "FAILED" || status === "VOIDED") return "badge-danger";
  if (status === "REFUNDED") return "badge-neutral";
  if (status === "INITIATED" || status === "PENDING" || status === "PAYMENT_PENDING") return "badge-warning";
  return "badge-info";
}

function formatDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

