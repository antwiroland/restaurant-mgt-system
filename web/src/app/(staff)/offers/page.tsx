"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createMenuOffer,
  deleteMenuOffer,
  getBranches,
  getMenuItems,
  getMenuOffers,
  setMenuOfferActive,
  updateMenuOffer,
  type BranchRecord,
  type MenuItemRecord,
  type MenuOfferRecord,
  type MenuOfferUpsertRequest,
  type OfferType,
} from "@/lib/apiClient";

const TYPES: { value: OfferType; label: string; hint: string }[] = [
  { value: "BUY_X_GET_Y", label: "Buy X, get more free", hint: "Buy 2 Kelewele, get 1 free: every third one is free." },
  { value: "BUY_X_GET_OTHER", label: "Buy one dish, get another free", hint: "Buy a burger, get a soda free." },
  { value: "PERCENT_OFF", label: "Percentage off a dish", hint: "20% off every Jollof." },
  { value: "COMBO", label: "Combo price", hint: "Burger + fries + soda together for one price." },
];

const DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

type ComboLine = { menuItemId: string; quantity: string };

type Draft = {
  id?: string;
  name: string;
  description: string;
  offerType: OfferType;
  menuItemId: string;
  buyQuantity: string;
  freeQuantity: string;
  freeMenuItemId: string;
  percentOff: string;
  comboPrice: string;
  comboItems: ComboLine[];
  startsAt: string;
  endsAt: string;
  days: string[];
  dailyStartTime: string;
  dailyEndTime: string;
  branchId: string;
};

const EMPTY: Draft = {
  name: "",
  description: "",
  offerType: "BUY_X_GET_Y",
  menuItemId: "",
  buyQuantity: "2",
  freeQuantity: "1",
  freeMenuItemId: "",
  percentOff: "10",
  comboPrice: "",
  comboItems: [{ menuItemId: "", quantity: "1" }, { menuItemId: "", quantity: "1" }],
  startsAt: "",
  endsAt: "",
  days: [],
  dailyStartTime: "",
  dailyEndTime: "",
  branchId: "",
};

