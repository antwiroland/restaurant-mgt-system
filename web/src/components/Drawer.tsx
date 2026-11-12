"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";

type DrawerProps = {
  open: boolean;
  title?: string;
  onClose: () => void;
  children: React.ReactNode;
  footer?: React.ReactNode;
};

function focusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(
    container.querySelectorAll<HTMLElement>(
      "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])",
    ),
  ).filter((el) => !el.hasAttribute("disabled") && !el.getAttribute("aria-hidden"));
}

export function Drawer({ open, title, onClose, children, footer }: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const firstFocusable = panelRef.current ? focusableElements(panelRef.current)[0] : null;
    firstFocusable?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
        return;
      }
      if (event.key !== "Tab" || !panelRef.current) return;

      const focusables = focusableElements(panelRef.current);
      if (focusables.length === 0) return;

      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const active = document.activeElement as HTMLElement | null;

      if (event.shiftKey && active === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      // Return focus to the control that opened the dialog.
      if (previouslyFocused && document.contains(previouslyFocused)) previouslyFocused.focus();
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open, onClose]);

  if (!open || typeof document === "undefined") return null;

  return createPortal(
    <div className="fixed inset-0 z-[var(--z-drawer)]">
      <button
        aria-label="Close drawer"
        className="absolute inset-0 bg-overlay backdrop-blur-[3px]"
        onClick={onClose}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="absolute inset-x-0 bottom-0 z-10 grid grid-cols-[minmax(0,1fr)] max-h-[90dvh] gap-4 overflow-y-auto overflow-x-hidden rounded-t-[var(--radius-modal)] bg-surface p-5 shadow-xl
                   animate-[modalIn_var(--duration-base)_var(--ease-out)] sm:inset-y-0 sm:right-0 sm:left-auto sm:h-full sm:max-h-none sm:w-[min(92vw,var(--layout-panel-max))] sm:rounded-none sm:border-l sm:border-line sm:p-6"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 className="font-display text-xl font-bold text-ink">{title}</h2>
          <button type="button" className="btn btn-ghost btn-icon btn-sm -mr-2 -mt-1" onClick={onClose} aria-label="Close">
            <Icon name="close" />
          </button>
        </div>
        <div>{children}</div>
        {footer ? <div className="flex flex-wrap justify-end gap-2">{footer}</div> : null}
      </div>
    </div>,
    document.body,
  );
}

