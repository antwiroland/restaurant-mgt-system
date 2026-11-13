import Link from "next/link";
import type { PublicOfferRecord } from "@/lib/apiClient";
import { Icon } from "@/components/Icon";

/** The offers that involve a dish, running ones first. */
export function offersFor(menuItemId: string, offers: PublicOfferRecord[]): PublicOfferRecord[] {
  return offers
    .filter((offer) => offer.menuItemIds.includes(menuItemId))
    .sort((a, b) => Number(b.runningNow) - Number(a.runningNow));
}

function when(offer: PublicOfferRecord): string {
  const parts = [offer.runningNow ? "On now" : offer.schedule ? `Runs ${offer.schedule}` : "Coming soon"];
  if (offer.runningNow && offer.schedule) parts.push(offer.schedule);
  if (offer.branchName) parts.push(`${offer.branchName} only`);
  return parts.join(" · ");
}

/** Small ribbon on a menu card: the dish's best offer. */
export function OfferRibbon({ offers }: { offers: PublicOfferRecord[] }) {
  const offer = offers[0];
  if (!offer) return null;
  return (
    <span
      className={`inline-flex w-fit max-w-full items-center gap-1 rounded-pill px-2.5 py-1 text-[11px] font-bold leading-tight ${
        offer.runningNow ? "bg-brand text-white" : "bg-brand-subtle text-brand"
      }`}
      title={when(offer)}
    >
      <Icon name="tag" size={12} className="shrink-0" />
      <span className="truncate">{offer.summary}</span>
    </span>
  );
}

/** "Deals" strip at the top of the menu. */
export function OffersStrip({ offers }: { offers: PublicOfferRecord[] }) {
  if (offers.length === 0) return null;
  const sorted = [...offers].sort((a, b) => Number(b.runningNow) - Number(a.runningNow));
  return (
    <section aria-labelledby="offers-title" className="mt-10">
      <h2 id="offers-title" className="flex items-center gap-2 font-display text-xl font-bold text-ink">
        <Icon name="sparkle" size={20} className="text-brand" /> Deals
      </h2>
      <ul className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {sorted.map((offer) => (
          <li key={offer.id} className="rounded-xl border border-line bg-surface p-4">
            <p className="font-display font-bold text-ink">{offer.summary}</p>
            {offer.description ? <p className="mt-1 text-sm text-ink-soft">{offer.description}</p> : null}
            <p className={`mt-2 text-xs font-semibold ${offer.runningNow ? "text-success-on" : "text-ink-soft"}`}>{when(offer)}</p>
            {offer.menuItemIds[0] ? (
              <Link href={`/explore/${offer.menuItemIds[0]}`} className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-brand">
                See the dish <Icon name="arrowRight" size={14} />
              </Link>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Offers callout on a dish page. */
export function DishOffers({ offers }: { offers: PublicOfferRecord[] }) {
  if (offers.length === 0) return null;
  return (
    <div className="mt-6 grid gap-2" aria-label="Offers on this dish">
      {offers.map((offer) => (
        <div key={offer.id} className="flex gap-3 rounded-xl border border-brand/30 bg-brand-subtle p-4">
          <Icon name="tag" size={20} className="mt-0.5 shrink-0 text-brand" />
          <div>
            <p className="font-display font-bold text-ink">{offer.summary}</p>
            <p className="mt-0.5 text-sm text-ink-soft">
              {when(offer)}. Applied automatically at checkout.
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
