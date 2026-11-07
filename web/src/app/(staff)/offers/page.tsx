"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createMenuOffer,
  deleteMenuOffer,
  getMenuItems,
  getMenuOffers,
  setMenuOfferActive,
  updateMenuOffer,
  type MenuItemRecord,
  type MenuOfferRecord,
  type MenuOfferUpsertRequest,
} from "@/lib/apiClient";

type Draft = {
  id?: string;
  name: string;
  description: string;
  menuItemId: string;
  buyQuantity: string;
  freeQuantity: string;
  startsAt: string;
  endsAt: string;
};

const EMPTY: Draft = { name: "", description: "", menuItemId: "", buyQuantity: "2", freeQuantity: "1", startsAt: "", endsAt: "" };

function toLocalInput(iso?: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toPayload(draft: Draft): MenuOfferUpsertRequest {
  return {
    name: draft.name.trim(),
    description: draft.description.trim() || undefined,
    menuItemId: draft.menuItemId,
    buyQuantity: Number(draft.buyQuantity),
    freeQuantity: Number(draft.freeQuantity),
    startsAt: draft.startsAt ? new Date(draft.startsAt).toISOString() : undefined,
    endsAt: draft.endsAt ? new Date(draft.endsAt).toISOString() : undefined,
  };
}

export default function OffersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [offers, setOffers] = useState<MenuOfferRecord[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [nextOffers, nextItems] = await Promise.all([
        authenticatedFetch((s) => getMenuOffers(s)),
        authenticatedFetch((s) => getMenuItems(s)),
      ]);
      setOffers(nextOffers);
      setMenuItems(nextItems);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load offers");
    }
  }, [authenticatedFetch, session]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const payload = toPayload(draft);
      if (draft.id) {
        await authenticatedFetch((s) => updateMenuOffer(s, draft.id as string, payload));
        setMessage("Offer updated");
      } else {
        await authenticatedFetch((s) => createMenuOffer(s, payload));
        setMessage("Offer created");
      }
      setDraft(EMPTY);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save offer");
    } finally {
      setBusy(false);
    }
  }

  async function toggle(offer: MenuOfferRecord) {
    try {
      await authenticatedFetch((s) => setMenuOfferActive(s, offer.id, !offer.active));
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update offer");
    }
  }

  async function remove(offer: MenuOfferRecord) {
    try {
      await authenticatedFetch((s) => deleteMenuOffer(s, offer.id));
      if (draft.id === offer.id) setDraft(EMPTY);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete offer");
    }
  }

  if (loading) return <main className="shell"><section className="panel" aria-busy="true">Loading offers...</section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Offers</p>
          <h1 className="heading-lg mt-1">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      <form className="panel grid content-start gap-2" onSubmit={(e) => void save(e)}>
        <p className="kicker">Promotions</p>
        <h1 className="heading-lg mt-1">{draft.id ? "Edit offer" : "New Buy-X-Get-Y offer"}</h1>
        <p className="text-sm text-ink-soft">Active offers apply automatically at checkout: e.g. buy 2 get 1 makes every third unit free.</p>
        <label className="grid gap-1 text-sm">
          <span>Name</span>
          <input className="input" required maxLength={120} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <label className="grid gap-1 text-sm">
          <span>Menu item</span>
          <select className="select" required value={draft.menuItemId} onChange={(e) => setDraft({ ...draft, menuItemId: e.target.value })}>
            <option value="">Select an item</option>
            {menuItems.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </label>
        <div className="grid grid-cols-2 gap-2">
          <label className="grid gap-1 text-sm">
            <span>Buy</span>
            <input className="input" type="number" min={1} required value={draft.buyQuantity} onChange={(e) => setDraft({ ...draft, buyQuantity: e.target.value })} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>Get free</span>
            <input className="input" type="number" min={1} required value={draft.freeQuantity} onChange={(e) => setDraft({ ...draft, freeQuantity: e.target.value })} />
          </label>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <label className="grid gap-1 text-sm">
            <span>Starts (optional)</span>
            <input className="input" type="datetime-local" value={draft.startsAt} onChange={(e) => setDraft({ ...draft, startsAt: e.target.value })} />
          </label>
          <label className="grid gap-1 text-sm">
            <span>Ends (optional)</span>
            <input className="input" type="datetime-local" value={draft.endsAt} onChange={(e) => setDraft({ ...draft, endsAt: e.target.value })} />
          </label>
        </div>
        <label className="grid gap-1 text-sm">
          <span>Description</span>
          <textarea className="input" rows={2} value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
        </label>
        <div className="flex gap-2">
          <button type="submit" className="btn btn-primary" disabled={busy}>{draft.id ? "Save changes" : "Create offer"}</button>
          {draft.id ? <button type="button" className="btn btn-secondary" onClick={() => setDraft(EMPTY)}>Cancel</button> : null}
        </div>
        <div aria-live="polite">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="alert alert-success">{message}</p> : null}
        </div>
      </form>

      <section className="panel">
        <h2 className="heading-md">Offers</h2>
        {offers.length === 0 ? (
          <p className="mt-3 text-sm text-ink-soft">No offers yet. Create one to reward customers who order more.</p>
        ) : (
          <ul className="mt-3 grid gap-2">
            {offers.map((offer) => (
              <li key={offer.id} className="card flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-semibold">{offer.name}</p>
                  <p className="text-sm text-ink-soft">
                    {offer.menuItemName}: buy {offer.buyQuantity}, get {offer.freeQuantity} free
                  </p>
                  <span className={`badge ${offer.running ? "badge-success" : "badge-neutral"}`}>
                    {offer.running ? "Running" : offer.active ? "Scheduled / expired" : "Paused"}
                  </span>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => setDraft({
                      id: offer.id,
                      name: offer.name,
                      description: offer.description ?? "",
                      menuItemId: offer.menuItemId,
                      buyQuantity: String(offer.buyQuantity),
                      freeQuantity: String(offer.freeQuantity),
                      startsAt: toLocalInput(offer.startsAt),
                      endsAt: toLocalInput(offer.endsAt),
                    })}
                  >
                    Edit
                  </button>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => void toggle(offer)}>
                    {offer.active ? "Pause" : "Activate"}
                  </button>
                  <button type="button" className="btn btn-danger btn-sm" aria-label={`Delete offer ${offer.name}`} onClick={() => void remove(offer)}>
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
