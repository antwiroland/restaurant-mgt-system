"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession, hasStaffRole } from "@/components/SessionProvider";
import {
  createMenuModifierGroup,
  createMenuModifierOption,
  deleteMenuModifierGroup,
  deleteMenuModifierOption,
  getMenuItemModifiers,
  getMenuItems,
  type MenuItemRecord,
  type MenuModifierGroupRecord,
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

function toIntOrUndefined(value: string): number | undefined {
  if (!value.trim()) return undefined;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export default function AdminPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [menuItems, setMenuItems] = useState<MenuItemRecord[]>([]);
  const [menuItemQuery, setMenuItemQuery] = useState("");
  const [groupQuery, setGroupQuery] = useState("");
  const [selectedMenuItemId, setSelectedMenuItemId] = useState("");
  const [groups, setGroups] = useState<MenuModifierGroupRecord[]>([]);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [groupDraft, setGroupDraft] = useState<GroupDraft>(EMPTY_GROUP);
  const [optionDraftByGroupId, setOptionDraftByGroupId] = useState<Record<string, OptionDraft>>({});

  const allowed = hasStaffRole(session?.user.role, ["ADMIN", "MANAGER"]);

  const selectedItem = useMemo(
    () => menuItems.find((item) => item.id === selectedMenuItemId) ?? null,
    [menuItems, selectedMenuItemId],
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
    <main className="shell grid gap-4 md:grid-cols-[360px,1fr]">
      <section className="panel">
        <p className="kicker">Menu</p>
        <h1 className="text-2xl font-semibold">Modifier Setup</h1>
        <p className="mt-2 text-ink-soft">Configure size/spice groups and options for each menu item.</p>

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

        {error ? <p className="mt-4 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-2 alert alert-success">{message}</p> : null}
      </section>

      <section className="panel">
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
      </section>
    </main>
  );
}


