"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/Icon";
import { useT } from "@/components/I18nProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { Spinner } from "@/components/Spinner";
import { FoodImage } from "@/components/site/FoodImage";
import {
  addMyAddress,
  createOrder,
  getDeliveryZones,
  getMe,
  getMyAddresses,
  initiatePayment,
  quoteOrder,
  quoteOrderPublic,
  type CustomerAddressRecord,
  type DeliveryZoneRecord,
  type OrderQuoteRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { cart, cartSubtotal, useCart } from "@/lib/cart";

type Fulfilment = "PICKUP" | "DELIVERY";

export function CartClient() {
  const t = useT();
  const router = useRouter();
  const lines = useCart();
  const { session, authenticatedFetch } = useStaffSession();
  const isCustomer = session?.user.role === "CUSTOMER";

  const [type, setType] = useState<Fulfilment>("PICKUP");
  const [zones, setZones] = useState<DeliveryZoneRecord[]>([]);
  const [zoneId, setZoneId] = useState("");
  const [addresses, setAddresses] = useState<CustomerAddressRecord[]>([]);
  const [addressId, setAddressId] = useState<string>("new");
  const [address, setAddress] = useState("");
  const [saveAddress, setSaveAddress] = useState(true);
  const [notes, setNotes] = useState("");
  const [promo, setPromo] = useState("");
  const [appliedPromo, setAppliedPromo] = useState("");
  const [tip, setTip] = useState(0);
  const [method, setMethod] = useState<"MOBILE_MONEY" | "CARD">("MOBILE_MONEY");
  const [momoPhone, setMomoPhone] = useState("");
  const [quote, setQuote] = useState<OrderQuoteRecord | null>(null);
  const [quoteError, setQuoteError] = useState("");
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getDeliveryZones().then((list) => setZones(list.filter((z) => z.active))).catch(() => setZones([]));
  }, []);

  useEffect(() => {
    if (!isCustomer) return;
    authenticatedFetch((s) => getMe(s)).then((me) => setMomoPhone((current) => current || me.phone)).catch(() => {});
    authenticatedFetch((s) => getMyAddresses(s))
      .then((list) => {
        setAddresses(list);
        const preferred = list.find((a) => a.isDefault) ?? list[0];
        if (preferred) setAddressId(preferred.id);
      })
      .catch(() => {});
  }, [authenticatedFetch, isCustomer]);

  const zone = zones.find((z) => z.id === zoneId);
  const chosenAddress = addresses.find((a) => a.id === addressId);
  const deliveryAddress = type === "DELIVERY" ? (chosenAddress ? [chosenAddress.addressLine, chosenAddress.city, chosenAddress.landmark ? `near ${chosenAddress.landmark}` : ""].filter(Boolean).join(", ") : address.trim()) : undefined;

  const payload = useMemo(
    () => ({
      type,
      deliveryZoneId: type === "DELIVERY" ? zoneId || undefined : undefined,
      deliveryAddress,
      notes: notes.trim() || undefined,
      promoCode: appliedPromo || undefined,
      tipAmount: tip > 0 ? tip : undefined,
      items: lines.map((l) => ({ menuItemId: l.menuItemId, quantity: l.quantity, notes: l.notes, modifierOptionIds: l.modifierOptionIds })),
    }),
    [appliedPromo, deliveryAddress, lines, notes, tip, type, zoneId],
  );

  // Server-side price check (offers, promo, delivery fee, tax, service charge).
  useEffect(() => {
    if (lines.length === 0 || (type === "DELIVERY" && !zoneId)) {
      const frame = window.requestAnimationFrame(() => setQuote(null));
      return () => window.cancelAnimationFrame(frame);
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      const request = { ...payload, deliveryAddress: payload.deliveryAddress || (type === "DELIVERY" ? "-" : undefined) };
      (isCustomer ? authenticatedFetch((s) => quoteOrder(s, request)) : quoteOrderPublic(request))
        .then((next) => {
          if (cancelled) return;
          setQuote(next);
          setQuoteError("");
        })
        .catch((err) => {
          if (cancelled) return;
          setQuote(null);
          setQuoteError(err instanceof Error ? err.message : "Could not price the order");
          if (appliedPromo) setAppliedPromo("");
        });
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [appliedPromo, authenticatedFetch, isCustomer, lines.length, payload, type, zoneId]);

  const subtotal = cartSubtotal(lines);
  const total = quote ? Number(quote.total) : subtotal + (zone ? Number(zone.fee) : 0) + tip;
  const belowMinimum = type === "DELIVERY" && zone && subtotal < Number(zone.minOrderAmount);

  async function place() {
    setError("");
    if (type === "DELIVERY" && !zoneId) return setError("Choose your delivery area.");
    if (type === "DELIVERY" && !deliveryAddress) return setError("Enter your delivery address.");
    if (method === "MOBILE_MONEY" && !momoPhone.trim()) return setError("Enter the mobile money number to charge.");
    setPlacing(true);
    try {
      if (type === "DELIVERY" && addressId === "new" && saveAddress && address.trim()) {
        await authenticatedFetch((s) => addMyAddress(s, { label: "Delivery", addressLine: address.trim(), city: null, landmark: null, isDefault: addresses.length === 0 })).catch(() => undefined);
      }
      const order = await authenticatedFetch((s) => createOrder(s, payload));
      cart.clear();
      try {
        const payment = await authenticatedFetch((s) =>
          initiatePayment(s, { orderId: order.id, method, momoPhone: method === "MOBILE_MONEY" ? momoPhone.trim() : undefined, idempotencyKey: `web-${order.id}-${Date.now()}` }),
        );
        if (payment.authorizationUrl) window.open(payment.authorizationUrl, "_blank", "noopener");
        router.push(`/my-orders/${order.id}?paymentId=${payment.paymentId}`);
      } catch {
        // The order exists; payment can be retried from the tracking page.
        router.push(`/my-orders/${order.id}`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not place the order");
      setPlacing(false);
    }
  }

  if (lines.length === 0) {
    return (
      <section className="mx-auto max-w-md rounded-card border border-dashed border-line bg-surface p-10 text-center">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-cream text-brand"><Icon name="bag" size={26} /></span>
        <h1 className="heading-serif mt-4 text-3xl">{t("cart.empty")}</h1>
        <p className="mt-2 text-ink-soft">{t("cart.emptyHint")}</p>
        <Link href="/explore" className="btn btn-primary mt-6">{t("cart.browseMenu")}</Link>
      </section>
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1.25fr_1fr] lg:items-start">
      <div className="grid gap-6">
        <h1 className="heading-serif text-4xl">{t("checkout.title")}</h1>

        {/* Items */}
        <section className="rounded-card border border-line bg-surface p-5" aria-labelledby="cart-items">
          <h2 id="cart-items" className="font-display text-lg font-bold">{t("cart.title")}</h2>
          <ul className="mt-3 divide-y divide-line-subtle">
            {lines.map((line) => (
              <li key={line.key} className="flex gap-4 py-3">
                <FoodImage src={line.imageUrl ?? undefined} alt={line.name} ratio="aspect-square" className="w-16 shrink-0 rounded-lg" />
                <div className="min-w-0 flex-1">
                  <p className="font-semibold">{line.name}</p>
                  {line.modifierLabels.length > 0 ? <p className="text-sm text-ink-soft">{line.modifierLabels.join(" · ")}</p> : null}
                  {line.notes ? <p className="text-sm italic text-ink-muted">“{line.notes}”</p> : null}
                  <div className="mt-2 flex items-center gap-2">
                    <button type="button" className="btn btn-secondary btn-icon btn-sm" aria-label={`Fewer ${line.name}`} onClick={() => cart.setQuantity(line.key, line.quantity - 1)}><Icon name={line.quantity === 1 ? "trash" : "minus"} size={16} /></button>
                    <span className="w-6 text-center font-semibold tabular">{line.quantity}</span>
                    <button type="button" className="btn btn-secondary btn-icon btn-sm" aria-label={`More ${line.name}`} onClick={() => cart.setQuantity(line.key, line.quantity + 1)}><Icon name="plus" size={16} /></button>
                  </div>
                </div>
                <span className="money font-semibold">{formatMoney(line.unitPrice * line.quantity)}</span>
              </li>
            ))}
          </ul>
          <Link href="/explore" className="mt-2 inline-flex text-sm font-semibold text-brand hover:underline">+ Add more dishes</Link>
        </section>

        {/* Fulfilment */}
        <section className="rounded-card border border-line bg-surface p-5" aria-labelledby="fulfilment">
          <h2 id="fulfilment" className="font-display text-lg font-bold">{t("checkout.howToGet")}</h2>
          <div className="mt-3 grid grid-cols-2 gap-3">
            {(["PICKUP", "DELIVERY"] as const).map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={type === option}
                disabled={option === "DELIVERY" && zones.length === 0}
                onClick={() => setType(option)}
                className={`rounded-xl border p-4 text-left transition-colors disabled:opacity-50 ${type === option ? "border-brand bg-brand-subtle" : "border-line hover:border-line-strong"}`}
              >
                <Icon name={option === "PICKUP" ? "bag" : "scooter"} className="text-brand" />
                <p className="mt-2 font-semibold">{option === "PICKUP" ? t("checkout.pickup") : t("checkout.delivery")}</p>
                <p className="text-sm text-ink-soft">{option === "PICKUP" ? t("checkout.pickupHint") : zones.length === 0 ? "Not available right now" : t("checkout.deliveryHint")}</p>
              </button>
            ))}
          </div>

          {type === "DELIVERY" ? (
            <div className="mt-4 grid gap-4">
              <label className="field">
                <span className="field-label">{t("checkout.zone")}</span>
                <select className="select" value={zoneId} onChange={(e) => setZoneId(e.target.value)}>
                  <option value="">Choose an area…</option>
                  {zones.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name} · {formatMoney(z.fee)} · ~{z.estimatedMinutes} min{Number(z.minOrderAmount) > 0 ? ` · min ${formatMoney(z.minOrderAmount)}` : ""}
                    </option>
                  ))}
                </select>
                {zone?.description ? <span className="field-hint">{zone.description}</span> : null}
              </label>
              {addresses.length > 0 ? (
                <fieldset className="grid gap-2">
                  <legend className="field-label mb-1">{t("checkout.savedAddresses")}</legend>
                  {addresses.map((a) => (
                    <label key={a.id} className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${addressId === a.id ? "border-brand bg-brand-subtle" : "border-line"}`}>
                      <input type="radio" name="address" checked={addressId === a.id} onChange={() => setAddressId(a.id)} className="mt-1 accent-[var(--color-brand)]" />
                      <span><span className="font-semibold">{a.label}</span><span className="block text-sm text-ink-soft">{[a.addressLine, a.city].filter(Boolean).join(", ")}</span></span>
                    </label>
                  ))}
                  <label className={`flex cursor-pointer gap-3 rounded-xl border p-3 ${addressId === "new" ? "border-brand bg-brand-subtle" : "border-line"}`}>
                    <input type="radio" name="address" checked={addressId === "new"} onChange={() => setAddressId("new")} className="mt-1 accent-[var(--color-brand)]" />
                    <span className="font-semibold">{t("checkout.newAddress")}</span>
                  </label>
                </fieldset>
              ) : null}
              {addressId === "new" ? (
                <label className="field">
                  <span className="field-label">{t("checkout.address")}</span>
                  <textarea className="textarea" rows={2} maxLength={255} value={address} onChange={(e) => setAddress(e.target.value)} placeholder="House number, street, area, landmark" />
                  {isCustomer ? <span className="flex items-center gap-2 text-sm"><input type="checkbox" checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} /> {t("checkout.saveAddress")}</span> : null}
                </label>
              ) : null}
              {belowMinimum && zone ? <p className="alert alert-warning">{t("checkout.minOrder", { amount: formatMoney(zone.minOrderAmount) })}</p> : null}
            </div>
          ) : null}

          <label className="field mt-4">
            <span className="field-label">{t("cart.note")} <span className="font-normal text-ink-muted">({t("common.optional")})</span></span>
            <input className="input" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Allergies, gate code, call on arrival…" />
          </label>
        </section>
      </div>

      {/* Summary + payment */}
      <aside className="grid gap-4 lg:sticky lg:top-24">
        <section className="rounded-card border border-line bg-surface p-5 shadow-card">
          <h2 className="font-display text-lg font-bold">{t("checkout.summary")}</h2>

          <div className="mt-3 flex gap-2">
            <input className="input" placeholder={t("checkout.promo")} value={promo} onChange={(e) => setPromo(e.target.value.toUpperCase())} aria-label={t("checkout.promo")} />
            <button type="button" className="btn btn-secondary" disabled={!promo.trim()} onClick={() => setAppliedPromo(promo.trim())}>Apply</button>
          </div>
          {appliedPromo && quote?.promoCode ? <p className="mt-1 text-sm font-semibold text-success-on">Code {quote.promoCode} applied</p> : null}

          <div className="mt-4">
            <p className="field-label">{t("checkout.tip")}</p>
            <div className="mt-1 flex flex-wrap gap-2">
              {[0, 5, 10, 20].map((amount) => (
                <button key={amount} type="button" className="chip" aria-pressed={tip === amount} onClick={() => setTip(amount)}>
                  {amount === 0 ? t("checkout.noTip") : formatMoney(amount)}
                </button>
              ))}
            </div>
          </div>

          <dl className="mt-5 grid gap-1.5 text-sm">
            <Row label={t("common.subtotal")} value={quote ? quote.subtotal : subtotal} />
            {quote && Number(quote.discountTotal) > 0 ? <Row label="Discounts" value={-Number(quote.discountTotal)} /> : null}
            {type === "DELIVERY" ? <Row label="Delivery" value={quote ? quote.deliveryFee : zone?.fee ?? 0} /> : null}
            {quote && Number(quote.serviceCharge ?? 0) > 0 ? <Row label="Service charge" value={quote.serviceCharge ?? 0} /> : null}
            {quote?.taxLines?.map((tax) => <Row key={tax.code} label={`${tax.name}${quote.taxInclusive ? " (incl.)" : ""}`} value={tax.amount} />)}
            {tip > 0 ? <Row label={t("checkout.tip")} value={tip} /> : null}
            <div className="mt-2 flex items-baseline justify-between border-t border-line pt-3">
              <dt className="font-display text-base font-bold">{t("common.total")}</dt>
              <dd className="money font-display text-2xl font-extrabold">{formatMoney(total)}</dd>
            </div>
          </dl>
          {quoteError ? <p className="mt-2 text-sm text-danger-on" role="alert">{quoteError}</p> : null}
          {!quote && !quoteError ? <p className="mt-2 text-xs text-ink-muted">Final total is confirmed by the restaurant when you place the order.</p> : null}
        </section>

        {isCustomer ? (
          <section className="rounded-card border border-line bg-surface p-5">
            <h2 className="font-display text-lg font-bold">{t("checkout.payWith")}</h2>
            <div className="segmented mt-3 grid grid-cols-2" role="group" aria-label={t("checkout.payWith")}>
              <button type="button" aria-pressed={method === "MOBILE_MONEY"} onClick={() => setMethod("MOBILE_MONEY")}>Mobile money</button>
              <button type="button" aria-pressed={method === "CARD"} onClick={() => setMethod("CARD")}>Card</button>
            </div>
            {method === "MOBILE_MONEY" ? (
              <label className="field mt-3">
                <span className="field-label">{t("checkout.momoNumber")}</span>
                <input className="input" type="tel" inputMode="tel" value={momoPhone} onChange={(e) => setMomoPhone(e.target.value)} placeholder="+233 24 000 0000" />
              </label>
            ) : (
              <p className="mt-3 text-sm text-ink-soft">A secure card payment page opens in a new tab.</p>
            )}
            {error ? <p className="alert alert-danger mt-3" role="alert">{error}</p> : null}
            <button type="button" className="btn btn-primary btn-lg btn-block mt-4" disabled={placing || !!belowMinimum || !!quoteError} onClick={() => void place()}>
              {placing ? <><Spinner /> {t("checkout.placing")}</> : t("checkout.placeOrder", { amount: formatMoney(total) })}
            </button>
          </section>
        ) : (
          <section className="rounded-card border border-line bg-cream/60 p-5 text-center">
            <p className="font-display font-bold">{session ? "Staff accounts can't order here" : t("checkout.signInToOrder")}</p>
            <p className="mt-1 text-sm text-ink-soft">{t("checkout.signInHint")}</p>
            {!session ? <Link href="/account?next=/cart" className="btn btn-primary btn-block mt-4">{t("common.signIn")} / {t("common.createAccount")}</Link> : null}
          </section>
        )}
      </aside>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | number }) {
  const n = Number(value);
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-ink-soft">{label}</dt>
      <dd className="money">{n < 0 ? `- ${formatMoney(-n)}` : formatMoney(n)}</dd>
    </div>
  );
}
