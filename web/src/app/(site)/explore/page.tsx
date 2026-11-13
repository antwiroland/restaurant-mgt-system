import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import { MenuExplorer } from "@/components/site/MenuExplorer";
import { Icon } from "@/components/Icon";
import { OffersStrip } from "@/components/site/Offers";
import { fetchPublicMenu, fetchPublicOffers } from "@/lib/publicServer";

export const metadata: Metadata = {
  title: "Menu",
  description: "Browse the full menu with live availability.",
};

type Props = { searchParams: Promise<{ q?: string | string[]; category?: string | string[] }> };

export default async function ExplorePage({ searchParams }: Props) {
  await connection();
  const [menu, offers, params] = await Promise.all([fetchPublicMenu(), fetchPublicOffers(), searchParams]);
  const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value) ?? "";

  return (
    <div className="site pb-24 pt-10 md:pt-14">
      <header className="max-w-2xl">
        <p className="eyebrow">The menu</p>
        <h1 className="heading-serif mt-3 text-5xl md:text-6xl">What we&apos;re cooking</h1>
        <p className="mt-4 text-lg text-ink-soft">Prices and availability update live from our kitchen. To order, scan the QR code on your table.</p>
      </header>

      <OffersStrip offers={offers} />

      <div className="mt-10">
        {menu ? (
          menu.items.length > 0 ? (
            <MenuExplorer items={menu.items} categories={menu.categories} initialQuery={first(params.q)} initialCategory={first(params.category) || "all"} offers={offers} />
          ) : (
            <div className="rounded-2xl border border-dashed border-line-strong bg-surface p-12 text-center">
              <p className="font-serif text-2xl">The menu is being prepared</p>
              <p className="mt-2 text-sm text-ink-soft">Dishes will appear here as soon as the kitchen publishes them.</p>
            </div>
          )
        ) : (
          <div className="rounded-2xl border border-danger-border bg-danger-subtle p-10 text-center text-danger-on" role="alert">
            <p className="font-serif text-2xl">Menu temporarily unavailable</p>
            <p className="mt-2 text-sm">We couldn&apos;t reach the kitchen just now. Please try again in a moment.</p>
            <Link href="/explore" className="btn btn-danger mt-5"><Icon name="refresh" size={16} /> Try again</Link>
          </div>
        )}
      </div>
    </div>
  );
}
