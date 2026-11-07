"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import { useT } from "@/components/I18nProvider";
import { FavouriteButton } from "@/components/site/FavouriteButton";
import type { MenuItemRecord, MenuModifierGroupRecord } from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { cart } from "@/lib/cart";

/** Option picker + quantity + "Add to order" for pickup/delivery on the dish page. */
export function AddToCart({ item, modifiers }: { item: MenuItemRecord; modifiers: MenuModifierGroupRecord[] }) {
  const t = useT();
  const [selected, setSelected] = useState<Record<string, string[]>>(() => {
    const initial: Record<string, string[]> = {};
    for (const group of modifiers) {
      if (group.required && group.selectionType === "SINGLE" && group.options[0]) initial[group.id] = [group.options[0].id];
    }
    return initial;
  });
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState("");
  const [added, setAdded] = useState(false);

  const options = modifiers.flatMap((g) => g.options.map((o) => ({ ...o, group: g })));
  const chosen = options.filter((o) => (selected[o.group.id] ?? []).includes(o.id));
  const unit = Number(item.price) + chosen.reduce((sum, o) => sum + Number(o.priceDelta), 0);
  const missing = modifiers.filter((g) => {
    const count = (selected[g.id] ?? []).length;
    return (g.required && count === 0) || (g.minSelect ? count < g.minSelect : false);
  });

  function toggle(group: MenuModifierGroupRecord, optionId: string) {
    setAdded(false);
    setSelected((current) => {
      const now = current[group.id] ?? [];
      if (group.selectionType === "SINGLE") return { ...current, [group.id]: now.includes(optionId) && !group.required ? [] : [optionId] };
      if (now.includes(optionId)) return { ...current, [group.id]: now.filter((id) => id !== optionId) };
      if (group.maxSelect && now.length >= group.maxSelect) return current;
      return { ...current, [group.id]: [...now, optionId] };
    });
  }

  function add() {
    if (missing.length > 0) return;
    cart.add({
      menuItemId: item.id,
      name: item.name,
      imageUrl: item.imageUrl,
      unitPrice: unit,
      quantity,
      notes: notes.trim() || undefined,
      modifierOptionIds: chosen.map((o) => o.id),
      modifierLabels: chosen.map((o) => o.name),
    });
    setAdded(true);
    setQuantity(1);
    setNotes("");
  }

  if (!item.available) {
    return <p className="mt-8 alert alert-info">{t("menu.soldOutToday")}</p>;
  }

  return (
    <div className="mt-8 grid gap-5">
      {modifiers.map((group) => (
        <fieldset key={group.id} className="rounded-xl border border-line bg-surface p-5">
          <legend className="sr-only">{group.name}</legend>
          <div className="flex items-center justify-between gap-3">
            <p className="font-display text-base font-bold">{group.name}</p>
            <span className={`text-xs font-semibold ${missing.includes(group) ? "text-danger-on" : "text-ink-muted"}`}>
              {group.required ? "Required · " : "Optional · "}
              {group.selectionType === "SINGLE" ? "choose one" : group.maxSelect ? `up to ${group.maxSelect}` : "choose any"}
            </span>
          </div>
          <ul className="mt-3 divide-y divide-line-subtle">
            {group.options.map((option) => {
              const on = (selected[group.id] ?? []).includes(option.id);
              return (
                <li key={option.id}>
                  <label className="flex cursor-pointer items-center justify-between gap-3 py-2.5 text-[15px]">
                    <span className="flex items-center gap-3">
                      <input
                        type={group.selectionType === "SINGLE" ? "radio" : "checkbox"}
                        name={`mod-${group.id}`}
                        checked={on}
                        onChange={() => toggle(group, option.id)}
                        className="h-4 w-4 accent-[var(--color-brand)]"
                      />
                      <span className="text-ink">{option.name}</span>
                    </span>
                    <span className="money text-sm text-ink-soft">{Number(option.priceDelta) > 0 ? `+ ${formatMoney(option.priceDelta)}` : t("menu.included")}</span>
                  </label>
                </li>
              );
            })}
          </ul>
        </fieldset>
      ))}

      <label className="field">
        <span className="field-label">{t("cart.note")} <span className="font-normal text-ink-muted">({t("common.optional")})</span></span>
        <input className="input" maxLength={200} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="No onions, extra spicy…" />
      </label>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center rounded-full border border-line bg-surface" role="group" aria-label="Quantity">
          <button type="button" className="btn btn-ghost btn-icon" aria-label="Fewer" onClick={() => setQuantity((q) => Math.max(1, q - 1))}><Icon name="minus" size={18} /></button>
          <span className="w-8 text-center font-display text-lg font-bold tabular" aria-live="polite">{quantity}</span>
          <button type="button" className="btn btn-ghost btn-icon" aria-label="More" onClick={() => setQuantity((q) => Math.min(99, q + 1))}><Icon name="plus" size={18} /></button>
        </div>
        <button type="button" className="btn btn-primary btn-lg flex-1" disabled={missing.length > 0} onClick={add}>
          <Icon name="bag" size={18} /> {t("menu.addToCart")} · <span className="money">{formatMoney(unit * quantity)}</span>
        </button>
        <FavouriteButton menuItemId={item.id} />
      </div>
      {added ? (
        <p className="alert alert-success" role="status">
          <Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />
          <span>{t("menu.added")} — <Link href="/cart" className="font-semibold underline">{t("cart.checkout")}</Link> or keep browsing.</span>
        </p>
      ) : null}
    </div>
  );
}
