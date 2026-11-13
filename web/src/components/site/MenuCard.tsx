import Link from "next/link";
import type { MenuItemRecord, PublicOfferRecord } from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import { FoodImage } from "@/components/site/FoodImage";
import { Icon } from "@/components/Icon";
import { OfferRibbon, offersFor } from "@/components/site/Offers";

type MenuCardProps = {
  item: MenuItemRecord;
  priority?: boolean;
  variant?: "grid" | "row";
  /** All public offers; the card shows the best one that involves this dish. */
  offers?: PublicOfferRecord[];
};

export function MenuCard({ item, priority = false, variant = "grid", offers = [] }: MenuCardProps) {
  const unavailable = !item.available;
  const dishOffers = offersFor(item.id, offers);

  if (variant === "row") {
    return (
      <Link
        href={`/explore/${item.id}`}
        className="group flex gap-4 rounded-card border border-line bg-surface p-3 transition-[border-color,box-shadow] duration-200 hover:border-line-strong hover:shadow-card"
      >
        <FoodImage src={item.imageUrl} alt={item.name} ratio="aspect-square" className="w-24 shrink-0 rounded-lg sm:w-28" />
        <div className="flex min-w-0 flex-1 flex-col justify-between gap-2 py-1">
          <div>
            <div className="flex items-start justify-between gap-3">
              <h3 className="font-display text-[15px] font-bold leading-snug text-ink group-hover:text-brand">{item.name}</h3>
              <span className="money shrink-0 text-[15px] text-ink">{formatMoney(item.price)}</span>
            </div>
            {item.description ? <p className="mt-1 line-clamp-2 text-sm leading-relaxed text-ink-soft">{item.description}</p> : null}
          </div>
          <OfferRibbon offers={dishOffers} />
          {unavailable ? <span className="badge badge-neutral w-fit">Sold out today</span> : null}
        </div>
      </Link>
    );
  }

  return (
    <Link
      href={`/explore/${item.id}`}
      className="group flex flex-col overflow-hidden rounded-xl border border-line bg-surface transition-[border-color,box-shadow,transform] duration-300 hover:-translate-y-0.5 hover:border-line-strong hover:shadow-lg"
    >
      <div className="relative overflow-hidden">
        <FoodImage
          src={item.imageUrl}
          alt={item.name}
          priority={priority}
          className={`transition-transform duration-700 ease-out group-hover:scale-[1.04] ${unavailable ? "grayscale-[0.6]" : ""}`}
        />
        <span className="absolute left-3 top-3 rounded-pill bg-surface/90 px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-ink-soft backdrop-blur">
          {item.categoryName}
        </span>
        {unavailable ? (
          <span className="absolute right-3 top-3 rounded-pill bg-espresso/85 px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.08em] text-espresso-ink">
            Sold out
          </span>
        ) : null}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-5">
        <div className="flex items-start justify-between gap-4">
          <h3 className="font-display text-[17px] font-bold leading-snug text-ink">{item.name}</h3>
          <span className="money shrink-0 text-[17px] text-brand">{formatMoney(item.price)}</span>
        </div>
        {item.description ? <p className="line-clamp-2 text-sm leading-relaxed text-ink-soft">{item.description}</p> : null}
        <OfferRibbon offers={dishOffers} />
        <span className="mt-auto inline-flex items-center gap-1.5 pt-2 text-sm font-semibold text-ink-soft transition-colors group-hover:text-brand">
          View dish <Icon name="arrowRight" size={16} className="transition-transform group-hover:translate-x-0.5" />
        </span>
      </div>
    </Link>
  );
}
