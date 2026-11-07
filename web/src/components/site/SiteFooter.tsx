import Link from "next/link";
import { BrandMark } from "@/components/NavSidebar";
import { Icon } from "@/components/Icon";
import { brand } from "@/lib/brand";

export function SiteFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="bg-espresso text-espresso-soft">
      <div className="site grid gap-10 py-14 md:grid-cols-[1.4fr_1fr_1fr]">
        <div className="grid content-start gap-4">
          <div className="flex items-center gap-3">
            <BrandMark size={40} />
            <span className="font-serif text-2xl font-semibold text-espresso-ink">{brand.name}</span>
          </div>
          <p className="max-w-sm text-sm leading-relaxed">{brand.tagline}.</p>
          {brand.address || brand.phone || brand.email ? (
            <ul className="grid gap-2 text-sm">
              {brand.address ? <li className="flex items-start gap-2"><Icon name="pin" size={16} className="mt-0.5 text-gold" />{brand.address}</li> : null}
              {brand.phone ? <li><a className="flex items-center gap-2 hover:text-espresso-ink" href={`tel:${brand.phone.replace(/\s+/g, "")}`}><Icon name="phone" size={16} className="text-gold" />{brand.phone}</a></li> : null}
              {brand.email ? <li><a className="flex items-center gap-2 hover:text-espresso-ink" href={`mailto:${brand.email}`}><Icon name="mail" size={16} className="text-gold" />{brand.email}</a></li> : null}
            </ul>
          ) : null}
        </div>

        <nav aria-label="Footer" className="grid content-start gap-2 text-sm">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.16em] text-espresso-ink">Explore</p>
          <Link className="hover:text-espresso-ink" href="/explore">Menu</Link>
          <Link className="hover:text-espresso-ink" href="/reserve">Book a table</Link>
          <Link className="hover:text-espresso-ink" href="/reserve#manage">Manage a booking</Link>
          <Link className="hover:text-espresso-ink" href="/orders-status">Order status board</Link>
        </nav>

        <div className="grid content-start gap-2 text-sm">
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.16em] text-espresso-ink">Hours</p>
          {brand.hours.length > 0 ? (
            brand.hours.map((line) => <p key={line}>{line}</p>)
          ) : (
            <p>Ask our team for today&apos;s hours.</p>
          )}
        </div>
      </div>
      <div className="border-t border-espresso-line">
        <div className="site flex flex-col items-start justify-between gap-2 py-5 text-xs sm:flex-row sm:items-center">
          <p>© {year} {brand.name}</p>
          <Link href="/login" className="hover:text-espresso-ink">Staff sign-in</Link>
        </div>
      </div>
    </footer>
  );
}
