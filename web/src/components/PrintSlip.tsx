"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useBrand } from "@/components/BrandProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { getOrderById, getReceiptByOrderId, type OrderRecord, type ReceiptRecord } from "@/lib/apiClient";

type Kind = "receipt" | "kitchen";

/**
 * Browser-printable 58/80 mm receipt or kitchen ticket. Works with any printer the device has
 * (USB/Bluetooth thermal printers included); network ESC/POS printing goes through the backend instead.
 */
export function PrintSlip({ orderId, kind }: { orderId: string; kind: Kind }) {
  const brand = useBrand();
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [order, setOrder] = useState<OrderRecord | null>(null);
  const [receipt, setReceipt] = useState<ReceiptRecord | null>(null);
  const [error, setError] = useState("");
  const [width, setWidth] = useState<58 | 80>(80);
  const [autoPrinted, setAutoPrinted] = useState(false);

  useEffect(() => {
    try {
      const stored = window.localStorage.getItem("print-width");
      if (stored === "58") {
        const frame = window.requestAnimationFrame(() => setWidth(58));
        return () => window.cancelAnimationFrame(frame);
      }
    } catch {
      /* default width */
    }
  }, []);

  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    authenticatedFetch(async (s) => {
      const nextOrder = await getOrderById(s, orderId);
      const nextReceipt = kind === "receipt" ? await getReceiptByOrderId(s, orderId).catch(() => null) : null;
      if (!cancelled) {
        setOrder(nextOrder);
        setReceipt(nextReceipt);
      }
    }).catch((err) => !cancelled && setError(err instanceof Error ? err.message : "Could not load the order"));
    return () => {
      cancelled = true;
    };
  }, [authenticatedFetch, kind, orderId, session]);

  useEffect(() => {
    if (!order || autoPrinted) return;
    const timer = window.setTimeout(() => {
      setAutoPrinted(true);
      window.print();
    }, 300);
    return () => window.clearTimeout(timer);
  }, [autoPrinted, order]);

  function chooseWidth(next: 58 | 80) {
    setWidth(next);
    try {
      window.localStorage.setItem("print-width", String(next));
    } catch {
      /* not persisted */
    }
  }

  if (!loading && !session) {
    return <p className="p-6 text-sm">Sign in to print. <Link className="underline" href={`/login?next=/print/${kind}/${orderId}`}>Staff sign-in</Link></p>;
  }

  const money = (value?: string | number | null) => Number(value ?? 0).toFixed(2);
  const ref = orderId.slice(0, 8).toUpperCase();
  const where = order
    ? order.type === "DINE_IN" ? `Table ${order.tableNumber ?? "-"}` : order.type === "PICKUP" ? `Pickup${order.pickupCode ? ` ${order.pickupCode}` : ""}` : "Delivery"
    : "";
  const time = (iso?: string | null) => (iso ? new Date(iso).toLocaleString(undefined, { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "");

  return (
    <>
      <style>{`
        @page { size: ${width}mm auto; margin: 3mm; }
        @media print { .no-print { display: none !important; } body { background: #fff !important; } }
        .slip { width: ${width === 58 ? 48 : 72}mm; font-family: ui-monospace, "IBM Plex Mono", monospace; font-size: ${width === 58 ? 11 : 12.5}px; line-height: 1.35; color: #000; }
        .slip .row { display: flex; justify-content: space-between; gap: 8px; }
        .slip .rule { border-top: 1px dashed #000; margin: 6px 0; }
        .slip .big { font-size: 1.45em; font-weight: 700; }
      `}</style>

      <div className="no-print flex flex-wrap items-center gap-2 border-b border-line bg-surface px-4 py-3">
        <button type="button" className="btn btn-primary btn-sm" onClick={() => window.print()} disabled={!order}>Print</button>
        <div className="segmented" role="group" aria-label="Paper width">
          <button type="button" aria-pressed={width === 80} onClick={() => chooseWidth(80)}>80 mm</button>
          <button type="button" aria-pressed={width === 58} onClick={() => chooseWidth(58)}>58 mm</button>
        </div>
        <button type="button" className="btn btn-ghost btn-sm" onClick={() => window.close()}>Close</button>
      </div>

      <div className="flex justify-center bg-white p-4">
        {error ? <p className="alert alert-danger">{error}</p> : null}
        {!order && !error ? <p className="text-sm">Loading…</p> : null}
        {order ? (
          <div className="slip">
            {kind === "receipt" ? (
              <>
                <div style={{ textAlign: "center" }}>
                  <div className="big">{brand.name}</div>
                  {brand.address ? <div>{brand.address}</div> : null}
                  {brand.phone ? <div>Tel {brand.phone}</div> : null}
                </div>
                <div className="rule" />
                <div className="row"><span>{receipt ? `Receipt ${receipt.receiptNumber}` : "Bill (not paid)"}</span></div>
                <div className="row"><span>Order #{ref}</span><span>{time(order.createdAt)}</span></div>
                <div>{where}</div>
                <div className="rule" />
                {order.items.map((item) => (
                  <div key={item.id}>
                    <div className="row"><span>{item.quantity} x {item.name}</span><span>{money(Number(item.price) * item.quantity)}</span></div>
                    {item.modifiers?.map((m) => <div key={m.id}>&nbsp;&nbsp;+ {m.optionName}</div>)}
                  </div>
                ))}
                <div className="rule" />
                <div className="row"><span>Subtotal</span><span>{money(order.subtotal)}</span></div>
                {Number(order.discountTotal ?? 0) > 0 ? <div className="row"><span>Discounts</span><span>-{money(order.discountTotal)}</span></div> : null}
                {Number(order.deliveryFee ?? 0) > 0 ? <div className="row"><span>Delivery</span><span>{money(order.deliveryFee)}</span></div> : null}
                {receipt && Number(receipt.serviceCharge ?? 0) > 0 ? <div className="row"><span>Service charge</span><span>{money(receipt.serviceCharge)}</span></div> : null}
                {receipt?.taxLines?.map((tax) => (
                  <div key={tax.code} className="row"><span>{tax.name}{receipt.taxInclusive ? " (incl.)" : ""}</span><span>{money(tax.amount)}</span></div>
                ))}
                {receipt && Number(receipt.tipAmount ?? 0) > 0 ? <div className="row"><span>Tip</span><span>{money(receipt.tipAmount)}</span></div> : null}
                <div className="row big"><span>TOTAL {brand.currency}</span><span>{money(order.total)}</span></div>
                {receipt ? (
                  <>
                    <div className="row"><span>Paid ({receipt.paymentMethod.replace("_", " ").toLowerCase()})</span><span>{money(receipt.amountPaid ?? receipt.total)}</span></div>
                    {receipt.paidAt ? <div className="row"><span>Paid at</span><span>{time(receipt.paidAt)}</span></div> : null}
                  </>
                ) : null}
                <div className="rule" />
                <div style={{ textAlign: "center" }}>{brand.receiptFooter ?? "Thank you!"}</div>
              </>
            ) : (
              <>
                <div style={{ textAlign: "center" }}>
                  <div className="big">{where.toUpperCase()}</div>
                  <div>#{ref} · {time(order.createdAt)}</div>
                </div>
                <div className="rule" />
                {order.items.map((item) => (
                  <div key={item.id} style={{ marginBottom: 6 }}>
                    <div className="big">{item.quantity} x {item.name}</div>
                    {item.modifiers?.map((m) => <div key={m.id}>&nbsp;&nbsp;{m.groupName}: {m.optionName}</div>)}
                    {item.notes ? <div style={{ fontWeight: 700 }}>&nbsp;&nbsp;! {item.notes}</div> : null}
                  </div>
                ))}
                {order.notes ? (<><div className="rule" /><div style={{ fontWeight: 700 }}>NOTE: {order.notes}</div></>) : null}
              </>
            )}
          </div>
        ) : null}
      </div>
    </>
  );
}