function toLocalInput(iso?: string | null): string {
  if (!iso) return "";
  const date = new Date(iso);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toPayload(draft: Draft): MenuOfferUpsertRequest {
  const base: MenuOfferUpsertRequest = {
    name: draft.name.trim(),
    description: draft.description.trim() || undefined,
    offerType: draft.offerType,
    startsAt: draft.startsAt ? new Date(draft.startsAt).toISOString() : undefined,
    endsAt: draft.endsAt ? new Date(draft.endsAt).toISOString() : undefined,
    daysOfWeek: draft.days.length > 0 && draft.days.length < 7 ? draft.days : undefined,
    dailyStartTime: draft.dailyStartTime || undefined,
    dailyEndTime: draft.dailyEndTime || undefined,
    branchId: draft.branchId || undefined,
  };
  switch (draft.offerType) {
    case "BUY_X_GET_Y":
      return { ...base, menuItemId: draft.menuItemId, buyQuantity: Number(draft.buyQuantity), freeQuantity: Number(draft.freeQuantity) };
    case "BUY_X_GET_OTHER":
      return {
        ...base,
        menuItemId: draft.menuItemId,
        buyQuantity: Number(draft.buyQuantity),
        freeMenuItemId: draft.freeMenuItemId,
        freeQuantity: Number(draft.freeQuantity),
      };
    case "PERCENT_OFF":
      return { ...base, menuItemId: draft.menuItemId, percentOff: Number(draft.percentOff) };
    case "COMBO":
      return {
        ...base,
        comboPrice: Number(draft.comboPrice),
        comboItems: draft.comboItems
          .filter((line) => line.menuItemId)
          .map((line) => ({ menuItemId: line.menuItemId, quantity: Number(line.quantity) || 1 })),
      };
  }
}

function toDraft(offer: MenuOfferRecord): Draft {
  return {
    id: offer.id,
    name: offer.name,
    description: offer.description ?? "",
    offerType: offer.offerType,
    menuItemId: offer.menuItemId ?? "",
    buyQuantity: String(offer.buyQuantity || 2),
    freeQuantity: String(offer.freeQuantity || 1),
    freeMenuItemId: offer.freeMenuItemId ?? "",
    percentOff: offer.percentOff ? String(Number(offer.percentOff)) : "10",
    comboPrice: offer.comboPrice ? String(Number(offer.comboPrice)) : "",
    comboItems: offer.comboItems.length > 0
      ? offer.comboItems.map((item) => ({ menuItemId: item.menuItemId, quantity: String(item.quantity) }))
      : EMPTY.comboItems,
    startsAt: toLocalInput(offer.startsAt),
    endsAt: toLocalInput(offer.endsAt),
    days: offer.daysOfWeek ?? [],
    dailyStartTime: offer.dailyStartTime?.slice(0, 5) ?? "",
    dailyEndTime: offer.dailyEndTime?.slice(0, 5) ?? "",
    branchId: offer.branchId ?? "",
  };
}

function status(offer: MenuOfferRecord): { label: string; tone: string } {
  if (!offer.active) return { label: "Paused", tone: "badge-neutral" };
  if (offer.running) return { label: "Running now", tone: "badge-success" };
  if (offer.endsAt && new Date(offer.endsAt) < new Date()) return { label: "Ended", tone: "badge-neutral" };
  return { label: "Scheduled", tone: "badge-neutral" };
}

export default function OffersPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [offers, setOffers] = useState<MenuOfferRecord[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const canEdit = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";

  const load = useCallback(async () => {
    if (!session) return;
    try {
      const [nextOffers, nextItems, nextBranches] = await Promise.all([
        authenticatedFetch((s) => getMenuOffers(s)),
        authenticatedFetch((s) => getMenuItems(s)),
        authenticatedFetch((s) => getBranches(s)).catch(() => [] as BranchRecord[]),
      ]);
      setOffers(nextOffers);
      setMenuItems(nextItems);
      setBranches(nextBranches.filter((branch) => branch.active));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load offers");
    }
  }, [authenticatedFetch, session]);

  useEffect(() => {
    void load();
  }, [load]);

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft((current) => ({ ...current, [key]: value }));

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
    if (!window.confirm(`Delete the offer "${offer.name}"?`)) return;
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

  const dishSelect = (value: string, onChange: (value: string) => void, label: string) => (
    <label className="grid gap-1 text-sm">
      <span>{label}</span>
      <select className="select" required value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Select a dish</option>
        {menuItems.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
      </select>
    </label>
  );

  const typeHint = TYPES.find((type) => type.value === draft.offerType)?.hint;

  return (
    <main className="shell grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      {canEdit ? (
        <form className="panel grid content-start gap-3" onSubmit={(e) => void save(e)}>
          <p className="kicker">Promotions</p>
          <h1 className="heading-lg mt-1">{draft.id ? "Edit offer" : "New offer"}</h1>
          <p className="text-sm text-ink-soft">
            Running offers apply automatically at checkout, show on the menu, and customers are told when one more dish would complete a deal.
          </p>

          <label className="grid gap-1 text-sm">
            <span>Name</span>
            <input className="input" required maxLength={120} value={draft.name} onChange={(e) => set("name", e.target.value)} />
          </label>

          <label className="grid gap-1 text-sm">
            <span>Kind of offer</span>
            <select className="select" value={draft.offerType} onChange={(e) => set("offerType", e.target.value as OfferType)}>
              {TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
            </select>
            {typeHint ? <span className="text-xs text-ink-soft">{typeHint}</span> : null}
          </label>

          {draft.offerType === "BUY_X_GET_Y" ? (
            <>
              {dishSelect(draft.menuItemId, (v) => set("menuItemId", v), "Dish")}
              <div className="grid grid-cols-2 gap-2">
                <label className="grid gap-1 text-sm">
                  <span>Buy</span>
                  <input className="input" type="number" min={1} required value={draft.buyQuantity} onChange={(e) => set("buyQuantity", e.target.value)} />
                </label>
                <label className="grid gap-1 text-sm">
                  <span>Get free</span>
                  <input className="input" type="number" min={1} required value={draft.freeQuantity} onChange={(e) => set("freeQuantity", e.target.value)} />
                </label>
              </div>
            </>
          ) : null}

          {draft.offerType === "BUY_X_GET_OTHER" ? (
            <>
              <div className="grid grid-cols-[1fr_6rem] gap-2">
                {dishSelect(draft.menuItemId, (v) => set("menuItemId", v), "Buy this dish")}
                <label className="grid gap-1 text-sm">
                  <span>How many</span>
                  <input className="input" type="number" min={1} required value={draft.buyQuantity} onChange={(e) => set("buyQuantity", e.target.value)} />
                </label>
              </div>
              <div className="grid grid-cols-[1fr_6rem] gap-2">
                {dishSelect(draft.freeMenuItemId, (v) => set("freeMenuItemId", v), "Get this dish free")}
                <label className="grid gap-1 text-sm">
                  <span>How many</span>
                  <input className="input" type="number" min={1} required value={draft.freeQuantity} onChange={(e) => set("freeQuantity", e.target.value)} />
                </label>
              </div>
            </>
          ) : null}

          {draft.offerType === "PERCENT_OFF" ? (
            <div className="grid grid-cols-[1fr_7rem] gap-2">
              {dishSelect(draft.menuItemId, (v) => set("menuItemId", v), "Dish")}
              <label className="grid gap-1 text-sm">
                <span>% off</span>
                <input className="input" type="number" min={1} max={100} step="0.5" required value={draft.percentOff} onChange={(e) => set("percentOff", e.target.value)} />
              </label>
            </div>
          ) : null}

          {draft.offerType === "COMBO" ? (
            <fieldset className="grid gap-2 rounded-xl border border-line p-3">
              <legend className="px-1 text-sm font-semibold">Dishes in the combo</legend>
              {draft.comboItems.map((line, index) => (
                <div key={index} className="grid grid-cols-[1fr_5rem_auto] items-end gap-2">
                  {dishSelect(line.menuItemId, (v) => set("comboItems", draft.comboItems.map((l, i) => (i === index ? { ...l, menuItemId: v } : l))), `Dish ${index + 1}`)}
                  <label className="grid gap-1 text-sm">
                    <span>Qty</span>
                    <input
                      className="input"
                      type="number"
                      min={1}
                      value={line.quantity}
                      onChange={(e) => set("comboItems", draft.comboItems.map((l, i) => (i === index ? { ...l, quantity: e.target.value } : l)))}
                    />
                  </label>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    aria-label={`Remove dish ${index + 1}`}
                    disabled={draft.comboItems.length <= 2}
                    onClick={() => set("comboItems", draft.comboItems.filter((_, i) => i !== index))}
                  >
                    Remove
                  </button>
                </div>
              ))}
              <button type="button" className="btn btn-secondary btn-sm w-fit" onClick={() => set("comboItems", [...draft.comboItems, { menuItemId: "", quantity: "1" }])}>
                Add a dish
              </button>
              <label className="grid gap-1 text-sm">
                <span>Combo price</span>
                <input className="input" type="number" min={0} step="0.01" required value={draft.comboPrice} onChange={(e) => set("comboPrice", e.target.value)} />
              </label>
            </fieldset>
          ) : null}

          <fieldset className="grid gap-2 rounded-xl border border-line p-3">
            <legend className="px-1 text-sm font-semibold">When it runs</legend>
            <div className="grid grid-cols-2 gap-2">
              <label className="grid gap-1 text-sm">
                <span>From date (optional)</span>
                <input className="input" type="datetime-local" value={draft.startsAt} onChange={(e) => set("startsAt", e.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Until date (optional)</span>
                <input className="input" type="datetime-local" value={draft.endsAt} onChange={(e) => set("endsAt", e.target.value)} />
              </label>
            </div>
            <div className="text-sm">
              <span>Days (none ticked = every day)</span>
              <div className="mt-1 flex flex-wrap gap-2">
                {DAYS.map((day) => (
                  <button
                    key={day}
                    type="button"
                    className="chip"
                    aria-pressed={draft.days.includes(day)}
                    onClick={() => set("days", draft.days.includes(day) ? draft.days.filter((d) => d !== day) : [...draft.days, day])}
                  >
                    {day.charAt(0) + day.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <label className="grid gap-1 text-sm">
                <span>Daily from (happy hour)</span>
                <input className="input" type="time" value={draft.dailyStartTime} onChange={(e) => set("dailyStartTime", e.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Daily until</span>
                <input className="input" type="time" value={draft.dailyEndTime} onChange={(e) => set("dailyEndTime", e.target.value)} />
              </label>
            </div>
            <p className="text-xs text-ink-soft">Leave both times empty for all day. An end before the start runs past midnight.</p>
          </fieldset>

          <label className="grid gap-1 text-sm">
            <span>Branch</span>
            <select className="select" value={draft.branchId} onChange={(e) => set("branchId", e.target.value)}>
              <option value="">Every branch</option>
              {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
            </select>
          </label>

          <label className="grid gap-1 text-sm">
            <span>Description (shown to customers)</span>
            <textarea className="input" rows={2} value={draft.description} onChange={(e) => set("description", e.target.value)} />
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
      ) : (
        <section className="panel">
          <p className="kicker">Promotions</p>
          <h1 className="heading-lg mt-1">Offers</h1>
          <p className="mt-2 text-sm text-ink-soft">Running offers apply automatically at checkout. Ask a manager to change them.</p>
        </section>
      )}

      <section className="panel">
        <h2 className="heading-md">Offers</h2>
        {offers.length === 0 ? (
          <p className="mt-3 text-sm text-ink-soft">No offers yet. Create one to reward customers who order more.</p>
        ) : (
          <ul className="mt-3 grid gap-2">
            {offers.map((offer) => {
              const state = status(offer);
              return (
                <li key={offer.id} className="card flex flex-wrap items-center justify-between gap-3">
                  <div className="grid gap-1">
                    <p className="font-semibold">{offer.name}</p>
                    <p className="text-sm text-ink-soft">{offer.summary}</p>
                    <p className="text-xs text-ink-soft">
                      {offer.schedule ?? "All day, every day"}
                      {offer.branchName ? ` · ${offer.branchName} only` : " · Every branch"}
                    </p>
                    <span className={`badge w-fit ${state.tone}`}>{state.label}</span>
                  </div>
                  {canEdit ? (
                    <div className="flex flex-wrap gap-2">
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setDraft(toDraft(offer))}>Edit</button>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => void toggle(offer)}>
                        {offer.active ? "Pause" : "Activate"}
                      </button>
                      <button type="button" className="btn btn-danger btn-sm" aria-label={`Delete offer ${offer.name}`} onClick={() => void remove(offer)}>
                        Delete
                      </button>
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </main>
  );
}
