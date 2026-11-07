"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { useT } from "@/components/I18nProvider";
import { useStaffSession } from "@/components/SessionProvider";
import { getFavourites, setFavourite } from "@/lib/apiClient";

/** Heart toggle for signed-in customers; sends guests to sign in. */
export function FavouriteButton({ menuItemId }: { menuItemId: string }) {
  const t = useT();
  const router = useRouter();
  const { session, authenticatedFetch } = useStaffSession();
  const [on, setOn] = useState(false);
  const [busy, setBusy] = useState(false);
  const isCustomer = session?.user.role === "CUSTOMER";

  useEffect(() => {
    if (!isCustomer) return;
    let cancelled = false;
    authenticatedFetch((s) => getFavourites(s))
      .then((items) => !cancelled && setOn(items.some((i) => i.id === menuItemId)))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [authenticatedFetch, isCustomer, menuItemId]);

  if (session && !isCustomer) return null;

  async function toggle() {
    if (!isCustomer) {
      router.push(`/account?next=${encodeURIComponent(window.location.pathname)}`);
      return;
    }
    setBusy(true);
    const next = !on;
    setOn(next);
    try {
      await authenticatedFetch((s) => setFavourite(s, menuItemId, next));
    } catch {
      setOn(!next);
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      type="button"
      className={`btn btn-secondary btn-icon btn-lg ${on ? "text-brand" : ""}`}
      aria-pressed={on}
      aria-label={on ? t("menu.unfavourite") : t("menu.favourite")}
      title={on ? t("menu.unfavourite") : t("menu.favourite")}
      disabled={busy}
      onClick={() => void toggle()}
    >
      <Icon name="heart" size={20} filled={on} />
    </button>
  );
}
