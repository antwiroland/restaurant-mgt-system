"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import {
  createMenuCategory,
  createMenuItem,
  deleteMenuCategory,
  deleteMenuItem,
  getMenuCategories,
  getMenuItems,
  updateMenuCategory,
  updateMenuItem,
  updateMenuItemAvailability,
  type CategoryRecord,
  type MenuItemRecord,
} from "@/lib/apiClient";

export default function MenuManagementPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [categories, setCategories] = useState<CategoryRecord[]>([]);
  const [items, setItems] = useState<MenuItemRecord[]>([]);
  const [categoryQuery, setCategoryQuery] = useState("");
  const [itemQuery, setItemQuery] = useState("");
  const [itemCategoryFilter, setItemCategoryFilter] = useState("");
  const [availabilityFilter, setAvailabilityFilter] = useState<"ALL" | "AVAILABLE" | "UNAVAILABLE">("ALL");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [categoryForm, setCategoryForm] = useState({ name: "", description: "", displayOrder: "0", active: true });
  const [itemForm, setItemForm] = useState({ categoryId: "", name: "", description: "", price: "", imageUrl: "", available: true });
  const filteredCategories = categories.filter((category) => {
    const normalizedQuery = categoryQuery.trim().toLowerCase();
    return !normalizedQuery
      || category.name.toLowerCase().includes(normalizedQuery)
      || (category.description?.toLowerCase().includes(normalizedQuery) ?? false);
  });
  const filteredItems = items.filter((item) => {
    const normalizedQuery = itemQuery.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || item.name.toLowerCase().includes(normalizedQuery)
      || item.categoryName.toLowerCase().includes(normalizedQuery)
      || (item.description?.toLowerCase().includes(normalizedQuery) ?? false);
    const matchesCategory = !itemCategoryFilter || item.categoryId === itemCategoryFilter;
    const matchesAvailability = availabilityFilter === "ALL"
      || (availabilityFilter === "AVAILABLE" && item.available)
      || (availabilityFilter === "UNAVAILABLE" && !item.available);
    return matchesQuery && matchesCategory && matchesAvailability;
  });

  async function loadAll() {
    if (!session) return;
    const [nextCategories, nextItems] = await authenticatedFetch(async (activeSession) => {
      const [cats, menu] = await Promise.all([getMenuCategories(), getMenuItems(activeSession)]);
      return [cats, menu] as const;
    });
    setCategories(nextCategories);
    setItems(nextItems);
    setItemForm((prev) => ({ ...prev, categoryId: prev.categoryId || nextCategories[0]?.id || "" }));
  }

  useEffect(() => {
    if (!session) return;
    loadAll().catch((err) => setError(err instanceof Error ? err.message : "Could not load menu"));
  }, [session]);

  async function onCreateCategory(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) => createMenuCategory(activeSession, {
        name: categoryForm.name,
        description: categoryForm.description || undefined,
        displayOrder: Number.parseInt(categoryForm.displayOrder || "0", 10),
        active: categoryForm.active,
      }));
      setCategoryForm({ name: "", description: "", displayOrder: "0", active: true });
      await loadAll();
      setMessage("Category created.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create category");
    }
  }

  async function onCreateItem(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) => createMenuItem(activeSession, {
        categoryId: itemForm.categoryId,
        name: itemForm.name,
        description: itemForm.description || undefined,
        price: Number.parseFloat(itemForm.price),
        imageUrl: itemForm.imageUrl || undefined,
        available: itemForm.available,
      }));
      setItemForm((prev) => ({ ...prev, name: "", description: "", price: "", imageUrl: "", available: true }));
      await loadAll();
      setMessage("Menu item created.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create item");
    }
  }

  async function quickEditCategory(category: CategoryRecord) {
    if (!session) return;
    const name = prompt("Category name", category.name);
    if (!name) return;
    const description = prompt("Description", category.description ?? "") ?? undefined;
    const displayOrder = Number.parseInt(prompt("Display order", String(category.displayOrder)) || String(category.displayOrder), 10);
    try {
      await authenticatedFetch((activeSession) => updateMenuCategory(activeSession, category.id, {
        name,
        description,
        displayOrder,
        active: category.active,
      }));
      await loadAll();
      setMessage("Category updated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update category");
    }
  }

  async function quickEditItem(item: MenuItemRecord) {
    if (!session) return;
    const name = prompt("Item name", item.name);
    if (!name) return;
    const description = prompt("Description", item.description ?? "") ?? undefined;
    const price = Number.parseFloat(prompt("Price", String(item.price)) || String(item.price));
    try {
      await authenticatedFetch((activeSession) => updateMenuItem(activeSession, item.id, {
        categoryId: item.categoryId,
        name,
        description,
        price,
        imageUrl: item.imageUrl,
        available: item.available,
      }));
      await loadAll();
      setMessage("Item updated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update item");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading menu...</section></main>;

  if (!session) {
    return <main className="shell"><section className="panel"><p className="kicker">Menu</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link></section></main>;
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Menu</p>
        <h1 className="text-2xl font-semibold">Menu Management</h1>
        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {message ? <p className="mt-3 rounded-xl bg-[#dcfce7] px-4 py-3 text-sm text-[#166534]">{message}</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Create Category</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onCreateCategory(event)}>
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Name" value={categoryForm.name} onChange={(e) => setCategoryForm((s) => ({ ...s, name: e.target.value }))} required />
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Description" value={categoryForm.description} onChange={(e) => setCategoryForm((s) => ({ ...s, description: e.target.value }))} />
            <input className="rounded-xl border border-[#cfe0c8] p-3" type="number" min={0} placeholder="Display order" value={categoryForm.displayOrder} onChange={(e) => setCategoryForm((s) => ({ ...s, displayOrder: e.target.value }))} />
            <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Create</button>
          </form>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Create Item</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onCreateItem(event)}>
            <select className="rounded-xl border border-[#cfe0c8] p-3" value={itemForm.categoryId} onChange={(e) => setItemForm((s) => ({ ...s, categoryId: e.target.value }))} required>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Name" value={itemForm.name} onChange={(e) => setItemForm((s) => ({ ...s, name: e.target.value }))} required />
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Description" value={itemForm.description} onChange={(e) => setItemForm((s) => ({ ...s, description: e.target.value }))} />
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Price" type="number" step="0.01" min="0.01" value={itemForm.price} onChange={(e) => setItemForm((s) => ({ ...s, price: e.target.value }))} required />
            <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Create</button>
          </form>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Categories</h2>
          <input className="mt-3 w-full rounded-xl border border-[#cfe0c8] p-2 text-sm" placeholder="Search categories" value={categoryQuery} onChange={(e) => setCategoryQuery(e.target.value)} />
          <ul className="mt-3 grid gap-2">
            {filteredCategories.map((category) => (
              <li key={category.id} className="rounded-xl border border-[#cfe0c8] p-3 flex items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{category.name}</p>
                  <p className="text-xs text-[#35523d]">order {category.displayOrder} · {category.active ? "active" : "inactive"}</p>
                </div>
                <div className="flex gap-2">
                  <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void quickEditCategory(category)}>Edit</button>
                  <button className="rounded-full border border-[#991b1b] px-3 py-1 text-xs text-[#991b1b]" onClick={() => void authenticatedFetch((s) => deleteMenuCategory(s, category.id)).then(loadAll)}>Delete</button>
                </div>
              </li>
            ))}
            {filteredCategories.length === 0 ? <li className="rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No categories match the current filters.</li> : null}
          </ul>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Items</h2>
          <div className="mt-3 grid gap-2 md:grid-cols-3">
            <input className="rounded-xl border border-[#cfe0c8] p-2 text-sm" placeholder="Search items or category" value={itemQuery} onChange={(e) => setItemQuery(e.target.value)} />
            <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={itemCategoryFilter} onChange={(e) => setItemCategoryFilter(e.target.value)}>
              <option value="">All categories</option>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
            <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={availabilityFilter} onChange={(e) => setAvailabilityFilter(e.target.value as "ALL" | "AVAILABLE" | "UNAVAILABLE")}>
              <option value="ALL">All availability</option>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
            </select>
          </div>
          <ul className="mt-3 grid gap-2">
            {filteredItems.map((item) => (
              <li key={item.id} className="rounded-xl border border-[#cfe0c8] p-3">
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <p className="font-medium">{item.name}</p>
                    <p className="text-xs text-[#35523d]">GHS {item.price} · {item.available ? "available" : "unavailable"}</p>
                  </div>
                  <div className="flex gap-2">
                    <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void quickEditItem(item)}>Edit</button>
                    <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void authenticatedFetch((s) => updateMenuItemAvailability(s, item.id, !item.available)).then(loadAll)}>{item.available ? "Disable" : "Enable"}</button>
                    <button className="rounded-full border border-[#991b1b] px-3 py-1 text-xs text-[#991b1b]" onClick={() => void authenticatedFetch((s) => deleteMenuItem(s, item.id)).then(loadAll)}>Delete</button>
                  </div>
                </div>
              </li>
            ))}
            {filteredItems.length === 0 ? <li className="rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No menu items match the current filters.</li> : null}
          </ul>
        </article>
      </section>
    </main>
  );
}
