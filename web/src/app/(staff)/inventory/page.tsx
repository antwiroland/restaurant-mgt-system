"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  adjustIngredient,
  createIngredient,
  getIngredients,
  getStockMovements,
  restockIngredient,
  updateIngredient,
  type IngredientRecord,
  type StockMovementRecord,
} from "@/lib/apiClient";

type IngredientDraft = {
  id?: string;
  name: string;
  unit: string;
  lowStockThreshold: string;
  costPerUnit: string;
  initialStock: string;
  active: boolean;
};

type StockAction = "RESTOCK" | "WASTE" | "COUNT";

const EMPTY: IngredientDraft = { name: "", unit: "kg", lowStockThreshold: "0", costPerUnit: "", initialStock: "0", active: true };

const REASON_LABEL: Record<StockMovementRecord["reason"], string> = {
  RESTOCK: "Restock",
  ORDER: "Used by order",
  ORDER_CANCELLED: "Order cancelled",
  WASTE: "Waste",
  ADJUSTMENT: "Stock count",
};

function qty(value: string | number): string {
  return Number(Number(value).toFixed(3)).toString();
}

export default function InventoryPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [ingredients, setIngredients] = useState<IngredientRecord[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [draft, setDraft] = useState<IngredientDraft | null>(null);
  const [filter, setFilter] = useState<"ALL" | "LOW">("ALL");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<IngredientRecord | null>(null);
  const [movements, setMovements] = useState<StockMovementRecord[]>([]);
  const [action, setAction] = useState<StockAction>("RESTOCK");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const canManage = session?.user.role === "ADMIN" || session?.user.role === "MANAGER";

  const load = useCallback(async () => {
    if (!session) return;
    try {
      setIngredients(await authenticatedFetch((s) => getIngredients(s)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load inventory");
    } finally {
      setLoaded(true);
    }
  }, [authenticatedFetch, session]);

  useEffect(() => {
    void load();
  }, [load]);

  const lowCount = ingredients.filter((i) => i.low).length;
  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    return ingredients
      .filter((i) => (filter === "LOW" ? i.low : true))
      .filter((i) => (term ? i.name.toLowerCase().includes(term) : true))
      .sort((a, b) => Number(b.low) - Number(a.low) || a.name.localeCompare(b.name));
  }, [ingredients, filter, search]);

  async function openIngredient(ingredient: IngredientRecord) {
    setSelected(ingredient);
    setAction("RESTOCK");
    setAmount("");
    setNote("");
    setMovements([]);
    if (!canManage) return;
    try {
      setMovements(await authenticatedFetch((s) => getStockMovements(s, ingredient.id)));
    } catch {
      // History is optional; the drawer still works without it.
    }
  }

  async function saveIngredient(event: React.FormEvent) {
    event.preventDefault();
    if (!draft) return;
    setBusy(true);
    setError("");
    setMessage("");
    const payload = {
      name: draft.name.trim(),
      unit: draft.unit.trim(),
      lowStockThreshold: Number(draft.lowStockThreshold || 0),
      costPerUnit: draft.costPerUnit === "" ? undefined : Number(draft.costPerUnit),
      initialStock: draft.id ? undefined : Number(draft.initialStock || 0),
      active: draft.active,
    };
    try {
      if (draft.id) {
        await authenticatedFetch((s) => updateIngredient(s, draft.id as string, payload));
        setMessage(`${payload.name} updated`);
      } else {
        await authenticatedFetch((s) => createIngredient(s, payload));
        setMessage(`${payload.name} added`);
      }
      setDraft(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save ingredient");
    } finally {
      setBusy(false);
    }
  }

  async function applyStock(event: React.FormEvent) {
    event.preventDefault();
    if (!selected) return;
    const value = Number(amount);
    if (!Number.isFinite(value) || value < 0 || (action !== "COUNT" && value === 0)) {
      setError("Enter a quantity greater than zero");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      let next: IngredientRecord;
      if (action === "RESTOCK") {
        next = await authenticatedFetch((s) => restockIngredient(s, selected.id, value, note.trim() || undefined));
      } else if (action === "WASTE") {
        next = await authenticatedFetch((s) => adjustIngredient(s, selected.id, { reason: "WASTE", delta: -value, note: note.trim() || undefined }));
      } else {
        next = await authenticatedFetch((s) =>
          adjustIngredient(s, selected.id, { reason: "ADJUSTMENT", countedQuantity: value, note: note.trim() || undefined }),
        );
      }
      setMessage(`${next.name}: ${qty(next.stockQuantity)} ${next.unit} in stock`);
      setAmount("");
      setNote("");
      await load();
      await openIngredient(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update stock");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-48 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Inventory</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel grid gap-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="kicker">Inventory</p>
            <h1 className="text-2xl font-semibold">Ingredients and stock</h1>
            <p className="text-sm text-ink-soft">
              Stock goes down as orders are placed, using each menu item&apos;s recipe (set on the Menu page). Items run out
              automatically when an ingredient hits zero.
            </p>
          </div>
          {canManage ? (
            <button type="button" className="btn btn-primary" onClick={() => setDraft(EMPTY)}>Add ingredient</button>
          ) : null}
        </div>

        {lowCount > 0 ? (
          <p className="alert alert-warning" role="status">
            {lowCount} ingredient{lowCount === 1 ? " is" : "s are"} at or below the low-stock level.{" "}
            <button type="button" className="underline" onClick={() => setFilter("LOW")}>Show them</button>
          </p>
        ) : null}

        <div className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-sm">
            <span>Search</span>
            <input className="input" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Ingredient name" />
          </label>
          <div className="flex gap-1" role="group" aria-label="Filter">
            {(["ALL", "LOW"] as const).map((value) => (
              <button
                key={value}
                type="button"
                className={`btn btn-sm ${filter === value ? "btn-primary" : "btn-secondary"}`}
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
              >
                {value === "ALL" ? `All (${ingredients.length})` : `Low (${lowCount})`}
              </button>
            ))}
          </div>
        </div>

        <div aria-live="polite">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="alert alert-success">{message}</p> : null}
        </div>
      </section>

      <section className="panel">
        {!loaded ? (
          <Skeleton className="h-40 w-full" />
        ) : visible.length === 0 ? (
          <p className="text-sm text-ink-soft">
            {ingredients.length === 0 ? "No ingredients yet. Add the ingredients you want to track." : "Nothing matches this filter."}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-ink-soft">
                <tr>
                  <th scope="col" className="py-2 pr-3">Ingredient</th>
                  <th scope="col" className="py-2 pr-3">In stock</th>
                  <th scope="col" className="py-2 pr-3">Low at</th>
                  <th scope="col" className="py-2 pr-3">Cost / unit</th>
                  <th scope="col" className="py-2 pr-3">Status</th>
                  <th scope="col" className="py-2"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {visible.map((ingredient) => (
                  <tr key={ingredient.id} className="border-t border-line">
                    <td className="py-2 pr-3 font-medium">{ingredient.name}</td>
                    <td className="py-2 pr-3">{qty(ingredient.stockQuantity)} {ingredient.unit}</td>
                    <td className="py-2 pr-3">{qty(ingredient.lowStockThreshold)} {ingredient.unit}</td>
                    <td className="py-2 pr-3">{ingredient.costPerUnit ? `GHS ${Number(ingredient.costPerUnit).toFixed(2)}` : "-"}</td>
                    <td className="py-2 pr-3">
                      {!ingredient.active ? (
                        <span className="badge badge-neutral">Inactive</span>
                      ) : Number(ingredient.stockQuantity) <= 0 ? (
                        <span className="badge badge-danger">Out</span>
                      ) : ingredient.low ? (
                        <span className="badge badge-warning">Low</span>
                      ) : (
                        <span className="badge badge-success">OK</span>
                      )}
                    </td>
                    <td className="py-2">
                      <div className="flex justify-end gap-2">
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => void openIngredient(ingredient)}>
                          Stock
                        </button>
                        {canManage ? (
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() =>
                              setDraft({
                                id: ingredient.id,
                                name: ingredient.name,
                                unit: ingredient.unit,
                                lowStockThreshold: qty(ingredient.lowStockThreshold),
                                costPerUnit: ingredient.costPerUnit ? String(Number(ingredient.costPerUnit)) : "",
                                initialStock: "0",
                                active: ingredient.active,
                              })
                            }
                          >
                            Edit
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Drawer open={draft !== null} title={draft?.id ? "Edit ingredient" : "Add ingredient"} onClose={() => setDraft(null)}>
        {draft ? (
          <form className="grid gap-2" onSubmit={(e) => void saveIngredient(e)}>
            <label className="grid gap-1 text-sm">
              <span>Name</span>
              <input className="input" required maxLength={120} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </label>
            <label className="grid gap-1 text-sm">
              <span>Unit</span>
              <input className="input" required maxLength={20} list="inventory-units" value={draft.unit} onChange={(e) => setDraft({ ...draft, unit: e.target.value })} />
              <datalist id="inventory-units">
                {["kg", "g", "l", "ml", "pcs", "pack", "crate"].map((u) => <option key={u} value={u} />)}
              </datalist>
            </label>
            <div className="grid grid-cols-2 gap-2">
              <label className="grid gap-1 text-sm">
                <span>Low-stock level</span>
                <input className="input" type="number" min={0} step="0.001" value={draft.lowStockThreshold} onChange={(e) => setDraft({ ...draft, lowStockThreshold: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Cost per unit (GHS)</span>
                <input className="input" type="number" min={0} step="0.01" value={draft.costPerUnit} onChange={(e) => setDraft({ ...draft, costPerUnit: e.target.value })} />
              </label>
            </div>
            {!draft.id ? (
              <label className="grid gap-1 text-sm">
                <span>Opening stock</span>
                <input className="input" type="number" min={0} step="0.001" value={draft.initialStock} onChange={(e) => setDraft({ ...draft, initialStock: e.target.value })} />
              </label>
            ) : null}
            <label className="flex min-h-[var(--touch-md)] items-center gap-2 text-sm">
              <input type="checkbox" className="h-5 w-5" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              Track this ingredient
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>{draft.id ? "Save" : "Add ingredient"}</button>
          </form>
        ) : null}
      </Drawer>

      <Drawer open={selected !== null} title={selected ? `${selected.name} stock` : "Stock"} onClose={() => setSelected(null)}>
        {selected ? (
          <div className="grid gap-4">
            <p className="text-sm">
              <span className="text-2xl font-semibold">{qty(selected.stockQuantity)}</span> {selected.unit} in stock · low at{" "}
              {qty(selected.lowStockThreshold)} {selected.unit}
            </p>
            <form className="grid gap-2" onSubmit={(e) => void applyStock(e)}>
              <div className="flex flex-wrap gap-1" role="group" aria-label="Stock action">
                {(["RESTOCK", "WASTE", "COUNT"] as const)
                  .filter((value) => value === "RESTOCK" || canManage)
                  .map((value) => (
                    <button
                      key={value}
                      type="button"
                      className={`btn btn-sm ${action === value ? "btn-primary" : "btn-secondary"}`}
                      aria-pressed={action === value}
                      onClick={() => setAction(value)}
                    >
                      {value === "RESTOCK" ? "Restock" : value === "WASTE" ? "Record waste" : "Stock count"}
                    </button>
                  ))}
              </div>
              <label className="grid gap-1 text-sm">
                <span>
                  {action === "RESTOCK" ? "Quantity received" : action === "WASTE" ? "Quantity wasted" : "Quantity counted on the shelf"} ({selected.unit})
                </span>
                <input className="input" type="number" min={0} step="0.001" required value={amount} onChange={(e) => setAmount(e.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Note (optional)</span>
                <input className="input" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} placeholder={action === "RESTOCK" ? "Supplier, invoice no." : "Reason"} />
              </label>
              <button type="submit" className="btn btn-primary" disabled={busy}>Save</button>
            </form>

            {canManage ? (
              <section>
                <h3 className="font-semibold">Recent movements</h3>
                {movements.length === 0 ? (
                  <p className="text-sm text-ink-soft">No movements yet.</p>
                ) : (
                  <ul className="mt-2 grid gap-1 text-sm">
                    {movements.slice(0, 30).map((m) => (
                      <li key={m.id} className="flex justify-between gap-2 border-b border-line py-1">
                        <span>
                          {REASON_LABEL[m.reason]}
                          {m.note ? <span className="text-ink-soft"> · {m.note}</span> : null}
                          <span className="block text-xs text-ink-soft">{new Date(m.createdAt).toLocaleString()}</span>
                        </span>
                        <span className={Number(m.changeQuantity) < 0 ? "text-danger-on" : "text-success-on"}>
                          {Number(m.changeQuantity) > 0 ? "+" : ""}{qty(m.changeQuantity)} → {qty(m.balanceAfter)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ) : null}
          </div>
        ) : null}
      </Drawer>
    </main>
  );
}
