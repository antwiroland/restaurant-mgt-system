"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession, hasStaffRole } from "@/components/SessionProvider";
import {
  createMenuModifierGroup,
  createMenuModifierOption,
  createPromoCode,
  deleteMenuModifierGroup,
  deleteMenuModifierOption,
  getMenuItemModifiers,
  getMenuItems,
  getPromoCodes,
  updatePromoCode,
  updatePromoCodeStatus,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
  type PromoCodeRecord,
  type PromoCodeUpsertRequest,
  type PromoDiscountType,
} from "@/lib/apiClient";

type GroupDraft = {
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
  required: boolean;
  minSelect: string;
  maxSelect: string;
  displayOrder: string;
};

type OptionDraft = {
  name: string;
  priceDelta: string;
  displayOrder: string;
};

type PromoDraft = {
  code: string;
  description: string;
  discountType: PromoDiscountType;
  discountValue: string;
  minOrderAmount: string;
  maxDiscount: string;
  expiryDate: string;
  usageLimit: string;
  active: boolean;
};

const EMPTY_GROUP: GroupDraft = {
  name: "",
  selectionType: "SINGLE",
  required: false,
  minSelect: "",
  maxSelect: "",
  displayOrder: "0",
};

const EMPTY_OPTION: OptionDraft = {
  name: "",
  priceDelta: "0",
  displayOrder: "0",
};

const EMPTY_PROMO: PromoDraft = {
  code: "",
  description: "",
  discountType: "PERCENTAGE",
  discountValue: "10",
  minOrderAmount: "0",
  maxDiscount: "",
  expiryDate: "",
  usageLimit: "",
  active: true,
};

