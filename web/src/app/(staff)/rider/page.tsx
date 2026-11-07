"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  getRiderQueue,
  markDeliveryDelivered,
  markDeliveryPickedUp,
  sendRiderLocation,
  type DeliveryRecord,
} from "@/lib/apiClient";

const LOCATION_INTERVAL_MS = 15000;

function mapsLink(address?: string | null): string | null {
  return address ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}` : null;
}

export default function RiderPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [deliveries, setDeliveries] = useState<DeliveryRecord[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [sharing, setSharing] = useState(false);
  const lastSentRef = useRef(0);
  const watchIdRef = useRef<number | null>(null);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      setDeliveries(await authenticatedFetch((s) => getRiderQueue(s)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load deliveries");
    }
  }, [authenticatedFetch, session]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => window.clearInterval(timer);
  }, [load]);

  const activeDeliveries = deliveries.filter((d) => d.status === "OUT_FOR_DELIVERY" || d.status === "READY");

  useEffect(() => {
    if (!sharing) {
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
      return;
    }
    if (!("geolocation" in navigator)) {
      setError("This device cannot share its location");
      setSharing(false);
      return;
    }
    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        const now = Date.now();
        if (now - lastSentRef.current < LOCATION_INTERVAL_MS) return;
        lastSentRef.current = now;
        const { latitude, longitude } = position.coords;
        const targets = activeDeliveries.map((d) => d.orderId);
        void Promise.all(targets.map((orderId) =>
          authenticatedFetch((s) => sendRiderLocation(s, orderId, latitude, longitude)).catch(() => undefined),
        ));
      },
      (geoError) => {
        setError(`Location unavailable: ${geoError.message}`);
        setSharing(false);
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 },
    );
    return () => {
      if (watchIdRef.current !== null) navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    };
    // re-subscribe when the set of active deliveries changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sharing, activeDeliveries.map((d) => d.orderId).join(","), authenticatedFetch]);

  async function act(orderId: string, action: "pickup" | "delivered") {
    setBusyId(orderId);
    setError("");
    setMessage("");
    try {
      if (action === "pickup") {
        await authenticatedFetch((s) => markDeliveryPickedUp(s, orderId));
        setMessage("Marked as picked up. The customer has been notified.");
        setSharing(true);
      } else {
        await authenticatedFetch((s) => markDeliveryDelivered(s, orderId));
        setMessage("Delivery completed.");
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update delivery");
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <main className="shell"><section className="panel" aria-busy="true">Loading deliveries...</section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <h1 className="text-2xl font-semibold">Rider access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="kicker">Rider</p>
          <h1 className="text-2xl font-semibold">My deliveries</h1>
        </div>
        <label className="flex min-h-[var(--touch-md)] items-center gap-3 text-sm font-medium">
          <input type="checkbox" className="h-6 w-6" checked={sharing} onChange={(e) => setSharing(e.target.checked)} />
          Share live location with customers
        </label>
      </section>

      <div aria-live="polite">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        {message ? <p className="alert alert-success">{message}</p> : null}
      </div>

      {deliveries.length === 0 ? (
        <section className="panel text-ink-soft">No deliveries assigned to you right now.</section>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {deliveries.map((delivery) => {
            const link = mapsLink(delivery.deliveryAddress);
            return (
              <li key={delivery.orderId} className="panel grid gap-2">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-mono text-sm">#{delivery.orderId.slice(0, 8)}</p>
                  <span className="badge badge-info">{delivery.status.replaceAll("_", " ")}</span>
                </div>
                <p className="text-lg font-semibold">{delivery.deliveryAddress ?? "No address"}</p>
                {delivery.notes ? <p className="text-sm text-ink-soft">Note: {delivery.notes}</p> : null}
                <p className="text-sm">Collect: GHS {Number(delivery.total).toFixed(2)} (incl. delivery GHS {Number(delivery.deliveryFee).toFixed(2)})</p>
                <div className="flex flex-wrap gap-2">
                  {link ? <a className="btn btn-secondary" href={link} target="_blank" rel="noreferrer">Open in Maps</a> : null}
                  {delivery.customerPhone ? <a className="btn btn-secondary" href={`tel:${delivery.customerPhone}`}>Call customer</a> : null}
                  {delivery.status === "READY" ? (
                    <button type="button" className="btn btn-primary" disabled={busyId === delivery.orderId} onClick={() => void act(delivery.orderId, "pickup")}>
                      Picked up
                    </button>
                  ) : null}
                  {delivery.status === "OUT_FOR_DELIVERY" ? (
                    <button type="button" className="btn btn-primary" disabled={busyId === delivery.orderId} onClick={() => void act(delivery.orderId, "delivered")}>
                      Delivered
                    </button>
                  ) : null}
                </div>
                {delivery.status !== "READY" && delivery.status !== "OUT_FOR_DELIVERY" ? (
                  <p className="text-xs text-ink-soft">Waiting for the kitchen to mark this order ready.</p>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </main>
  );
}
