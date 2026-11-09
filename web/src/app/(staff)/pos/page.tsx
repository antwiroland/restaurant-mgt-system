"use client";

/* eslint-disable @next/next/no-img-element -- menu images are arbitrary backend/CDN URLs */
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
import { Icon, type IconName } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import {
  addToCart,
  buildOrderPayload,
  cartTotal,
  lineKey,
  removeFromCart,
  setLineNotes,
  setQuantity,
  type CartLine,
} from "@/features/pos/cart";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createOrder,
  getBillingConfig,
  getDeliveryZones,
  getMenuItemModifiers,
  getMenuItems,
  getPayment,
  getTables,
  initiatePayment,
  quoteOrder,
  sendOrderToPrinter,
  verifyPayment,
  type DeliveryZoneRecord,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
  type OrderQuoteRecord,
  type OrderRecord,
  type PaymentMethod,
  type PaymentRecord,
  type TableRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { StaffClockPanel } from "@/features/hr/attendance/StaffClockPanel";

const ORDER_TYPES = [
  { value: "DINE_IN", label: "Dine in", icon: "table" as IconName },
  { value: "PICKUP", label: "Pickup", icon: "bag" as IconName },
  { value: "DELIVERY", label: "Delivery", icon: "scooter" as IconName },
] as const;

type OrderTypeValue = (typeof ORDER_TYPES)[number]["value"];

const PAYMENT_METHODS: Array<{ value: PaymentMethod; label: string; hint: string; icon: IconName }> = [
  { value: "MOBILE_MONEY", label: "Mobile money", hint: "Customer approves on their phone", icon: "phoneMoney" },
  { value: "CARD", label: "Card", hint: "Terminal or gateway", icon: "card" },
  { value: "CASH", label: "Cash", hint: "Collected at the counter", icon: "cash" },
];

const TABLE_TONE: Record<TableRecord["status"], string> = {
  AVAILABLE: "border-available/40 bg-available-subtle text-available",
  OCCUPIED: "border-occupied/40 bg-occupied-subtle text-occupied",
  RESERVED: "border-reserved/40 bg-reserved-subtle text-reserved",
  CLOSED: "border-line bg-closed-subtle text-closed",
};