function toIntOrUndefined(value: string): number | undefined {
  if (!value.trim()) return undefined;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function toDateTimeLocalValue(value?: string): string {
  if (!value) return "";
  return new Date(value).toISOString().slice(0, 16);
}

function requireDecimal(value: string, fieldName: string): number {
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${fieldName} must be a valid number`);
  }
  return parsed;
}

function optionalDecimal(value: string, fieldName: string): number | undefined {
  if (!value.trim()) return undefined;
  return requireDecimal(value, fieldName);
}

function toPromoPayload(draft: PromoDraft): PromoCodeUpsertRequest {
  const code = draft.code.trim().toUpperCase();
  if (!code) {
    throw new Error("Promo code is required");
  }

  return {
    code,
    description: draft.description.trim() || undefined,
    discountType: draft.discountType,
    discountValue: requireDecimal(draft.discountValue, "Discount value"),
    minOrderAmount: requireDecimal(draft.minOrderAmount, "Minimum order amount"),
    maxDiscount: optionalDecimal(draft.maxDiscount, "Max discount"),
    expiryDate: draft.expiryDate ? new Date(draft.expiryDate).toISOString() : undefined,
    usageLimit: toIntOrUndefined(draft.usageLimit),
    active: draft.active,
  };
}

function promoToDraft(promo: PromoCodeRecord): PromoDraft {
  return {
    code: promo.code,
    description: promo.description ?? "",
    discountType: promo.discountType,
    discountValue: String(promo.discountValue),
    minOrderAmount: String(promo.minOrderAmount),
    maxDiscount: promo.maxDiscount ? String(promo.maxDiscount) : "",
    expiryDate: toDateTimeLocalValue(promo.expiryDate),
    usageLimit: promo.usageLimit ? String(promo.usageLimit) : "",
    active: promo.active,
  };
}

export default function AdminPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [menuItemQuery, setMenuItemQuery] = useState("");
  const [groupQuery, setGroupQuery] = useState("");
  const [selectedMenuItemId, setSelectedMenuItemId] = useState("");
  const [groups, setGroups] = useState<MenuModifierGroupRecord[]>([]);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [promos, setPromos] = useState<PromoCodeRecord[]>([]);
  const [loadingPromos, setLoadingPromos] = useState(false);
  const [promoQuery, setPromoQuery] = useState("");
  const [promoMode, setPromoMode] = useState<"create" | "edit">("create");
  const [selectedPromoId, setSelectedPromoId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [groupDraft, setGroupDraft] = useState<GroupDraft>(EMPTY_GROUP);
  const [promoDraft, setPromoDraft] = useState<PromoDraft>(EMPTY_PROMO);
  const [optionDraftByGroupId, setOptionDraftByGroupId] = useState<Record<string, OptionDraft>>({});

  const allowed = hasStaffRole(session?.user.role, ["ADMIN", "MANAGER"]);

  const selectedItem = useMemo(
    () => menuItems.find((item) => item.id === selectedMenuItemId) ?? null,
    [menuItems, selectedMenuItemId],
  );
  const selectedPromo = useMemo(
    () => promos.find((promo) => promo.id === selectedPromoId) ?? null,
    [promos, selectedPromoId],
  );
  const filteredMenuItems = useMemo(() => {
    const normalizedQuery = menuItemQuery.trim().toLowerCase();
    return menuItems.filter((item) => !normalizedQuery
      || item.name.toLowerCase().includes(normalizedQuery)
      || item.categoryName.toLowerCase().includes(normalizedQuery));
  }, [menuItems, menuItemQuery]);
  const filteredGroups = useMemo(() => {
    const normalizedQuery = groupQuery.trim().toLowerCase();
    return groups.filter((group) => {
      if (!normalizedQuery) return true;
      return group.name.toLowerCase().includes(normalizedQuery)
        || group.options.some((option) => option.name.toLowerCase().includes(normalizedQuery));
    });
  }, [groups, groupQuery]);
  const filteredPromos = useMemo(() => {
    const normalizedQuery = promoQuery.trim().toLowerCase();
    return promos.filter((promo) => {
      if (!normalizedQuery) return true;
      return promo.code.toLowerCase().includes(normalizedQuery)
        || (promo.description ?? "").toLowerCase().includes(normalizedQuery);
    });
  }, [promoQuery, promos]);

  useEffect(() => {
    if (!session || !allowed) return;
    authenticatedFetch(getMenuItems)
      .then((items) => {
        const sorted = [...items].sort((a, b) => a.name.localeCompare(b.name));
        setMenuItems(sorted);
        if (sorted.length > 0) {
          setSelectedMenuItemId((current) => current || sorted[0].id);
        }
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load menu items"));

    setLoadingPromos(true);
    authenticatedFetch(getPromoCodes)
      .then((nextPromos) => setPromos(nextPromos))
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load promo codes"))
      .finally(() => setLoadingPromos(false));
  }, [authenticatedFetch, allowed, session]);

  useEffect(() => {
    if (!selectedMenuItemId) return;
    setLoadingGroups(true);
    setError("");
    getMenuItemModifiers(selectedMenuItemId)
      .then((nextGroups) => {
        setGroups(nextGroups);
        setOptionDraftByGroupId((current) => {
          const next: Record<string, OptionDraft> = { ...current };
          for (const group of nextGroups) {
            if (!next[group.id]) {
              next[group.id] = { ...EMPTY_OPTION };
            }
          }
          return next;
        });
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load modifiers"))
      .finally(() => setLoadingGroups(false));
  }, [selectedMenuItemId]);

  async function reloadGroups() {
    if (!selectedMenuItemId) return;
    const nextGroups = await getMenuItemModifiers(selectedMenuItemId);
    setGroups(nextGroups);
  }

  async function reloadPromos() {
    const nextPromos = await authenticatedFetch(getPromoCodes);
    setPromos(nextPromos);
  }

  function resetPromoForm() {
    setPromoMode("create");
    setSelectedPromoId("");
    setPromoDraft(EMPTY_PROMO);
  }

  async function createGroup() {
    if (!session || !selectedMenuItemId) return;
    setError("");
    setMessage("");

    try {
      await authenticatedFetch((activeSession) =>
        createMenuModifierGroup(activeSession, selectedMenuItemId, {
          name: groupDraft.name.trim(),
          selectionType: groupDraft.selectionType,
          required: groupDraft.required,
          minSelect: toIntOrUndefined(groupDraft.minSelect),
          maxSelect: toIntOrUndefined(groupDraft.maxSelect),
          displayOrder: Number.parseInt(groupDraft.displayOrder || "0", 10),
        }),
      );
      setGroupDraft(EMPTY_GROUP);
      await reloadGroups();
      setMessage("Modifier group added.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create modifier group");
    }
  }

  async function removeGroup(groupId: string) {
    if (!session || !selectedMenuItemId) return;
    setError("");
    setMessage("");

    try {
      await authenticatedFetch((activeSession) => deleteMenuModifierGroup(activeSession, selectedMenuItemId, groupId));
      await reloadGroups();
      setMessage("Modifier group removed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete modifier group");
    }
  }

  async function createOption(groupId: string) {
    if (!session || !selectedMenuItemId) return;
    const draft = optionDraftByGroupId[groupId] ?? EMPTY_OPTION;
    setError("");
    setMessage("");

    try {
      await authenticatedFetch((activeSession) =>
        createMenuModifierOption(activeSession, selectedMenuItemId, groupId, {
          name: draft.name.trim(),
          priceDelta: Number.parseFloat(draft.priceDelta || "0"),
          displayOrder: Number.parseInt(draft.displayOrder || "0", 10),
        }),
      );
      setOptionDraftByGroupId((current) => ({
        ...current,
        [groupId]: { ...EMPTY_OPTION },
      }));
      await reloadGroups();
      setMessage("Modifier option added.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create modifier option");
    }
  }

  async function removeOption(groupId: string, optionId: string) {
    if (!session || !selectedMenuItemId) return;
    setError("");
    setMessage("");

    try {
      await authenticatedFetch((activeSession) => deleteMenuModifierOption(activeSession, selectedMenuItemId, groupId, optionId));
      await reloadGroups();
      setMessage("Modifier option removed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete modifier option");
    }
  }

  function editPromo(promo: PromoCodeRecord) {
    setPromoMode("edit");
    setSelectedPromoId(promo.id);
    setPromoDraft(promoToDraft(promo));
    setError("");
    setMessage("");
  }

  async function savePromo() {
    if (!session) return;
    setError("");
    setMessage("");

    try {
      const payload = toPromoPayload(promoDraft);
      if (promoMode === "edit" && selectedPromoId) {
        await authenticatedFetch((activeSession) => updatePromoCode(activeSession, selectedPromoId, payload));
        setMessage("Promo updated.");
      } else {
        await authenticatedFetch((activeSession) => createPromoCode(activeSession, payload));
        setMessage("Promo created.");
      }
      await reloadPromos();
      resetPromoForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save promo");
    }
  }

  async function togglePromoStatus(promo: PromoCodeRecord) {
    if (!session) return;
    setError("");
    setMessage("");

    try {
      await authenticatedFetch((activeSession) => updatePromoCodeStatus(activeSession, promo.id, !promo.active));
      await reloadPromos();
      if (selectedPromoId === promo.id) {
        setPromoDraft((current) => ({ ...current, active: !promo.active }));
      }
      setMessage(promo.active ? "Promo deactivated." : "Promo reactivated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update promo status");
    }
  }

  if (loading) {
    return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-44" />{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}</section></main>;
  }

  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Admin</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link className="mt-4 inline-flex btn btn-primary" href="/login">Open Login</Link>
        </section>
      </main>
    );
  }

  if (!allowed) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Admin</p>
          <h1 className="text-2xl font-semibold">Manager/Admin only</h1>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4">
      <section className="grid gap-4 md:grid-cols-[360px,1fr]">
        <div className="panel">
          <p className="kicker">Menu</p>
          <h1 className="text-2xl font-semibold">Modifier Setup</h1>
          <p className="mt-2 text-ink-soft">Configure size, spice, and add-on groups for each menu item.</p>

          <label className="mt-4 grid gap-2 text-sm text-ink-soft">
            <span>Search Menu Item</span>
            <input
              className="input"
              placeholder="Search item or category"
              value={menuItemQuery}
              onChange={(event) => setMenuItemQuery(event.target.value)}
            />
          </label>

          <label className="mt-4 grid gap-2 text-sm text-ink-soft">
            <span>Menu Item</span>
            <select
              className="input"
              value={selectedMenuItemId}
              onChange={(event) => setSelectedMenuItemId(event.target.value)}
            >
              {filteredMenuItems.map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </select>
          </label>

          <div className="mt-5 input">
            <p className="text-sm font-semibold text-ink">Add Group</p>
            <div className="mt-3 grid gap-2">
              <input
                className="input text-sm"
                placeholder="Group name (e.g., Size)"
                value={groupDraft.name}
                onChange={(event) => setGroupDraft((s) => ({ ...s, name: event.target.value }))}
              />
              <select
                className="input text-sm"
                value={groupDraft.selectionType}
                onChange={(event) => setGroupDraft((s) => ({ ...s, selectionType: event.target.value as "SINGLE" | "MULTIPLE" }))}
              >
                <option value="SINGLE">SINGLE</option>
                <option value="MULTIPLE">MULTIPLE</option>
              </select>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={groupDraft.required}
                  onChange={(event) => setGroupDraft((s) => ({ ...s, required: event.target.checked }))}
                />
                Required
              </label>
              <div className="grid grid-cols-3 gap-2">
                <input
                  className="input text-sm"
                  placeholder="min"
                  value={groupDraft.minSelect}
                  onChange={(event) => setGroupDraft((s) => ({ ...s, minSelect: event.target.value }))}
                />
                <input
                  className="input text-sm"
                  placeholder="max"
                  value={groupDraft.maxSelect}
                  onChange={(event) => setGroupDraft((s) => ({ ...s, maxSelect: event.target.value }))}
                />
                <input
                  className="input text-sm"
                  placeholder="order"
                  value={groupDraft.displayOrder}
                  onChange={(event) => setGroupDraft((s) => ({ ...s, displayOrder: event.target.value }))}
                />
              </div>
              <button className="btn btn-primary btn-sm" onClick={() => void createGroup()}>
                Add Group
              </button>
            </div>
          </div>
        </div>

        <div className="panel">
          <p className="kicker">Modifiers</p>
          <h2 className="text-xl font-semibold">{selectedItem ? selectedItem.name : "Select a menu item"}</h2>
          <input
            className="mt-4 w-full input text-sm"
            placeholder="Search group or option"
            value={groupQuery}
            onChange={(event) => setGroupQuery(event.target.value)}
          />

          {loadingGroups ? (
            <p className="mt-4 text-sm text-ink-soft">Loading groups...</p>
          ) : filteredGroups.length === 0 ? (
            <p className="mt-4 rounded-lg border border-dashed border-line p-3 text-sm text-ink-soft">No modifier groups yet.</p>
          ) : (
            <div className="mt-4 grid gap-3">
              {filteredGroups.map((group) => (
                <article key={group.id} className="input">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-semibold text-ink">{group.name}</p>
                    <button className="btn btn-danger btn-sm" onClick={() => void removeGroup(group.id)}>Delete</button>
                  </div>
                  <p className="mt-1 text-xs text-ink-soft">
                    {group.selectionType} · {group.required ? "Required" : "Optional"} · min {group.minSelect ?? 0} / max {group.maxSelect ?? "none"}
                  </p>

                  <div className="mt-3 grid gap-2">
                    {group.options.map((option) => (
                      <div key={option.id} className="grid grid-cols-[1fr,120px,120px] gap-2">
                        <input className="input text-sm" value={option.name} readOnly />
                        <input className="input text-sm" value={String(option.priceDelta)} readOnly />
                        <button className="btn btn-danger btn-sm" onClick={() => void removeOption(group.id, option.id)}>Delete</button>
                      </div>
                    ))}
                  </div>

                  <div className="mt-3 grid grid-cols-[1fr,120px,90px] gap-2">
                    <input
                      className="input text-sm"
                      placeholder="Option name"
                      value={optionDraftByGroupId[group.id]?.name ?? ""}
                      onChange={(event) =>
                        setOptionDraftByGroupId((current) => ({
                          ...current,
                          [group.id]: { ...(current[group.id] ?? EMPTY_OPTION), name: event.target.value },
                        }))
                      }
                    />
                    <input
                      className="input text-sm"
                      placeholder="Price"
                      value={optionDraftByGroupId[group.id]?.priceDelta ?? "0"}
                      onChange={(event) =>
                        setOptionDraftByGroupId((current) => ({
                          ...current,
                          [group.id]: { ...(current[group.id] ?? EMPTY_OPTION), priceDelta: event.target.value },
                        }))
                      }
                    />
                    <button className="btn btn-primary btn-sm" onClick={() => void createOption(group.id)}>
                      Add
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="panel grid gap-4 xl:grid-cols-[360px,1fr]">
        <div>
          <p className="kicker">Promos</p>
          <h2 className="text-xl font-semibold">
            {promoMode === "edit" ? `Edit ${selectedPromo?.code ?? "Promo"}` : "Promo Management"}
          </h2>
          <p className="mt-2 text-ink-soft">Create, update, activate, and deactivate persisted promo codes for mobile and staff checkout flows.</p>

          <div className="mt-5 grid gap-3">
            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Promo Code</span>
              <input
                className="input"
                placeholder="SAVE10"
                value={promoDraft.code}
                onChange={(event) => setPromoDraft((current) => ({ ...current, code: event.target.value.toUpperCase() }))}
              />
            </label>

            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Description</span>
              <textarea
                className="input min-h-24"
                placeholder="10% off orders above 20 GHS"
                value={promoDraft.description}
                onChange={(event) => setPromoDraft((current) => ({ ...current, description: event.target.value }))}
              />
            </label>

            <div className="grid gap-3 md:grid-cols-2">
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Discount Type</span>
                <select
                  className="input"
                  value={promoDraft.discountType}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, discountType: event.target.value as PromoDiscountType }))}
                >
                  <option value="PERCENTAGE">PERCENTAGE</option>
                  <option value="FLAT">FLAT</option>
                </select>
              </label>
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Discount Value</span>
                <input
                  className="input"
                  value={promoDraft.discountValue}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, discountValue: event.target.value }))}
                />
              </label>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Minimum Order Amount</span>
                <input
                  className="input"
                  value={promoDraft.minOrderAmount}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, minOrderAmount: event.target.value }))}
                />
              </label>
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Max Discount</span>
                <input
                  className="input"
                  placeholder="Optional"
                  value={promoDraft.maxDiscount}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, maxDiscount: event.target.value }))}
                />
              </label>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Expiry Date</span>
                <input
                  className="input"
                  type="datetime-local"
                  value={promoDraft.expiryDate}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, expiryDate: event.target.value }))}
                />
              </label>
              <label className="grid gap-2 text-sm text-ink-soft">
                <span>Usage Limit</span>
                <input
                  className="input"
                  placeholder="Optional"
                  value={promoDraft.usageLimit}
                  onChange={(event) => setPromoDraft((current) => ({ ...current, usageLimit: event.target.value }))}
                />
              </label>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={promoDraft.active}
                onChange={(event) => setPromoDraft((current) => ({ ...current, active: event.target.checked }))}
              />
              Active
            </label>

            <div className="flex flex-wrap gap-2">
              <button className="btn btn-primary" onClick={() => void savePromo()}>
                {promoMode === "edit" ? "Save Promo" : "Create Promo"}
              </button>
              {promoMode === "edit" ? (
                <button className="btn" onClick={resetPromoForm}>
                  New Promo
                </button>
              ) : null}
            </div>
          </div>
        </div>

        <div>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="kicker">Current Codes</p>
              <h3 className="text-lg font-semibold">Promo Catalog</h3>
            </div>
            <label className="grid gap-2 text-sm text-ink-soft">
              <span>Search Promos</span>
              <input
                className="input min-w-64"
                placeholder="Search code or description"
                value={promoQuery}
                onChange={(event) => setPromoQuery(event.target.value)}
              />
            </label>
          </div>

          {loadingPromos ? (
            <p className="mt-4 text-sm text-ink-soft">Loading promo codes...</p>
          ) : filteredPromos.length === 0 ? (
            <p className="mt-4 rounded-lg border border-dashed border-line p-3 text-sm text-ink-soft">No promo codes found.</p>
          ) : (
            <div className="mt-4 grid gap-3">
              {filteredPromos.map((promo) => (
                <article key={promo.id} className="rounded-2xl border border-line p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-lg font-semibold text-ink">{promo.code}</p>
                        <span className="rounded-full border border-line px-2 py-1 text-xs text-ink-soft">
                          {promo.active ? "Active" : "Inactive"}
                        </span>
                      </div>
                      <p className="mt-1 text-sm text-ink-soft">{promo.description || "No description"}</p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <button className="btn btn-sm" onClick={() => editPromo(promo)}>Edit</button>
                      <button
                        className={promo.active ? "btn btn-danger btn-sm" : "btn btn-sm"}
                        onClick={() => void togglePromoStatus(promo)}
                      >
                        {promo.active ? "Deactivate" : "Reactivate"}
                      </button>
                    </div>
                  </div>

                  <div className="mt-4 grid gap-2 text-sm text-ink-soft md:grid-cols-2 xl:grid-cols-3">
                    <p>Discount: {promo.discountValue} {promo.discountType === "PERCENTAGE" ? "%" : "GHS"}</p>
                    <p>Minimum order: {promo.minOrderAmount}</p>
                    <p>Max discount: {promo.maxDiscount ?? "none"}</p>
                    <p>Usage limit: {promo.usageLimit ?? "unlimited"}</p>
                    <p>Used: {promo.usageCount}</p>
                    <p>Expires: {promo.expiryDate ? new Date(promo.expiryDate).toLocaleString() : "never"}</p>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>
      </section>

      {error ? <p className="alert alert-danger">{error}</p> : null}
      {message ? <p className="alert alert-success">{message}</p> : null}
    </main>
  );
}
