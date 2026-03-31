"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { ConfirmModal } from "@/components/ConfirmModal";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { Spinner } from "@/components/Spinner";
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
  const [editingCategory, setEditingCategory] = useState<CategoryRecord | null>(null);
  const [editingCategoryForm, setEditingCategoryForm] = useState({ name: "", description: "", displayOrder: "0", active: true });
  const [editingItem, setEditingItem] = useState<MenuItemRecord | null>(null);
  const [editingItemForm, setEditingItemForm] = useState({ categoryId: "", name: "", description: "", price: "", imageUrl: "", available: true });
  const [editBusy, setEditBusy] = useState(false);
  const [deletingCategory, setDeletingCategory] = useState<CategoryRecord | null>(null);
  const [deletingItem, setDeletingItem] = useState<MenuItemRecord | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
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

  async function saveCategoryEdit() {
    if (!session || !editingCategory) return;
    setEditBusy(true);
    try {
      await authenticatedFetch((activeSession) => updateMenuCategory(activeSession, editingCategory.id, {
        name: editingCategoryForm.name,
        description: editingCategoryForm.description || undefined,
        displayOrder: Number.parseInt(editingCategoryForm.displayOrder || "0", 10),
        active: editingCategoryForm.active,
      }));
      await loadAll();
      setMessage("Category updated.");
      setEditingCategory(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update category");
    } finally {
      setEditBusy(false);
    }
  }

  async function saveItemEdit() {
    if (!session || !editingItem) return;
    setEditBusy(true);
    try {
      await authenticatedFetch((activeSession) => updateMenuItem(activeSession, editingItem.id, {
        categoryId: editingItemForm.categoryId,
        name: editingItemForm.name,
        description: editingItemForm.description || undefined,
        price: Number.parseFloat(editingItemForm.price),
        imageUrl: editingItemForm.imageUrl || undefined,
        available: editingItemForm.available,
      }));
      await loadAll();
      setMessage("Item updated.");
      setEditingItem(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update item");
    } finally {
      setEditBusy(false);
    }
  }

  if (loading) {
    return (
      <main className="shell grid gap-4">
        <section className="panel grid gap-3">
          <Skeleton className="h-6 w-44" />
          <Skeleton className="h-12 w-full" />
        </section>
        <section className="grid gap-4 md:grid-cols-2">
          <div className="panel grid gap-3">
            <Skeleton className="h-10 w-full" />
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
          <div className="panel grid gap-3">
            <Skeleton className="h-10 w-full" />
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
        </section>
      </main>
    );
  }

  if (!session) {
    return <main className="shell"><section className="panel"><p className="kicker">Menu</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;
  }

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Menu</p>
        <h1 className="text-2xl font-semibold">Menu Management</h1>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Create Category</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onCreateCategory(event)}>
            <input className="input" placeholder="Name" value={categoryForm.name} onChange={(e) => setCategoryForm((s) => ({ ...s, name: e.target.value }))} required />
            <input className="input" placeholder="Description" value={categoryForm.description} onChange={(e) => setCategoryForm((s) => ({ ...s, description: e.target.value }))} />
            <input className="input" type="number" min={0} placeholder="Display order" value={categoryForm.displayOrder} onChange={(e) => setCategoryForm((s) => ({ ...s, displayOrder: e.target.value }))} />
            <button className="btn btn-primary">Create</button>
          </form>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Create Item</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void onCreateItem(event)}>
            <select className="input" value={itemForm.categoryId} onChange={(e) => setItemForm((s) => ({ ...s, categoryId: e.target.value }))} required>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
            <input className="input" placeholder="Name" value={itemForm.name} onChange={(e) => setItemForm((s) => ({ ...s, name: e.target.value }))} required />
            <input className="input" placeholder="Description" value={itemForm.description} onChange={(e) => setItemForm((s) => ({ ...s, description: e.target.value }))} />
            <input className="input" placeholder="Price" type="number" step="0.01" min="0.01" value={itemForm.price} onChange={(e) => setItemForm((s) => ({ ...s, price: e.target.value }))} required />
            <button className="btn btn-primary">Create</button>
          </form>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Categories</h2>
          <input className="mt-3 w-full input text-sm" placeholder="Search categories" value={categoryQuery} onChange={(e) => setCategoryQuery(e.target.value)} />
          <ul className="mt-3 grid gap-2">
            {filteredCategories.map((category) => (
              <li key={category.id} className="input flex items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{category.name}</p>
                  <p className="text-xs text-ink-soft">order {category.displayOrder} - {category.active ? "active" : "inactive"}</p>
                </div>
                <div className="flex gap-2">
                  <button
                    className="btn btn-secondary btn-sm"
                    onClick={() => {
                      setEditingCategory(category);
                      setEditingCategoryForm({
                        name: category.name,
                        description: category.description ?? "",
                        displayOrder: String(category.displayOrder),
                        active: category.active,
                      });
                    }}
                  >
                    Edit
                  </button>
                  <button className="btn btn-danger btn-sm" onClick={() => setDeletingCategory(category)}>Delete</button>
                </div>
              </li>
            ))}
            {filteredCategories.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No categories match the current filters.</li> : null}
          </ul>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Items</h2>
          <div className="mt-3 grid gap-2 md:grid-cols-3">
            <input className="input text-sm" placeholder="Search items or category" value={itemQuery} onChange={(e) => setItemQuery(e.target.value)} />
            <select className="input text-sm" value={itemCategoryFilter} onChange={(e) => setItemCategoryFilter(e.target.value)}>
              <option value="">All categories</option>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
            <select className="input text-sm" value={availabilityFilter} onChange={(e) => setAvailabilityFilter(e.target.value as "ALL" | "AVAILABLE" | "UNAVAILABLE")}>
              <option value="ALL">All availability</option>
              <option value="AVAILABLE">Available</option>
              <option value="UNAVAILABLE">Unavailable</option>
            </select>
          </div>
          <ul className="mt-3 grid gap-2">
            {filteredItems.map((item) => (
              <li key={item.id} className="input">
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <p className="font-medium">{item.name}</p>
                    <p className="text-xs text-ink-soft">GHS {item.price} - {item.available ? "available" : "unavailable"}</p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      className="btn btn-secondary btn-sm"
                      onClick={() => {
                        setEditingItem(item);
                        setEditingItemForm({
                          categoryId: item.categoryId,
                          name: item.name,
                          description: item.description ?? "",
                          price: String(item.price),
                          imageUrl: item.imageUrl ?? "",
                          available: item.available,
                        });
                      }}
                    >
                      Edit
                    </button>
                    <button className="btn btn-secondary btn-sm" onClick={() => void authenticatedFetch((s) => updateMenuItemAvailability(s, item.id, !item.available)).then(loadAll)}>{item.available ? "Disable" : "Enable"}</button>
                    <button className="btn btn-danger btn-sm" onClick={() => setDeletingItem(item)}>Delete</button>
                  </div>
                </div>
              </li>
            ))}
            {filteredItems.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No menu items match the current filters.</li> : null}
          </ul>
        </article>
      </section>

      <ConfirmModal
        open={!!deletingCategory}
        title="Delete Category"
        message={`Delete "${deletingCategory?.name}"? All items in this category will also be deleted.`}
        confirmLabel="Delete"
        danger
        busy={deleteBusy}
        onConfirm={async () => {
          if (!deletingCategory) return;
          setDeleteBusy(true);
          try {
            await authenticatedFetch((s) => deleteMenuCategory(s, deletingCategory.id));
            await loadAll();
            setMessage("Category deleted.");
            setDeletingCategory(null);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not delete category");
            setDeletingCategory(null);
          } finally {
            setDeleteBusy(false);
          }
        }}
        onClose={() => setDeletingCategory(null)}
      />

      <ConfirmModal
        open={!!deletingItem}
        title="Delete Item"
        message={`Delete "${deletingItem?.name}"?`}
        confirmLabel="Delete"
        danger
        busy={deleteBusy}
        onConfirm={async () => {
          if (!deletingItem) return;
          setDeleteBusy(true);
          try {
            await authenticatedFetch((s) => deleteMenuItem(s, deletingItem.id));
            await loadAll();
            setMessage("Item deleted.");
            setDeletingItem(null);
          } catch (err) {
            setError(err instanceof Error ? err.message : "Could not delete item");
            setDeletingItem(null);
          } finally {
            setDeleteBusy(false);
          }
        }}
        onClose={() => setDeletingItem(null)}
      />

      <Modal
        open={!!editingCategory}
        title={editingCategory ? `Edit Category: ${editingCategory.name}` : "Edit Category"}
        onClose={() => setEditingCategory(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setEditingCategory(null)} disabled={editBusy}>
              Cancel
            </button>
            <button
              className="btn btn-primary btn-sm"
              disabled={editBusy || !editingCategoryForm.name.trim()}
              onClick={() => void saveCategoryEdit()}
            >
              {editBusy ? <Spinner className="text-current" /> : null}
              Save
            </button>
          </>
        }
      >
        <div className="grid gap-3">
          <label className="field">
            <span className="field-label">Name</span>
            <input className="input" value={editingCategoryForm.name} onChange={(e) => setEditingCategoryForm((s) => ({ ...s, name: e.target.value }))} />
          </label>
          <label className="field">
            <span className="field-label">Description</span>
            <input className="input" value={editingCategoryForm.description} onChange={(e) => setEditingCategoryForm((s) => ({ ...s, description: e.target.value }))} />
          </label>
          <label className="field">
            <span className="field-label">Display Order</span>
            <input className="input" type="number" min={0} value={editingCategoryForm.displayOrder} onChange={(e) => setEditingCategoryForm((s) => ({ ...s, displayOrder: e.target.value }))} />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={editingCategoryForm.active} onChange={(e) => setEditingCategoryForm((s) => ({ ...s, active: e.target.checked }))} />
            Active
          </label>
        </div>
      </Modal>

      <Modal
        open={!!editingItem}
        title={editingItem ? `Edit Item: ${editingItem.name}` : "Edit Item"}
        onClose={() => setEditingItem(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setEditingItem(null)} disabled={editBusy}>
              Cancel
            </button>
            <button
              className="btn btn-primary btn-sm"
              disabled={editBusy || !editingItemForm.name.trim() || !editingItemForm.price.trim()}
              onClick={() => void saveItemEdit()}
            >
              {editBusy ? <Spinner className="text-current" /> : null}
              Save
            </button>
          </>
        }
      >
        <div className="grid gap-3">
          <label className="field">
            <span className="field-label">Category</span>
            <select className="select" value={editingItemForm.categoryId} onChange={(e) => setEditingItemForm((s) => ({ ...s, categoryId: e.target.value }))}>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field-label">Name</span>
            <input className="input" value={editingItemForm.name} onChange={(e) => setEditingItemForm((s) => ({ ...s, name: e.target.value }))} />
          </label>
          <label className="field">
            <span className="field-label">Description</span>
            <input className="input" value={editingItemForm.description} onChange={(e) => setEditingItemForm((s) => ({ ...s, description: e.target.value }))} />
          </label>
          <label className="field">
            <span className="field-label">Price</span>
            <input className="input" type="number" step="0.01" min="0.01" value={editingItemForm.price} onChange={(e) => setEditingItemForm((s) => ({ ...s, price: e.target.value }))} />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={editingItemForm.available} onChange={(e) => setEditingItemForm((s) => ({ ...s, available: e.target.checked }))} />
            Available
          </label>
        </div>
      </Modal>
    </main>
  );
}


