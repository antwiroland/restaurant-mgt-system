"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import type { StaffRole } from "@/lib/apiClient";
import { roleLabel } from "@/lib/navigation";

type AccountMenuProps = {
  name: string;
  role?: StaffRole;
  onSignOut: () => void;
  dark?: boolean;
};

/** Avatar button in the top bar with the signed-in user, My Account and Sign out — always reachable. */
export function AccountMenu({ name, role, onSignOut, dark = false }: AccountMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${name}`}
        className={`flex items-center gap-2 rounded-full py-1 pl-1 pr-2 transition-colors ${
          dark ? "hover:bg-night-card" : "hover:bg-sunken"
        }`}
      >
        <span className={`grid h-9 w-9 place-items-center rounded-full text-sm font-bold ${dark ? "bg-night-card text-gold" : "bg-espresso text-gold"}`}>
          {name.charAt(0).toUpperCase()}
        </span>
        <span className={`hidden text-left leading-tight md:block ${dark ? "text-night-ink" : "text-ink"}`}>
          <span className="block max-w-[140px] truncate text-sm font-semibold">{name}</span>
          {role ? <span className={`block text-[11px] ${dark ? "text-night-soft" : "text-ink-muted"}`}>{roleLabel[role]}</span> : null}
        </span>
        <Icon name="chevronDown" size={16} className={dark ? "text-night-soft" : "text-ink-muted"} />
      </button>

      {open ? (
        <div
          role="menu"
          aria-label="Account"
          className="absolute right-0 top-full z-[var(--z-drawer)] mt-2 w-60 overflow-hidden rounded-xl border border-line bg-surface text-ink shadow-lg"
        >
          <div className="border-b border-line-subtle px-4 py-3">
            <p className="truncate text-sm font-semibold">{name}</p>
            {role ? <p className="text-xs text-ink-muted">{roleLabel[role]}</p> : null}
          </div>
          <Link
            href="/account"
            role="menuitem"
            onClick={() => setOpen(false)}
            className="flex min-h-[44px] items-center gap-3 px-4 text-sm font-semibold text-ink-soft hover:bg-sunken hover:text-ink"
          >
            <Icon name="user" size={18} /> My account
          </Link>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              onSignOut();
            }}
            className="flex min-h-[44px] w-full items-center gap-3 border-t border-line-subtle px-4 text-sm font-semibold text-danger-on hover:bg-danger-subtle"
          >
            <Icon name="logout" size={18} /> Sign out
          </button>
        </div>
      ) : null}
    </div>
  );
}
