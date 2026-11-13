"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { CategoryRecord, MenuItemRecord, PublicOfferRecord } from "@/lib/apiClient";
import { Icon } from "@/components/Icon";
import { MenuCard } from "@/components/site/MenuCard";

type Sort = "menu" | "price-asc" | "price-desc" | "name";

type MenuExplorerProps = {
  items: MenuItemRecord[];
  categories: CategoryRecord[];
  initialQuery?: string;
  initialCategory?: string;
  offers?: PublicOfferRecord[];
};

export function MenuExplorer({ items, categories, initialQuery = "", initialCategory = "all", offers = [] }: MenuExplorerProps) {
  const [query, setQuery] = useState(initialQuery);
  const [category, setCategory] = useState<string>(() =>
    initialCategory === "all" || categories.some((c) => c.id === initialCategory) ? initialCategory : "all",
  );
  const [sort, setSort] = useState<Sort>("menu");
  const [availableOnly, setAvailableOnly] = useState(false);
  const [view, setView] = useState<"grid" | "list">("grid");
  const searchRef = useRef<HTMLInputElement>(null);

  // /explore#search (header search button) lands with the field focused.
  useEffect(() => {
    if (window.location.hash === "#search") searchRef.current?.focus();
  }, []);

  // Keep the URL shareable
  useEffect(() => {
    const params = new URLSearchParams();
    if (query.trim()) params.set("q", query.trim());
    if (category !== "all") params.set("category", category);
    const next = `${window.location.pathname}${params.toString() ? `?${params}` : ""}`;
    window.history.replaceState(null, "", next);
  }, [query, category]);

  const categoryOrder = useMemo(() => new Map(categories.map((c, i) => [c.id, i])), [categories]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const list = items.filter((item) => {
      if (category !== "all" && item.categoryId !== category) return false;
      if (availableOnly && !item.available) return false;
      if (!needle) return true;
      return [item.name, item.description, item.categoryName].some((field) => field?.toLowerCase().includes(needle));
    });
    const price = (item: MenuItemRecord) => Number(item.price);
    switch (sort) {
      case "price-asc":
        return [...list].sort((a, b) => price(a) - price(b));
      case "price-desc":
        return [...list].sort((a, b) => price(b) - price(a));
      case "name":
        return [...list].sort((a, b) => a.name.localeCompare(b.name));
      default:
        return [...list].sort(
          (a, b) => (categoryOrder.get(a.categoryId) ?? 99) - (categoryOrder.get(b.categoryId) ?? 99) || a.name.localeCompare(b.name),
        );
    }
  }, [items, category, availableOnly, query, sort, categoryOrder]);

  // Group by category when browsing the whole menu in menu order
  const grouped = sort === "menu" && category === "all" && !query.trim();
  const sections = useMemo(() => {
    if (!grouped) return [];
    return categories
      .map((c) => ({ category: c, items: filtered.filter((item) => item.categoryId === c.id) }))
      .filter((section) => section.items.length > 0);
  }, [grouped, categories, filtered]);

  const counts = useMemo(() => {
    const map = new Map<string, number>();
    for (const item of items) map.set(item.categoryId, (map.get(item.categoryId) ?? 0) + 1);
    return map;
  }, [items]);

  const renderItems = (list: MenuItemRecord[]) =>
    view === "grid" ? (
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((item, index) => <MenuCard key={item.id} item={item} priority={index < 3} offers={offers} />)}
      </div>
    ) : (
      <div className="grid gap-3 md:grid-cols-2">
        {list.map((item) => <MenuCard key={item.id} item={item} variant="row" offers={offers} />)}
      </div>
    );

  return (
    <div className="grid gap-8">
      {/* Controls */}
      <div className="sticky top-[72px] z-[calc(var(--z-sticky)-1)] -mx-4 grid gap-3 border-b border-line bg-ivory/92 px-4 py-4 backdrop-blur-md sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <label className="relative block flex-1" id="search">
            <span className="sr-only">Search dishes</span>
            <Icon name="search" size={18} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-muted" />
            <input
              ref={searchRef}
              type="search"
              className="input pl-10"
              placeholder="Search dishes, ingredients or categories"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
              <span className="sr-only md:not-sr-only">Sort</span>
              <select className="select w-auto min-w-[170px]" value={sort} onChange={(event) => setSort(event.target.value as Sort)}>
                <option value="menu">Menu order</option>
                <option value="price-asc">Price: low to high</option>
                <option value="price-desc">Price: high to low</option>
                <option value="name">Name A–Z</option>
              </select>
            </label>
            <button type="button" className="chip" aria-pressed={availableOnly} onClick={() => setAvailableOnly((v) => !v)}>
              {availableOnly ? <Icon name="check" size={16} /> : null} Available now
            </button>
            <div className="segmented" role="group" aria-label="Layout">
              <button type="button" aria-pressed={view === "grid"} onClick={() => setView("grid")} aria-label="Grid view"><Icon name="dashboard" size={18} /></button>
              <button type="button" aria-pressed={view === "list"} onClick={() => setView("list")} aria-label="List view"><Icon name="menu" size={18} /></button>
            </div>
          </div>
        </div>
        <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-0.5 [scrollbar-width:none]" role="group" aria-label="Categories">
          <button type="button" className="chip shrink-0" aria-pressed={category === "all"} onClick={() => setCategory("all")}>
            All <span className="opacity-60">{items.length}</span>
          </button>
          {categories.map((c) => (
            <button key={c.id} type="button" className="chip shrink-0" aria-pressed={category === c.id} onClick={() => setCategory(c.id)}>
              {c.name} <span className="opacity-60">{counts.get(c.id) ?? 0}</span>
            </button>
          ))}
        </div>
      </div>

      <p className="sr-only" aria-live="polite">{filtered.length} dishes shown</p>

      {filtered.length === 0 ? (
        <div className="grid justify-items-center gap-3 rounded-2xl border border-dashed border-line-strong bg-surface px-6 py-16 text-center">
          <span className="grid h-12 w-12 place-items-center rounded-full bg-muted text-ink-muted"><Icon name="search" /></span>
          <p className="font-serif text-2xl text-ink">No dishes match</p>
          <p className="max-w-sm text-sm text-ink-soft">Try a different word, or clear the filters to see the whole menu.</p>
          <button type="button" className="btn btn-secondary mt-2" onClick={() => { setQuery(""); setCategory("all"); setAvailableOnly(false); }}>
            Clear filters
          </button>
        </div>
      ) : grouped ? (
        <div className="grid gap-14">
          {sections.map((section) => (
            <section key={section.category.id} aria-labelledby={`cat-${section.category.id}`} className="scroll-mt-48">
              <div className="mb-6 flex items-baseline justify-between gap-4 border-b border-line pb-3">
                <h2 id={`cat-${section.category.id}`} className="heading-serif text-3xl">{section.category.name}</h2>
                <span className="text-sm text-ink-muted">{section.items.length} dishes</span>
              </div>
              {section.category.description ? <p className="-mt-3 mb-6 max-w-2xl text-ink-soft">{section.category.description}</p> : null}
              {renderItems(section.items)}
            </section>
          ))}
        </div>
      ) : (
        renderItems(filtered)
      )}
    </div>
  );
}
