"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { hasStaffRole, useStaffSession } from "@/components/SessionProvider";
import {
  assignRider,
  createDeliveryZone,
  getAllDeliveryZones,
  getOrders,
  getRiders,
  setDeliveryZoneActive,
  updateDeliveryZone,
  type DeliveryZoneRecord,
  type OrderRecord,
  type RiderRecord,
} from "@/lib/apiClient";

type ZoneDraft = { id?: string; name: string; fee: string; minOrderAmount: string; estimatedMinutes: string; description: string };
const EMPTY_ZONE: ZoneDraft = { name: "", fee: "", minOrderAmount: "0", estimatedMinutes: "45", description: "" };
const OPEN_STATUSES = new Set(["PENDING", "CONFIRMED", "PREPARING", "READY", "OUT_FOR_DELIVERY"]);

export default function DeliveryPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const canManageZones = hasStaffRole(session?.user.role, ["ADMIN", "MANAGER"]);
  const [orders, setOrders] = useState<OrderRecord[]>([]);
  const [riders, setRiders] = useState<RiderRecord[]>([]);
  const [zones, setZones] = useState<DeliveryZoneRecord[]>([]);
  const [selectedRider, setSelectedRider] = useState<Record<string, string>>({});
  const [zoneDraft, setZoneDraft] = useState<ZoneDraft>(EMPTY_ZONE);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [nextOrders, nextRiders] = await Promise.all([
        authenticatedFetch((s) => getOrders(s)),
        authenticatedFetch((s) => getRiders(s)),
      ]);
      setOrders(nextOrders);
      setRiders(nextRiders);
      if (canManageZones) setZones(await authenticatedFetch((s) => getAllDeliveryZones(s)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load deliveries");
    }
  }, [authenticatedFetch, canManageZones, session]);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 20000);
    return () => window.clearInterval(timer);
  }, [load]);

  const deliveries = useMemo(
    () => orders.filter((order) => order.type === "DELIVERY" && OPEN_STATUSES.has(order.status)),
    [orders],
  );
  const riderName = (id?: string | null) => riders.find((r) => r.id === id)?.name ?? null;

  async function assign(orderId: string) {
    const riderId = selectedRider[orderId];
    if (!riderId) return;
    setError("");
    try {
      const result = await authenticatedFetch((s) => assignRider(s, orderId, riderId));
      setMessage(`Assigned ${result.riderName} to order ${orderId.slice(0, 8)}`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not assign rider");
    }
  }

  async function saveZone(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    const payload = {
      name: zoneDraft.name.trim(),
      description: zoneDraft.description.trim() || undefined,
      fee: Number(zoneDraft.fee),
      minOrderAmount: Number(zoneDraft.minOrderAmount || 0),
      estimatedMinutes: Number(zoneDraft.estimatedMinutes || 45),
    };
    try {
      if (zoneDraft.id) {
        await authenticatedFetch((s) => updateDeliveryZone(s, zoneDraft.id as string, payload));
      } else {
        await authenticatedFetch((s) => createDeliveryZone(s, payload));
      }
      setZoneDraft(EMPTY_ZONE);
      setMessage("Delivery zone saved");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save zone");
    }
  }

  if (loading) return <main className="shell"><section className="panel" aria-busy="true">Loading deliveries...</section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <h1 className="heading-lg mt-1">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 xl:grid-cols-[1.6fr_1fr]">
      <section className="panel">
        <p className="kicker">Delivery</p>
        <h1 className="heading-lg mt-1">Dispatch board</h1>
        <div aria-live="polite">
          {error ? <p className="mt-3 alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
        </div>
        {deliveries.length === 0 ? (
          <p className="mt-4 text-sm text-ink-soft">No open delivery orders.</p>
        ) : (
          <ul className="mt-4 grid gap-3">
            {deliveries.map((order) => (
              <li key={order.id} className="card grid gap-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <Link href={`/orders/${order.id}`} className="font-mono text-sm underline">#{order.id.slice(0, 8)}</Link>
                  <span className="badge badge-info">{order.status.replaceAll("_", " ")}</span>
                </div>
                <p className="font-medium">{order.deliveryAddress}</p>
                <p className="text-sm text-ink-soft">
                  Total GHS {Number(order.total).toFixed(2)}
                  {order.deliveryFee ? ` · delivery GHS ${Number(order.deliveryFee).toFixed(2)}` : ""}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  {order.riderUserId ? <span className="badge badge-success">Rider: {riderName(order.riderUserId) ?? "assigned"}</span> : null}
                  {order.status !== "OUT_FOR_DELIVERY" ? (
                    <>
                      <select
                        className="select"
                        aria-label={`Rider for order ${order.id.slice(0, 8)}`}
                        value={selectedRider[order.id] ?? ""}
                        onChange={(e) => setSelectedRider({ ...selectedRider, [order.id]: e.target.value })}
                      >
                        <option value="">{order.riderUserId ? "Reassign rider" : "Choose rider"}</option>
                        {riders.map((rider) => (
                          <option key={rider.id} value={rider.id}>{rider.name} ({rider.activeDeliveries} active)</option>
                        ))}
                      </select>
                      <button type="button" className="btn btn-primary" disabled={!selectedRider[order.id]} onClick={() => void assign(order.id)}>
                        Assign
                      </button>
                    </>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
        {riders.length === 0 ? (
          <p className="mt-4 text-sm text-ink-soft">No riders yet. Give a staff account the RIDER role on the Users page.</p>
        ) : null}
      </section>

      {canManageZones ? (
        <section className="panel grid content-start gap-3">
          <h2 className="heading-md">Delivery zones</h2>
          <form className="grid gap-2" onSubmit={(e) => void saveZone(e)}>
            <input className="input" required placeholder="Zone name (e.g. East Legon)" aria-label="Zone name" value={zoneDraft.name} onChange={(e) => setZoneDraft({ ...zoneDraft, name: e.target.value })} />
            <div className="grid grid-cols-3 gap-2">
              <input className="input" required type="number" min="0" step="0.01" placeholder="Fee" aria-label="Delivery fee" value={zoneDraft.fee} onChange={(e) => setZoneDraft({ ...zoneDraft, fee: e.target.value })} />
              <input className="input" type="number" min="0" step="0.01" placeholder="Min order" aria-label="Minimum order" value={zoneDraft.minOrderAmount} onChange={(e) => setZoneDraft({ ...zoneDraft, minOrderAmount: e.target.value })} />
              <input className="input" type="number" min="5" max="600" placeholder="ETA (min)" aria-label="Estimated minutes" value={zoneDraft.estimatedMinutes} onChange={(e) => setZoneDraft({ ...zoneDraft, estimatedMinutes: e.target.value })} />
            </div>
            <input className="input" placeholder="Description" aria-label="Zone description" value={zoneDraft.description} onChange={(e) => setZoneDraft({ ...zoneDraft, description: e.target.value })} />
            <div className="flex gap-2">
              <button type="submit" className="btn btn-primary">{zoneDraft.id ? "Save zone" : "Add zone"}</button>
              {zoneDraft.id ? <button type="button" className="btn btn-secondary" onClick={() => setZoneDraft(EMPTY_ZONE)}>Cancel</button> : null}
            </div>
          </form>
          <ul className="grid gap-2">
            {zones.map((zone) => (
              <li key={zone.id} className="card flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-semibold">{zone.name}</p>
                  <p className="text-sm text-ink-soft">GHS {Number(zone.fee).toFixed(2)} · min GHS {Number(zone.minOrderAmount).toFixed(2)} · ~{zone.estimatedMinutes} min</p>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => setZoneDraft({
                      id: zone.id,
                      name: zone.name,
                      fee: String(zone.fee),
                      minOrderAmount: String(zone.minOrderAmount),
                      estimatedMinutes: String(zone.estimatedMinutes),
                      description: zone.description ?? "",
                    })}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => void authenticatedFetch((s) => setDeliveryZoneActive(s, zone.id, !zone.active)).then(load)}
                  >
                    {zone.active ? "Disable" : "Enable"}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </main>
  );
}
