"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Drawer } from "@/components/Drawer";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import {
  adjustIngredient,
  createIngredient,
  getBranches,
  getFoodCostReport,
  getIngredients,
  getStockMovements,
  restockIngredient,
  updateIngredient,
  type BranchRecord,
  type FoodCostRecord,
  type IngredientRecord,
  type StockMovementRecord,
} from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";

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

const PERIODS = [
  { days: 7, label: "Last 7 days" },
  { days: 30, label: "Last 30 days" },
  { days: 90, label: "Last 90 days" },
];

function qty(value: string | number): string {
  return Number(Number(value).toFixed(3)).toString();
}

function money(value?: string | number | null): string {
  return value === undefined || value === null ? "—" : formatMoney(Number(value));
}

export default function InventoryPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [ingredients, setIngredients] = useState<IngredientRecord[]>([]);
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [branchId, setBranchId] = useState(""); // "" = all branches (admins) or the user's own branch (staff)
  const [loaded, setLoaded] = useState(false);
  const [draft, setDraft] = useState<IngredientDraft | null>(null);
  const [filter, setFilter] = useState<"ALL" | "LOW">("ALL");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<IngredientRecord | null>(null);
  const [movements, setMovements] = useState<StockMovementRecord[]>([]);
  const [action, setAction] = useState<StockAction>("RESTOCK");
  const [actionBranchId, setActionBranchId] = useState("");
  const [amount, setAmount] = useState("");
  const [unitCost, setUnitCost] = useState("");
  const [supplier, setSupplier] = useState("");
  const [note, setNote] = useState("");
  const [report, setReport] = useState<FoodCostRecord | null>(null);
  const [periodDays, setPeriodDays] = useState(30);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const isAdmin = session?.user.role === "ADMIN";
  const canManage = isAdmin || session?.user.role === "MANAGER";
  // Admins choose a branch; branch staff always work on their own (the server enforces it).
  const choosesBranch = isAdmin && branches.length > 0;
  const allBranches = choosesBranch && branchId === "";
  const branchName = (id?: string | null) => branches.find((b) => b.id === id)?.name;

  const load = useCallback(async () => {
    if (!session) return;
    try {
      setIngredients(await authenticatedFetch((s) => getIngredients(s, branchId || undefined)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load inventory");
    } finally {
      setLoaded(true);
    }
  }, [authenticatedFetch, session, branchId]);

  const loadReport = useCallback(async () => {
    if (!session || !canManage) return;
    const to = new Date();
    const from = new Date(to.getTime() - periodDays * 86400000);
    try {
      setReport(await authenticatedFetch((s) => getFoodCostReport(s, { from: from.toISOString(), to: to.toISOString(), branchId: branchId || undefined })));
    } catch {
      setReport(null);
    }
  }, [authenticatedFetch, session, canManage, periodDays, branchId]);

  useEffect(() => {
    if (!session) return;
    authenticatedFetch((s) => getBranches(s))
      .then((list) => setBranches(list.filter((b) => b.active)))
      .catch(() => setBranches([]));
  }, [authenticatedFetch, session]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

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
    setActionBranchId(branchId || branches[0]?.id || "");
    setAmount("");
    setUnitCost("");
    setSupplier("");
    setNote("");
    setMovements([]);
    if (!canManage) return;
    try {
      setMovements(await authenticatedFetch((s) => getStockMovements(s, ingredient.id, branchId || undefined)));
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
        const openingBranch = branchId || (choosesBranch && (payload.initialStock ?? 0) > 0 ? branches[0]?.id : undefined);
        await authenticatedFetch((s) => createIngredient(s, payload, openingBranch));
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
    const where = choosesBranch ? actionBranchId || undefined : undefined;
    if (choosesBranch && !where) {
      setError("Choose the branch whose stock this is");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      let next: IngredientRecord;
      if (action === "RESTOCK") {
        next = await authenticatedFetch((s) =>
          restockIngredient(s, selected.id, value, note.trim() || undefined, {
            unitCost: unitCost === "" ? undefined : Number(unitCost),
            supplier: supplier.trim() || undefined,
            branchId: where,
          }),
        );
      } else if (action === "WASTE") {
        next = await authenticatedFetch((s) => adjustIngredient(s, selected.id, { reason: "WASTE", delta: -value, note: note.trim() || undefined, branchId: where }));
      } else {
        next = await authenticatedFetch((s) =>
          adjustIngredient(s, selected.id, { reason: "ADJUSTMENT", countedQuantity: value, note: note.trim() || undefined, branchId: where }),
        );
      }
      const at = next.branchName ? ` at ${next.branchName}` : "";
      setMessage(`${next.name}: ${qty(next.stockQuantity)} ${next.unit} in stock${at}`);
      setAmount("");
      setUnitCost("");
      setSupplier("");
      setNote("");
      await load();
      await loadReport();
      await openIngredient({ ...selected, ...next, branchId: branchId || null });
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
              Each branch keeps its own stock. Orders use each dish&apos;s recipe and the options chosen (set on the Menu page);
              a dish is marked sold out at a branch when an ingredient runs out there. A branch that has never received an
              ingredient does not track it.
            </p>
          </div>
          {canManage ? (
            <button type="button" className="btn btn-primary" onClick={() => setDraft(EMPTY)}>Add ingredient</button>
          ) : null}
        </div>

        <div className="flex flex-wrap items-end gap-2">
          {choosesBranch ? (
            <label className="grid gap-1 text-sm">
              <span>Branch</span>
              <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
                <option value="">All branches (total)</option>
                {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
              </select>
            </label>
          ) : null}
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

        {lowCount > 0 ? (
          <p className="alert alert-warning" role="status">
            {lowCount} ingredient{lowCount === 1 ? " is" : "s are"} at or below the low-stock level{allBranches ? " at a branch" : ""}.{" "}
            <button type="button" className="underline" onClick={() => setFilter("LOW")}>Show them</button>
          </p>
        ) : null}

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
                  <th scope="col" className="py-2 pr-3">{allBranches ? "In stock (all branches)" : "In stock"}</th>
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
                    <td className="py-2 pr-3">
                      {ingredient.tracked ? `${qty(ingredient.stockQuantity)} ${ingredient.unit}` : <span className="text-ink-soft">Not tracked here</span>}
                    </td>
                    <td className="py-2 pr-3">{qty(ingredient.lowStockThreshold)} {ingredient.unit}</td>
                    <td className="py-2 pr-3">{ingredient.costPerUnit ? money(ingredient.costPerUnit) : "-"}</td>
                    <td className="py-2 pr-3">
                      {!ingredient.active ? (
                        <span className="badge badge-neutral">Inactive</span>
                      ) : !ingredient.tracked ? (
                        <span className="badge badge-neutral">Untracked</span>
                      ) : Number(ingredient.stockQuantity) <= 0 && !allBranches ? (
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

      {canManage ? (
        <section className="panel grid gap-3" aria-labelledby="food-cost-title">
          <div className="flex flex-wrap items-end justify-between gap-2">
            <div>
              <p className="kicker">Food cost</p>
              <h2 id="food-cost-title" className="heading-md">
                Ingredients used against sales{branchId ? ` · ${branchName(branchId)}` : allBranches ? " · all branches" : ""}
              </h2>
            </div>
            <select className="select" value={periodDays} onChange={(e) => setPeriodDays(Number(e.target.value))} aria-label="Period">
              {PERIODS.map((p) => <option key={p.days} value={p.days}>{p.label}</option>)}
            </select>
          </div>
          {!report ? (
            <p className="text-sm text-ink-soft">No food cost figures yet.</p>
          ) : (
            <>
              <dl className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {[
                  ["Net sales", money(report.netSales)],
                  ["Ingredients used", money(report.usedCost)],
                  ["Food cost", report.foodCostPercent ? `${Number(report.foodCostPercent)}%` : "—"],
                  ["Waste", money(report.wasteCost)],
                  ["Count differences", money(report.countDifferenceCost)],
                  ["Purchases", money(report.purchasesCost)],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-xl border border-line p-3">
                    <dt className="text-xs text-ink-soft">{label}</dt>
                    <dd className="mt-1 font-display text-lg font-bold">{value}</dd>
                  </div>
                ))}
              </dl>
              {report.ingredientsMissingCost > 0 ? (
                <p className="text-xs text-warning-on">
                  {report.ingredientsMissingCost} ingredient{report.ingredientsMissingCost === 1 ? " has" : "s have"} no cost for some movements:
                  set a cost per unit, or enter the price paid when restocking.
                </p>
              ) : null}
              <div className="grid gap-4 lg:grid-cols-2">
                <div className="overflow-x-auto">
                  <h3 className="font-semibold">By ingredient</h3>
                  <table className="mt-2 w-full text-left text-sm">
                    <thead className="text-ink-soft">
                      <tr><th className="py-1 pr-2">Ingredient</th><th className="py-1 pr-2 text-right">Used</th><th className="py-1 pr-2 text-right">Cost</th><th className="py-1 text-right">Waste</th></tr>
                    </thead>
                    <tbody>
                      {report.ingredients.slice(0, 15).map((line) => (
                        <tr key={line.ingredientId} className="border-t border-line">
                          <td className="py-1 pr-2">{line.name}{line.costMissing ? " *" : ""}</td>
                          <td className="py-1 pr-2 text-right">{qty(line.usedQuantity)} {line.unit}</td>
                          <td className="py-1 pr-2 text-right">{money(line.usedCost)}</td>
                          <td className="py-1 text-right">{Number(line.wasteCost) > 0 ? money(line.wasteCost) : "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="overflow-x-auto">
                  <h3 className="font-semibold">Dish margins (today&apos;s costs)</h3>
                  <table className="mt-2 w-full text-left text-sm">
                    <thead className="text-ink-soft">
                      <tr><th className="py-1 pr-2">Dish</th><th className="py-1 pr-2 text-right">Price</th><th className="py-1 pr-2 text-right">Recipe cost</th><th className="py-1 text-right">Margin</th></tr>
                    </thead>
                    <tbody>
                      {report.dishes.map((dish) => (
                        <tr key={dish.menuItemId} className="border-t border-line">
                          <td className="py-1 pr-2">{dish.name}</td>
                          <td className="py-1 pr-2 text-right">{money(dish.price)}</td>
                          <td className="py-1 pr-2 text-right">{dish.recipeCost ? money(dish.recipeCost) : "No cost"}</td>
                          <td className={`py-1 text-right font-semibold ${dish.marginPercent && Number(dish.marginPercent) < 60 ? "text-warning-on" : ""}`}>
                            {dish.marginPercent ? `${Number(dish.marginPercent)}%` : "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}
        </section>
      ) : null}

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
                <span>Low-stock level (per branch)</span>
                <input className="input" type="number" min={0} step="0.001" value={draft.lowStockThreshold} onChange={(e) => setDraft({ ...draft, lowStockThreshold: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                <span>Cost per unit (GHS)</span>
                <input className="input" type="number" min={0} step="0.01" value={draft.costPerUnit} onChange={(e) => setDraft({ ...draft, costPerUnit: e.target.value })} />
              </label>
            </div>
            {!draft.id ? (
              <label className="grid gap-1 text-sm">
                <span>Opening stock{choosesBranch ? ` (at ${branchName(branchId || branches[0]?.id) ?? "the first branch"})` : ""}</span>
                <input className="input" type="number" min={0} step="0.001" value={draft.initialStock} onChange={(e) => setDraft({ ...draft, initialStock: e.target.value })} />
              </label>
            ) : null}
            <label className="flex min-h-[var(--touch-md)] items-center gap-2 text-sm">
              <input type="checkbox" className="h-5 w-5" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              Track this ingredient (when off, orders ignore it and it never makes a dish sold out)
            </label>
            <button type="submit" className="btn btn-primary" disabled={busy}>{draft.id ? "Save" : "Add ingredient"}</button>
          </form>
        ) : null}
      </Drawer>

      <Drawer open={selected !== null} title={selected ? `${selected.name} stock` : "Stock"} onClose={() => setSelected(null)}>
        {selected ? (
          <div className="grid gap-4">
            <p className="text-sm">
              {selected.tracked ? (
                <>
                  <span className="text-2xl font-semibold">{qty(selected.stockQuantity)}</span> {selected.unit} in stock
                  {allBranches ? " across branches" : selected.branchName ? ` at ${selected.branchName}` : ""}
                </>
              ) : (
                <span className="text-ink-soft">Not tracked at this branch yet: a restock or stock count starts tracking it.</span>
              )}{" "}
              · low at {qty(selected.lowStockThreshold)} {selected.unit}
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
              {choosesBranch ? (
                <label className="grid gap-1 text-sm">
                  <span>Branch</span>
                  <select className="select" required value={actionBranchId} onChange={(e) => setActionBranchId(e.target.value)}>
                    <option value="">Choose a branch</option>
                    {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
                  </select>
                </label>
              ) : null}
              <label className="grid gap-1 text-sm">
                <span>
                  {action === "RESTOCK" ? "Quantity received" : action === "WASTE" ? "Quantity wasted" : "Quantity counted on the shelf"} ({selected.unit})
                </span>
                <input className="input" type="number" min={0} step="0.001" required value={amount} onChange={(e) => setAmount(e.target.value)} />
              </label>
              {action === "RESTOCK" ? (
                <div className="grid grid-cols-2 gap-2">
                  <label className="grid gap-1 text-sm">
                    <span>Price paid per {selected.unit} (optional)</span>
                    <input className="input" type="number" min={0} step="0.01" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
                  </label>
                  <label className="grid gap-1 text-sm">
                    <span>Supplier (optional)</span>
                    <input className="input" maxLength={120} value={supplier} onChange={(e) => setSupplier(e.target.value)} />
                  </label>
                </div>
              ) : null}
              <label className="grid gap-1 text-sm">
                <span>Note (optional)</span>
                <input className="input" maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} placeholder={action === "RESTOCK" ? "Invoice no." : "Reason"} />
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
                          {m.branchName && allBranches ? <span className="text-ink-soft"> · {m.branchName}</span> : null}
                          {m.supplier ? <span className="text-ink-soft"> · {m.supplier}</span> : null}
                          {m.reason === "RESTOCK" && m.unitCost ? <span className="text-ink-soft"> · {money(m.unitCost)}/{selected.unit}</span> : null}
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