export default function PosPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [staffClockOpen, setStaffClockOpen] = useState(false);
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [menuLoaded, setMenuLoaded] = useState(false);
  const [tables, setTables] = useState<TableRecord[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [menuQuery, setMenuQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");

  const [orderType, setOrderType] = useState<OrderTypeValue>("DINE_IN");
  const [tableId, setTableId] = useState("");
  const [promoCode, setPromoCode] = useState("");
  const [orderNotes, setOrderNotes] = useState("");
  const [tipAmount, setTipAmount] = useState("");
  const [tipsEnabled, setTipsEnabled] = useState(false);
  const [showExtras, setShowExtras] = useState(false);
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [deliveryZoneId, setDeliveryZoneId] = useState("");
  const [deliveryZones, setDeliveryZones] = useState<DeliveryZoneRecord[]>([]);
  const [submittingOrder, setSubmittingOrder] = useState(false);
  const [message, setMessage] = useState<{ tone: "info" | "success" | "danger"; text: string } | null>(null);
  const [orderFieldErrors, setOrderFieldErrors] = useState<{ cart?: string; tableId?: string; deliveryAddress?: string }>({});
  const [editingNote, setEditingNote] = useState<string | null>(null);

  const [quote, setQuote] = useState<OrderQuoteRecord | null>(null);
  const [quoteState, setQuoteState] = useState<"idle" | "loading" | "error">("idle");

  // Modifier picker
  const modifierCache = useRef(new Map<string, MenuModifierGroupRecord[]>());
  const [picker, setPicker] = useState<{ item: MenuItemRecord; groups: MenuModifierGroupRecord[] } | null>(null);
  const [pickerLoadingId, setPickerLoadingId] = useState<string | null>(null);

  // Payment step
  const [stage, setStage] = useState<"ticket" | "payment">("ticket");
  const [createdOrder, setCreatedOrder] = useState<OrderRecord | null>(null);
  const [createdOrderId, setCreatedOrderId] = useState("");
  const [paymentId, setPaymentId] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("MOBILE_MONEY");
  const [momoPhone, setMomoPhone] = useState("");
  const [tendered, setTendered] = useState("");
  const [changeDue, setChangeDue] = useState<number | null>(null);
  const [submittingPayment, setSubmittingPayment] = useState(false);
  const [paymentRecord, setPaymentRecord] = useState<PaymentRecord | null>(null);
  const [paymentFieldErrors, setPaymentFieldErrors] = useState<{ createdOrderId?: string; momoPhone?: string }>({});

  const searchRef = useRef<HTMLInputElement>(null);

  /* ---------------------------- Data ---------------------------- */
  useEffect(() => {
    getBillingConfig()
      .then((config) => setTipsEnabled(config.tipsEnabled))
      .catch(() => setTipsEnabled(false));
  }, []);

  useEffect(() => {
    if (!session) return;
    authenticatedFetch(async (activeSession) => {
      const [nextMenuItems, nextTables] = await Promise.all([getMenuItems(activeSession), getTables(activeSession)]);
      setMenuItems(nextMenuItems.filter((item) => item.active && item.available));
      setTables(nextTables);
      // Deep link from the floor plan: /pos?table=<id> preselects a dine-in table.
      const linkedTable = new URLSearchParams(window.location.search).get("table");
      if (linkedTable && nextTables.some((table) => table.id === linkedTable && table.status !== "CLOSED")) {
        setOrderType("DINE_IN");
        setTableId(linkedTable);
      }
      setDeliveryZones(await getDeliveryZones().catch(() => []));
    })
      .catch((err) => setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Could not load POS data" }))
      .finally(() => setMenuLoaded(true));
  }, [authenticatedFetch, session]);

  useEffect(() => {
    if (!session || !paymentId) return;
    const timer = window.setInterval(() => {
      authenticatedFetch((activeSession) => getPayment(activeSession, paymentId))
        .then(setPaymentRecord)
        .catch(() => {
          /* keep current view if polling fails temporarily */
        });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [authenticatedFetch, paymentId, session]);

  /* ---------------------------- Derived ---------------------------- */
  const categories = useMemo(
    () => ["ALL", ...Array.from(new Set(menuItems.map((item) => item.categoryName))).sort((a, b) => a.localeCompare(b))],
    [menuItems],
  );

  const filteredMenuItems = useMemo(() => {
    const q = menuQuery.trim().toLowerCase();
    return menuItems
      .filter((item) => {
        const matchesCategory = categoryFilter === "ALL" || item.categoryName === categoryFilter;
        const matchesQuery =
          !q || item.name.toLowerCase().includes(q) || item.categoryName.toLowerCase().includes(q) || (item.description?.toLowerCase().includes(q) ?? false);
        return matchesCategory && matchesQuery;
      })
      .sort((a, b) => a.categoryName.localeCompare(b.categoryName) || a.name.localeCompare(b.name));
  }, [categoryFilter, menuItems, menuQuery]);

  const qtyByItem = useMemo(() => {
    const map = new Map<string, number>();
    for (const line of cart) map.set(line.item.id, (map.get(line.item.id) ?? 0) + line.quantity);
    return map;
  }, [cart]);

  const subtotal = useMemo(() => cartTotal(cart), [cart]);
  const cartCount = useMemo(() => cart.reduce((sum, line) => sum + line.quantity, 0), [cart]);
  const dineInTables = useMemo(
    () => tables.filter((table) => table.status !== "CLOSED").sort((a, b) => a.number.localeCompare(b.number, undefined, { numeric: true })),
    [tables],
  );
  const selectedTable = useMemo(() => tables.find((table) => table.id === tableId) ?? null, [tableId, tables]);

  const payload = useMemo(
    () => ({
      ...buildOrderPayload(orderType, cart, tableId || undefined, promoCode, orderNotes),
      ...(tipsEnabled && Number(tipAmount) > 0 ? { tipAmount: Number(Number(tipAmount).toFixed(2)) } : {}),
      ...(orderType === "DELIVERY" ? { deliveryAddress: deliveryAddress.trim() || undefined, deliveryZoneId: deliveryZoneId || undefined } : {}),
    }),
    [cart, deliveryAddress, deliveryZoneId, orderNotes, orderType, promoCode, tableId, tipAmount, tipsEnabled],
  );

  /* Live server-side pricing (discounts, fees, service charge, tax). */
  useEffect(() => {
    if (!session || cart.length === 0) {
      setQuote(null);
      setQuoteState("idle");
      return;
    }
    setQuoteState("loading");
    const handle = window.setTimeout(() => {
      authenticatedFetch((activeSession) => quoteOrder(activeSession, payload))
        .then((next) => {
          setQuote(next);
          setQuoteState("idle");
        })
        .catch(() => {
          setQuote(null);
          setQuoteState("error");
        });
    }, 350);
    return () => window.clearTimeout(handle);
  }, [authenticatedFetch, cart.length, payload, session]);

  const displayTotal = quote ? Number(quote.total) : subtotal;

  /* ---------------------------- Cart actions ---------------------------- */
  const addItem = useCallback(
    async (item: MenuItemRecord) => {
      let groups = modifierCache.current.get(item.id);
      if (!groups) {
        setPickerLoadingId(item.id);
        try {
          groups = await getMenuItemModifiers(item.id);
        } catch {
          groups = [];
        } finally {
          setPickerLoadingId(null);
        }
        modifierCache.current.set(item.id, groups);
      }
      if (groups.length > 0) {
        setPicker({ item, groups });
        return;
      }
      setCart((current) => addToCart(current, { id: item.id, name: item.name, price: Number.parseFloat(item.price) }));
      setOrderFieldErrors((errors) => ({ ...errors, cart: undefined }));
    },
    [],
  );

  // Keyboard: "/" focuses search, Esc clears it, Enter adds the only/first match.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const typing = target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT");
      if (event.key === "/" && !typing) {
        event.preventDefault();
        searchRef.current?.focus();
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  /* ---------------------------- Submit ---------------------------- */
  async function handleCreateOrder() {
    if (!session) return;
    const nextFieldErrors: { cart?: string; tableId?: string; deliveryAddress?: string } = {};
    if (orderType === "DELIVERY") {
      if (!deliveryAddress.trim()) nextFieldErrors.deliveryAddress = "Delivery address is required";
      if (deliveryZones.length > 0 && !deliveryZoneId) nextFieldErrors.deliveryAddress = "Choose a delivery zone";
    }
    if (cart.length === 0) nextFieldErrors.cart = "Add at least one item";
    if (orderType === "DINE_IN" && !tableId) nextFieldErrors.tableId = "Select a table for dine-in";

    if (Object.keys(nextFieldErrors).length > 0) {
      setOrderFieldErrors(nextFieldErrors);
      return;
    }

    setSubmittingOrder(true);
    setMessage(null);
    setOrderFieldErrors({});

    try {
      const created = await authenticatedFetch((activeSession) => createOrder(activeSession, payload));
      setCreatedOrder(created);
      setCreatedOrderId(created.id);
      setPaymentId("");
      setPaymentRecord(null);
      setCart([]);
      setPromoCode("");
      setOrderNotes("");
      setTipAmount("");
      setShowExtras(false);
      setDeliveryAddress("");
      setDeliveryZoneId("");
      if (orderType !== "DINE_IN") setTableId("");
      const discount = Number(created.discountTotal ?? 0);
      setMessage({
        tone: "success",
        text:
          discount > 0
            ? `Order #${created.id.slice(0, 8).toUpperCase()} sent to the kitchen with ${formatMoney(discount)} off.`
            : `Order #${created.id.slice(0, 8).toUpperCase()} sent to the kitchen.`,
      });
      setStage("payment");
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Could not create order" });
    } finally {
      setSubmittingOrder(false);
    }
  }

  async function handleInitiatePayment() {
    if (!session) return;
    const nextFieldErrors: { createdOrderId?: string; momoPhone?: string } = {};
    if (!createdOrderId.trim()) nextFieldErrors.createdOrderId = "Order ID is required";
    const cash = paymentMethod === "CASH";
    // Cash is recorded at the counter; MoMo and card go through the provider and need the payer's phone.
    if (!cash && !momoPhone.trim()) nextFieldErrors.momoPhone = paymentMethod === "MOBILE_MONEY" ? "Enter the customer's MoMo number" : "Enter the customer's phone number";
    const tenderedAmount = cash && tendered.trim() ? Number(tendered) : undefined;
    if (cash && tenderedAmount !== undefined && createdOrder && tenderedAmount < Number(createdOrder.total)) {
      nextFieldErrors.momoPhone = `Amount received is less than ${formatMoney(createdOrder.total)}`;
    }
    if (Object.keys(nextFieldErrors).length > 0) {
      setPaymentFieldErrors(nextFieldErrors);
      return;
    }

    setSubmittingPayment(true);
    setMessage(null);
    setPaymentFieldErrors({});
    try {
      const initiated = await authenticatedFetch((activeSession) =>
        initiatePayment(activeSession, {
          orderId: createdOrderId.trim(),
          method: paymentMethod,
          momoPhone: cash ? undefined : momoPhone.trim(),
          amountTendered: tenderedAmount,
          idempotencyKey: `pos-${createdOrderId}-${Date.now()}`,
        }),
      );
      setChangeDue(initiated.changeDue == null ? null : Number(initiated.changeDue));
      setPaymentId(initiated.paymentId);
      const payment = await authenticatedFetch((activeSession) => getPayment(activeSession, initiated.paymentId));
      setPaymentRecord(payment);
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Could not initiate payment" });
    } finally {
      setSubmittingPayment(false);
    }
  }

  async function sendReceipt() {
    if (!createdOrderId) return;
    try {
      const result = await authenticatedFetch((activeSession) => sendOrderToPrinter(activeSession, createdOrderId, "receipt"));
      setMessage({ tone: "success", text: result.message });
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Could not print receipt" });
    }
  }

  async function handleVerifyPayment() {
    if (!session || !paymentId) return;
    setSubmittingPayment(true);
    setMessage(null);
    try {
      const verified = await authenticatedFetch((activeSession) => verifyPayment(activeSession, paymentId));
      setPaymentRecord(verified);
    } catch (err) {
      setMessage({ tone: "danger", text: err instanceof Error ? err.message : "Could not verify payment" });
    } finally {
      setSubmittingPayment(false);
    }
  }

  function startNewOrder() {
    setStage("ticket");
    setCreatedOrder(null);
    setCreatedOrderId("");
    setPaymentId("");
    setPaymentRecord(null);
    setMomoPhone("");
    setTendered("");
    setChangeDue(null);
    setPaymentFieldErrors({});
    setMessage(null);
    searchRef.current?.focus();
  }

  function payExistingOrder() {
    setCreatedOrder(null);
    setCreatedOrderId("");
    setPaymentId("");
    setPaymentRecord(null);
    setMessage(null);
    setStage("payment");
  }

  /* ---------------------------- Render ---------------------------- */
  if (loading || !session) {
    return (
      <div className="grid gap-4 lg:grid-cols-[1fr_420px]">
        <div className="grid content-start gap-3">
          <Skeleton className="h-12 w-full" />
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 12 }).map((_, i) => <Skeleton key={i} className="h-32" />)}
          </div>
        </div>
        <Skeleton className="h-[70dvh]" />
      </div>
    );
  }

  const paymentSettled = paymentRecord?.status === "SUCCESS";

  return (
    <div className="grid gap-4 lg:h-[calc(100dvh-64px-2.5rem)] lg:grid-cols-[minmax(0,1fr)_400px] xl:grid-cols-[minmax(0,1fr)_440px]">
      {/* ================= MENU ================= */}
      <section className="flex min-h-0 flex-col overflow-hidden rounded-panel border border-line bg-surface" aria-label="Menu">
        <div className="grid gap-3 border-b border-line p-3 sm:p-4">
          <label className="relative block">
            <span className="sr-only">Search menu</span>
            <Icon name="search" size={18} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              ref={searchRef}
              type="search"
              className="input h-12 pl-10 pr-16 text-base"
              placeholder="Search dishes…"
              value={menuQuery}
              onChange={(event) => setMenuQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && filteredMenuItems[0]) {
                  event.preventDefault();
                  void addItem(filteredMenuItems[0]);
                } else if (event.key === "Escape") {
                  setMenuQuery("");
                }
              }}
            />
            <kbd className="pointer-events-none absolute right-3 top-1/2 hidden -translate-y-1/2 rounded-md border border-line bg-sunken px-1.5 py-0.5 font-mono text-xs text-ink-muted sm:block">/</kbd>
          </label>
          <div className="flex justify-end">
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setStaffClockOpen(true)}>
              <Icon name="clock" size={16} /> Staff clock
            </button>
          </div>
          <div className="-mx-1 flex gap-2 overflow-x-auto px-1 [scrollbar-width:none]" role="group" aria-label="Categories">
            {categories.map((category) => (
              <button
                key={category}
                type="button"
                className="chip min-h-[44px] shrink-0 px-5"
                aria-pressed={categoryFilter === category}
                onClick={() => setCategoryFilter(category)}
              >
                {category === "ALL" ? "All dishes" : category}
              </button>
            ))}
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-3 sm:p-4">
          {!menuLoaded ? (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 2xl:grid-cols-4">
              {Array.from({ length: 12 }).map((_, i) => <Skeleton key={i} className="h-[132px]" />)}
            </div>
          ) : filteredMenuItems.length === 0 ? (
            <div className="grid h-full place-items-center p-10 text-center">
              <div>
                <p className="font-display text-lg font-bold">{menuItems.length === 0 ? "No dishes available" : "No dishes match"}</p>
                <p className="mt-1 text-sm text-ink-soft">
                  {menuItems.length === 0 ? "Dishes marked available in the Menu Builder appear here." : "Try another category or clear the search."}
                </p>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 2xl:grid-cols-4">
              {filteredMenuItems.map((item) => {
                const qty = qtyByItem.get(item.id) ?? 0;
                const busy = pickerLoadingId === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => void addItem(item)}
                    disabled={busy}
                    className={`group relative flex min-h-[132px] flex-col overflow-hidden rounded-xl border text-left transition-[border-color,box-shadow,transform] duration-150 active:scale-[0.98] ${
                      qty > 0 ? "border-brand bg-brand-subtle/40 shadow-sm" : "border-line bg-surface hover:border-line-strong hover:shadow-card"
                    }`}
                  >
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt="" className="h-20 w-full object-cover" loading="lazy" />
                    ) : null}
                    <div className="flex flex-1 flex-col justify-between gap-2 p-3">
                      <p className="line-clamp-2 font-display text-[15px] font-bold leading-snug text-ink">{item.name}</p>
                      <div className="flex items-end justify-between gap-2">
                        <span className="text-xs font-semibold text-ink-muted">{item.categoryName}</span>
                        <span className="money text-[15px] text-ink">{formatMoney(item.price)}</span>
                      </div>
                    </div>
                    {qty > 0 ? (
                      <span key={qty} className="absolute right-2 top-2 grid h-7 min-w-7 animate-pop place-items-center rounded-full bg-brand px-2 text-sm font-bold text-white shadow-sm">
                        {qty}
                      </span>
                    ) : (
                      <span className="absolute right-2 top-2 grid h-7 w-7 place-items-center rounded-full bg-surface/90 text-ink-muted opacity-0 shadow-xs transition-opacity group-hover:opacity-100">
                        {busy ? <Spinner size={14} /> : <Icon name="plus" size={16} />}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </section>

      {/* ================= TICKET / PAYMENT ================= */}
      <aside className="flex min-h-[70dvh] flex-col overflow-hidden rounded-panel border border-line bg-surface shadow-card lg:min-h-0" aria-label={stage === "ticket" ? "Current ticket" : "Payment"}>
        {/* Panel header */}
        <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
          <div className="flex items-center gap-2">
            <span className={`grid h-8 w-8 place-items-center rounded-lg ${stage === "ticket" ? "bg-espresso text-gold" : "bg-success-subtle text-success"}`}>
              <Icon name={stage === "ticket" ? "receipt" : "card"} size={18} />
            </span>
            <div>
              <p className="font-display text-base font-bold leading-tight">{stage === "ticket" ? "New ticket" : "Take payment"}</p>
              <p className="text-xs text-ink-muted">
                {stage === "ticket" ? `${cartCount} item${cartCount === 1 ? "" : "s"}` : createdOrderId ? `Order #${createdOrderId.slice(0, 8).toUpperCase()}` : "Existing order"}
              </p>
            </div>
          </div>
          {stage === "ticket" ? (
            <div className="flex items-center gap-1">
              <button type="button" className="btn btn-ghost btn-sm" onClick={payExistingOrder}>Pay existing</button>
              {cart.length > 0 ? (
                <button type="button" className="btn btn-ghost btn-sm text-danger-on" onClick={() => setCart([])}>Clear</button>
              ) : null}
            </div>
          ) : (
            <button type="button" className="btn btn-secondary btn-sm" onClick={startNewOrder}>
              <Icon name="plus" size={16} /> New order
            </button>
          )}
        </div>

        {message ? (
          <div className="px-4 pt-3">
            <p className={`alert alert-${message.tone}`} role={message.tone === "danger" ? "alert" : "status"}>
              <Icon name={message.tone === "danger" ? "alert" : message.tone === "success" ? "checkCircle" : "info"} size={18} className="mt-0.5 shrink-0" />
              <span className="flex-1">{message.text}</span>
              <button type="button" className="-my-1 -mr-1 rounded p-1 opacity-70 hover:opacity-100" onClick={() => setMessage(null)} aria-label="Dismiss">
                <Icon name="close" size={16} />
              </button>
            </p>
          </div>
        ) : null}

        {stage === "ticket" ? (
          <>
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
              {/* Service type */}
              <div className="segmented grid w-full grid-cols-3" role="group" aria-label="Service type">
                {ORDER_TYPES.map((type) => (
                  <button key={type.value} type="button" aria-pressed={orderType === type.value} onClick={() => setOrderType(type.value)}>
                    <Icon name={type.icon} size={17} /> {type.label}
                  </button>
                ))}
              </div>

              {orderType === "DINE_IN" ? (
                <fieldset className="mt-4">
                  <legend className="mb-2 flex w-full items-center justify-between text-[13px] font-semibold text-ink">
                    Table
                    {selectedTable ? <span className="text-xs font-normal text-ink-muted">Seats {selectedTable.capacity} · {selectedTable.zone}</span> : null}
                  </legend>
                  {dineInTables.length === 0 ? (
                    <p className="text-sm text-ink-soft">No tables set up yet.</p>
                  ) : (
                    <div className="grid grid-cols-5 gap-1.5" role="radiogroup" aria-label="Table" aria-describedby={orderFieldErrors.tableId ? "pos-table-error" : undefined}>
                      {dineInTables.map((table) => (
                        <button
                          key={table.id}
                          type="button"
                          role="radio"
                          aria-checked={tableId === table.id}
                          aria-label={`Table ${table.number}, ${table.status.toLowerCase()}`}
                          title={`${table.zone} · ${table.capacity} seats · ${table.status.toLowerCase()}`}
                          onClick={() => {
                            setTableId(table.id);
                            setOrderFieldErrors((errors) => ({ ...errors, tableId: undefined }));
                          }}
                          className={`relative grid h-12 place-items-center rounded-lg border font-display text-base font-bold transition-[box-shadow] ${TABLE_TONE[table.status]} ${
                            tableId === table.id ? "ring-2 ring-espresso ring-offset-1" : ""
                          }`}
                        >
                          {table.number}
                        </button>
                      ))}
                    </div>
                  )}
                  {orderFieldErrors.tableId ? <span id="pos-table-error" className="field-error mt-1.5 block" role="alert">{orderFieldErrors.tableId}</span> : null}
                </fieldset>
              ) : null}

              {orderType === "DELIVERY" ? (
                <div className="mt-4 grid gap-3">
                  <label className="field">
                    <span className="field-label">Delivery address</span>
                    <textarea
                      className="textarea min-h-[72px]"
                      rows={2}
                      value={deliveryAddress}
                      onChange={(event) => setDeliveryAddress(event.target.value)}
                      placeholder="House number, street, landmark"
                      aria-invalid={orderFieldErrors.deliveryAddress ? true : undefined}
                      aria-describedby={orderFieldErrors.deliveryAddress ? "pos-delivery-error" : undefined}
                    />
                  </label>
                  {deliveryZones.length > 0 ? (
                    <label className="field">
                      <span className="field-label">Delivery zone</span>
                      <select className="select" value={deliveryZoneId} onChange={(event) => setDeliveryZoneId(event.target.value)}>
                        <option value="">Select a zone</option>
                        {deliveryZones.map((zone) => (
                          <option key={zone.id} value={zone.id}>
                            {`${zone.name} · ${formatMoney(zone.fee)} · ~${zone.estimatedMinutes} min`}
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : null}
                  {orderFieldErrors.deliveryAddress ? <span id="pos-delivery-error" className="field-error" role="alert">{orderFieldErrors.deliveryAddress}</span> : null}
                </div>
              ) : null}

              {/* Lines */}
              <div className="mt-5">
                {cart.length === 0 ? (
                  <div className="grid justify-items-center gap-2 rounded-xl border border-dashed border-line-strong px-4 py-10 text-center">
                    <Icon name="receipt" size={26} className="text-ink-muted" />
                    <p className="font-display font-bold text-ink">Ticket is empty</p>
                    <p className="text-sm text-ink-soft">Tap dishes on the left. Press <kbd className="rounded border border-line bg-sunken px-1 font-mono text-xs">/</kbd> to search.</p>
                    {orderFieldErrors.cart ? <span className="field-error" role="alert">{orderFieldErrors.cart}</span> : null}
                  </div>
                ) : (
                  <ul className="divide-y divide-line-subtle">
                    {cart.map((line) => {
                      const key = lineKey(line.item);
                      return (
                        <li key={key} className="py-3">
                          <div className="flex items-start gap-3">
                            <div className="min-w-0 flex-1">
                              <p className="font-semibold leading-snug text-ink">{line.item.name}</p>
                              {line.item.modifierLabel ? <p className="text-xs text-ink-soft">{line.item.modifierLabel}</p> : null}
                              <p className="text-xs text-ink-muted">{formatMoney(line.item.price)} each</p>
                            </div>
                            <p className="money shrink-0 text-ink">{formatMoney(line.item.price * line.quantity)}</p>
                          </div>
                          <div className="mt-2 flex items-center justify-between gap-2">
                            <div className="flex items-center rounded-lg border border-line bg-sunken">
                              <button type="button" className="grid h-10 w-11 place-items-center rounded-l-lg text-ink hover:bg-muted" onClick={() => setCart((c) => setQuantity(c, key, line.quantity - 1))} aria-label={`Decrease ${line.item.name}`}>
                                <Icon name={line.quantity === 1 ? "trash" : "minus"} size={16} />
                              </button>
                              <span className="w-9 text-center font-display font-bold tabular" aria-live="polite">{line.quantity}</span>
                              <button type="button" className="grid h-10 w-11 place-items-center rounded-r-lg text-ink hover:bg-muted" onClick={() => setCart((c) => setQuantity(c, key, line.quantity + 1))} aria-label={`Increase ${line.item.name}`}>
                                <Icon name="plus" size={16} />
                              </button>
                            </div>
                            <div className="flex items-center gap-1">
                              <button type="button" className={`btn btn-ghost btn-sm ${line.notes ? "text-brand" : ""}`} onClick={() => setEditingNote(editingNote === key ? null : key)} aria-expanded={editingNote === key}>
                                <Icon name="note" size={16} /> {line.notes ? "Note" : "Add note"}
                              </button>
                              <button type="button" className="btn btn-ghost btn-icon btn-sm text-ink-muted hover:text-danger-on" onClick={() => setCart((c) => removeFromCart(c, key))} aria-label={`Remove ${line.item.name}`}>
                                <Icon name="close" size={16} />
                              </button>
                            </div>
                          </div>
                          {editingNote === key || line.notes ? (
                            <input
                              className="input mt-2 min-h-[40px] py-2 text-sm"
                              placeholder="e.g. no onions, extra spicy"
                              value={line.notes ?? ""}
                              maxLength={200}
                              autoFocus={editingNote === key}
                              onChange={(event) => setCart((c) => setLineNotes(c, key, event.target.value))}
                              aria-label={`Kitchen note for ${line.item.name}`}
                            />
                          ) : null}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>

            {/* Totals + submit */}
            <div className="border-t border-line bg-ivory/70 px-4 py-3">
              <button type="button" className="mb-2 flex w-full items-center justify-between text-sm font-semibold text-ink-soft hover:text-ink" onClick={() => setShowExtras((v) => !v)} aria-expanded={showExtras}>
                <span className="flex items-center gap-2"><Icon name="tag" size={16} /> Promo, {tipsEnabled ? "tip, " : ""}order note{promoCode || orderNotes || tipAmount ? " · added" : ""}</span>
                <Icon name="chevronDown" size={16} className={`transition-transform ${showExtras ? "rotate-180" : ""}`} />
              </button>
              {showExtras ? (
                <div className="mb-3 grid gap-2">
                  <input className="input min-h-[40px] py-2 text-sm uppercase" value={promoCode} onChange={(e) => setPromoCode(e.target.value.toUpperCase())} placeholder="Promo code" maxLength={30} aria-label="Promo code" />
                  <input className="input min-h-[40px] py-2 text-sm" value={orderNotes} onChange={(e) => setOrderNotes(e.target.value)} placeholder="Note for the whole order" maxLength={500} aria-label="Order note" />
                  {tipsEnabled ? (
                    <input className="input min-h-[40px] py-2 text-sm" type="number" min={0} step="0.01" inputMode="decimal" value={tipAmount} onChange={(e) => setTipAmount(e.target.value)} placeholder="Tip amount (optional)" aria-label="Tip amount" />
                  ) : null}
                </div>
              ) : null}

              <dl className="grid gap-1 text-sm">
                <Row label="Subtotal" value={formatMoney(quote ? quote.subtotal : subtotal)} />
                {quote && Number(quote.offerDiscount) > 0 ? <Row label="Offer" value={`− ${formatMoney(quote.offerDiscount)}`} tone="success" /> : null}
                {quote && Number(quote.promoDiscount) > 0 ? <Row label={`Promo ${quote.promoCode ?? ""}`} value={`− ${formatMoney(quote.promoDiscount)}`} tone="success" /> : null}
                {quote && Number(quote.deliveryFee) > 0 ? <Row label="Delivery" value={formatMoney(quote.deliveryFee)} /> : null}
                {quote && Number(quote.serviceCharge ?? 0) > 0 ? <Row label="Service charge" value={formatMoney(quote.serviceCharge)} /> : null}
                {quote && Number(quote.tipAmount ?? 0) > 0 ? <Row label="Tip" value={formatMoney(quote.tipAmount)} /> : null}
                {quote?.taxLines?.map((tax) => (
                  <Row key={tax.code} label={`${tax.name}${quote.taxInclusive ? " (incl.)" : ""}`} value={formatMoney(tax.amount)} muted={quote.taxInclusive} />
                ))}
              </dl>
              {promoCode && quote && !quote.promoCode ? <p className="mt-1 text-xs text-warning-on">Promo code not applied — check it&apos;s valid for this order.</p> : null}
              {quoteState === "error" ? <p className="mt-1 text-xs text-ink-muted">Live pricing unavailable — final total is confirmed when the order is placed.</p> : null}

              <button
                type="button"
                className="btn btn-primary btn-xl btn-block mt-3 justify-between"
                disabled={submittingOrder || cart.length === 0}
                onClick={() => void handleCreateOrder()}
              >
                <span className="flex items-center gap-2">
                  {submittingOrder ? <Spinner className="text-current" /> : <Icon name="chef" size={20} />}
                  {submittingOrder ? "Sending…" : "Send to kitchen"}
                </span>
                <span className="money flex items-center gap-2">
                  {quoteState === "loading" ? <Spinner size={14} className="opacity-70" /> : null}
                  {formatMoney(displayTotal)}
                </span>
              </button>
            </div>
          </>
        ) : (
          /* ---------------- Payment step ---------------- */
          <>
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
              {createdOrder ? (
                <div className="rounded-xl bg-espresso p-4 text-espresso-ink">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-espresso-soft">Amount due</p>
                  <p className="money mt-1 font-display text-4xl font-extrabold">{formatMoney(createdOrder.total)}</p>
                  <p className="mt-1 text-sm text-espresso-soft">
                    {createdOrder.type === "DINE_IN" ? `Table ${createdOrder.tableNumber ?? "—"}` : createdOrder.type === "PICKUP" ? `Pickup${createdOrder.pickupCode ? ` · code ${createdOrder.pickupCode}` : ""}` : "Delivery"}
                    {" · "}{createdOrder.items.reduce((n, i) => n + i.quantity, 0)} items
                  </p>
                </div>
              ) : (
                <label className="field">
                  <span className="field-label">Order ID</span>
                  <input
                    className="input font-mono"
                    value={createdOrderId}
                    onChange={(event) => setCreatedOrderId(event.target.value)}
                    placeholder="Paste the order ID to charge"
                    aria-invalid={paymentFieldErrors.createdOrderId ? true : undefined}
                    aria-describedby={paymentFieldErrors.createdOrderId ? "pos-order-id-error" : undefined}
                  />
                  {paymentFieldErrors.createdOrderId ? <span id="pos-order-id-error" className="field-error" role="alert">{paymentFieldErrors.createdOrderId}</span> : null}
                </label>
              )}

              <fieldset className="mt-5" disabled={!!paymentId && !paymentRecord?.status?.match(/FAILED|VOIDED/)}>
                <legend className="mb-2 text-[13px] font-semibold text-ink">Payment method</legend>
                <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Payment method">
                  {PAYMENT_METHODS.map((method) => (
                    <button
                      key={method.value}
                      type="button"
                      role="radio"
                      aria-checked={paymentMethod === method.value}
                      onClick={() => setPaymentMethod(method.value)}
                      className={`grid min-h-[92px] justify-items-center gap-2 rounded-xl border p-3 text-center transition-colors disabled:opacity-50 ${
                        paymentMethod === method.value ? "border-brand bg-brand-subtle text-brand ring-1 ring-brand" : "border-line bg-surface text-ink-soft hover:border-line-strong"
                      }`}
                    >
                      <Icon name={method.icon} size={26} />
                      <span className="text-sm font-bold text-ink">{method.label}</span>
                    </button>
                  ))}
                </div>
                <p className="mt-2 text-xs text-ink-muted">{PAYMENT_METHODS.find((m) => m.value === paymentMethod)?.hint}</p>
              </fieldset>

              {paymentMethod === "CASH" ? (
                <div className="field mt-4">
                  <label className="field-label" htmlFor="pos-tendered">Amount received <span className="font-normal text-ink-muted">(optional)</span></label>
                  <input
                    id="pos-tendered"
                    className="input text-lg tabular"
                    inputMode="decimal"
                    value={tendered}
                    onChange={(event) => setTendered(event.target.value.replace(/[^0-9.]/g, ""))}
                    placeholder={createdOrder ? Number(createdOrder.total).toFixed(2) : "0.00"}
                    aria-invalid={paymentFieldErrors.momoPhone ? true : undefined}
                    aria-describedby={paymentFieldErrors.momoPhone ? "pos-momo-error" : undefined}
                    disabled={!!paymentId && paymentRecord?.status === "SUCCESS"}
                  />
                  {createdOrder ? (
                    <div className="flex flex-wrap gap-2" role="group" aria-label="Quick amounts">
                      {Array.from(new Set([
                        Number(createdOrder.total),
                        Math.ceil(Number(createdOrder.total) / 10) * 10,
                        Math.ceil(Number(createdOrder.total) / 50) * 50,
                        Math.ceil(Number(createdOrder.total) / 100) * 100,
                      ])).map((amount) => (
                        <button key={amount} type="button" className="chip min-h-[40px]" onClick={() => setTendered(amount.toFixed(2))}>
                          {amount === Number(createdOrder.total) ? "Exact" : formatMoney(amount)}
                        </button>
                      ))}
                    </div>
                  ) : null}
                  {tendered && createdOrder && Number(tendered) >= Number(createdOrder.total) && changeDue === null ? (
                    <p className="text-sm font-semibold text-ink">Change to give: <span className="money">{formatMoney(Number(tendered) - Number(createdOrder.total))}</span></p>
                  ) : null}
                  {paymentFieldErrors.momoPhone ? <span id="pos-momo-error" className="field-error" role="alert">{paymentFieldErrors.momoPhone}</span> : null}
                </div>
              ) : (
                <label className="field mt-4">
                  <span className="field-label">{paymentMethod === "MOBILE_MONEY" ? "Customer MoMo number" : "Customer phone"}</span>
                  <input
                    className="input text-lg"
                    inputMode="tel"
                    value={momoPhone}
                    onChange={(event) => setMomoPhone(event.target.value)}
                    placeholder="+233 24 000 0000"
                    aria-invalid={paymentFieldErrors.momoPhone ? true : undefined}
                    aria-describedby={paymentFieldErrors.momoPhone ? "pos-momo-error" : undefined}
                  />
                  {paymentFieldErrors.momoPhone ? <span id="pos-momo-error" className="field-error" role="alert">{paymentFieldErrors.momoPhone}</span> : null}
                </label>
              )}

              {paymentRecord ? (
                <div className={`mt-5 rounded-xl border p-4 ${paymentSettled ? "border-success-border bg-success-subtle" : "border-line bg-sunken"}`} role="status" aria-live="polite">
                  {paymentSettled && changeDue !== null && changeDue > 0 ? (
                    <p className="mb-3 rounded-lg bg-surface px-3 py-2 text-center font-display text-2xl font-extrabold text-ink">
                      Change due <span className="money text-brand">{formatMoney(changeDue)}</span>
                    </p>
                  ) : null}
                  <div className="flex items-center justify-between gap-3">
                    <p className="flex items-center gap-2 font-display font-bold text-ink">
                      {paymentSettled ? <Icon name="checkCircle" className="text-success" /> : <Spinner size={16} />}
                      {paymentSettled ? "Payment received" : paymentRecord.status === "FAILED" ? "Payment failed" : "Waiting for confirmation"}
                    </p>
                    <span className={`badge ${paymentStatusClass(paymentRecord.status)}`}>{paymentRecord.status.replace(/_/g, " ").toLowerCase()}</span>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                    <div><dt className="text-xs text-ink-muted">Amount</dt><dd className="money">{paymentRecord.currency} {paymentRecord.amount}</dd></div>
                    <div><dt className="text-xs text-ink-muted">Method</dt><dd className="font-semibold">{paymentRecord.method === "MOBILE_MONEY" ? "MoMo" : paymentRecord.method.toLowerCase()}</dd></div>
                    <div><dt className="text-xs text-ink-muted">Reference</dt><dd className="font-mono text-xs">{paymentRecord.id.slice(0, 8).toUpperCase()}</dd></div>
                    <div><dt className="text-xs text-ink-muted">Started</dt><dd>{formatDateTime(paymentRecord.createdAt)}</dd></div>
                  </dl>
                  {paymentRecord.providerMessage ? <p className="mt-3 text-sm text-info-on">{paymentRecord.providerMessage}</p> : null}
                </div>
              ) : null}
            </div>

            <div className="grid gap-2 border-t border-line bg-ivory/70 px-4 py-3">
              {paymentSettled ? (
                <>
                  {createdOrderId ? (
                    <div className="grid grid-cols-2 gap-2">
                      <a className="btn btn-secondary btn-md" href={`/print/receipt/${createdOrderId}`} target="_blank" rel="noreferrer">
                        <Icon name="print" size={16} /> Print in browser
                      </a>
                      <button type="button" className="btn btn-secondary btn-md" onClick={() => void sendReceipt()}>
                        <Icon name="receipt" size={16} /> Send to printer
                      </button>
                    </div>
                  ) : null}
                  <button type="button" className="btn btn-success btn-xl btn-block" onClick={startNewOrder}>
                    <Icon name="check" size={20} /> Done — start next order
                  </button>
                </>
              ) : (
                <>
                  <button type="button" className="btn btn-primary btn-xl btn-block" disabled={submittingPayment || (!!paymentId && paymentRecord?.status !== "FAILED")} onClick={() => void handleInitiatePayment()}>
                    {submittingPayment ? <Spinner className="text-current" /> : <Icon name={PAYMENT_METHODS.find((m) => m.value === paymentMethod)?.icon ?? "card"} size={20} />}
                    {paymentId && paymentRecord?.status === "FAILED" ? "Try again" : createdOrder ? `Charge ${formatMoney(createdOrder.total)}` : "Start payment"}
                  </button>
                  <div className="grid grid-cols-2 gap-2">
                    <button type="button" className="btn btn-secondary btn-md" disabled={!paymentId || submittingPayment} onClick={() => void handleVerifyPayment()}>
                      <Icon name="refresh" size={16} /> Check status
                    </button>
                    <button type="button" className="btn btn-ghost btn-md" onClick={startNewOrder}>Pay later</button>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </aside>

      {picker ? (
        <ModifierPicker
          item={picker.item}
          groups={picker.groups}
          onClose={() => setPicker(null)}
          onAdd={(line, quantity) => {
            setCart((current) => addToCart(current, line, quantity));
            setOrderFieldErrors((errors) => ({ ...errors, cart: undefined }));
            setPicker(null);
          }}
        />
      ) : null}
      {/* Staff attendance is independent of the till: it never changes the signed-in account or the order in progress. */}
      {staffClockOpen ? <StaffClockPanel onClose={() => setStaffClockOpen(false)} /> : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function Row({ label, value, tone, muted }: { label: string; value: string; tone?: "success"; muted?: boolean }) {
  return (
    <div className={`flex justify-between gap-3 ${muted ? "text-ink-muted" : tone === "success" ? "text-success-on" : "text-ink-soft"}`}>
      <dt>{label}</dt>
      <dd className="money">{value}</dd>
    </div>
  );
}

function ModifierPicker({
  item,
  groups,
  onClose,
  onAdd,
}: {
  item: MenuItemRecord;
  groups: MenuModifierGroupRecord[];
  onClose: () => void;
  onAdd: (line: { id: string; name: string; price: number; modifierOptionIds: string[]; modifierLabel: string }, quantity: number) => void;
}) {
  const [selected, setSelected] = useState<Record<string, string[]>>(() => {
    const initial: Record<string, string[]> = {};
    for (const group of groups) {
      initial[group.id] = group.selectionType === "SINGLE" && group.required && group.options[0] ? [group.options[0].id] : [];
    }
    return initial;
  });
  const [quantity, setQty] = useState(1);
  const [error, setError] = useState("");

  const base = Number.parseFloat(item.price);
  const allOptions = groups.flatMap((g) => g.options);
  const chosenIds = Object.values(selected).flat();
  const unit = base + chosenIds.reduce((sum, id) => sum + Number(allOptions.find((o) => o.id === id)?.priceDelta ?? 0), 0);

  function toggle(group: MenuModifierGroupRecord, optionId: string) {
    setError("");
    setSelected((current) => {
      const existing = current[group.id] ?? [];
      if (group.selectionType === "SINGLE") return { ...current, [group.id]: [optionId] };
      if (existing.includes(optionId)) return { ...current, [group.id]: existing.filter((id) => id !== optionId) };
      if (group.maxSelect && existing.length >= group.maxSelect) return current;
      return { ...current, [group.id]: [...existing, optionId] };
    });
  }

  function submit() {
    for (const group of groups) {
      const count = selected[group.id]?.length ?? 0;
      const min = group.minSelect ?? (group.required ? 1 : 0);
      if (count < min) {
        setError(`Choose ${min === 1 ? "an option" : `at least ${min}`} for ${group.name}`);
        return;
      }
    }
    const label = groups
      .map((g) => (selected[g.id] ?? []).map((id) => g.options.find((o) => o.id === id)?.name).filter(Boolean).join(", "))
      .filter(Boolean)
      .join(" · ");
    onAdd({ id: item.id, name: item.name, price: unit, modifierOptionIds: chosenIds, modifierLabel: label }, quantity);
  }

  return (
    <Modal
      open
      title={item.name}
      onClose={onClose}
      footer={
        <>
          <div className="mr-auto flex items-center rounded-lg border border-line bg-surface">
            <button type="button" className="grid h-11 w-11 place-items-center" onClick={() => setQty((q) => Math.max(1, q - 1))} aria-label="Decrease quantity"><Icon name="minus" size={16} /></button>
            <span className="w-8 text-center font-display font-bold tabular">{quantity}</span>
            <button type="button" className="grid h-11 w-11 place-items-center" onClick={() => setQty((q) => q + 1)} aria-label="Increase quantity"><Icon name="plus" size={16} /></button>
          </div>
          <button type="button" className="btn btn-primary btn-md" onClick={submit}>
            Add · {formatMoney(unit * quantity)}
          </button>
        </>
      }
    >
      <div className="grid gap-5">
        {groups.map((group) => (
          <fieldset key={group.id}>
            <legend className="mb-2 flex w-full items-center justify-between">
              <span className="font-display font-bold">{group.name}</span>
              <span className={`badge ${group.required ? "badge-warning" : "badge-neutral"}`}>
                {group.required ? "Required" : "Optional"}
                {group.selectionType === "MULTIPLE" && group.maxSelect ? ` · max ${group.maxSelect}` : ""}
              </span>
            </legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {group.options.map((option) => {
                const on = selected[group.id]?.includes(option.id) ?? false;
                return (
                  <button
                    key={option.id}
                    type="button"
                    role={group.selectionType === "SINGLE" ? "radio" : "checkbox"}
                    aria-checked={on}
                    onClick={() => toggle(group, option.id)}
                    className={`flex min-h-[48px] items-center justify-between gap-2 rounded-lg border px-3 text-left text-sm font-semibold transition-colors ${
                      on ? "border-brand bg-brand-subtle text-ink ring-1 ring-brand" : "border-line text-ink-soft hover:border-line-strong"
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <span className={`grid h-5 w-5 place-items-center border ${group.selectionType === "SINGLE" ? "rounded-full" : "rounded"} ${on ? "border-brand bg-brand text-white" : "border-line-strong"}`}>
                        {on ? <Icon name="check" size={13} strokeWidth={3} /> : null}
                      </span>
                      {option.name}
                    </span>
                    <span className="money text-xs text-ink-muted">{Number(option.priceDelta) > 0 ? `+${formatMoney(option.priceDelta)}` : ""}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>
        ))}
        {error ? <p className="field-error" role="alert">{error}</p> : null}
      </div>
    </Modal>
  );
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
  return parsed.toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}
