import Link from "next/link";
import { connection } from "next/server";
import { Icon, type IconName } from "@/components/Icon";
import { FoodImage } from "@/components/site/FoodImage";
import { MenuCard } from "@/components/site/MenuCard";
import { formatMoney } from "@/lib/brand";
import { fetchPublicMenu, getBrand } from "@/lib/publicServer";
import type { MenuItemRecord } from "@/lib/apiClient";

/** Prefer dishes that have photographs, then keep menu order. */
function featured(items: MenuItemRecord[], count: number) {
  const available = items.filter((item) => item.available);
  return [...available.filter((i) => i.imageUrl), ...available.filter((i) => !i.imageUrl)].slice(0, count);
}

const steps: { icon: IconName; title: string; body: string }[] = [
  { icon: "qr", title: "Scan your table", body: "Every table has a QR code. Open it with your phone camera — no app or account needed." },
  { icon: "book", title: "Order from the menu", body: "Choose dishes, add notes for the kitchen, and send your order straight to the pass." },
  { icon: "receipt", title: "Follow along", body: "Watch your order move from kitchen to table and see your running bill at any time." },
];

export default async function HomePage() {
  await connection();
  const [menu, brand] = await Promise.all([fetchPublicMenu(), getBrand()]);
  const items = menu?.items ?? [];
  const categories = menu?.categories ?? [];
  const highlights = featured(items, 6);
  const heroImage = brand.heroImage ?? items.find((item) => item.imageUrl && item.available)?.imageUrl;
  const tonight = items.filter((item) => item.available).slice(0, 5);
  const countByCategory = new Map<string, number>();
  for (const item of items) countByCategory.set(item.categoryId, (countByCategory.get(item.categoryId) ?? 0) + 1);

  return (
    <>
      {/* ------------------------------------------------ HERO */}
      <section className="relative overflow-hidden">
        <div className="site grid items-center gap-12 pb-16 pt-10 md:pb-24 md:pt-16 lg:grid-cols-[1.05fr_1fr] lg:gap-16">
          <div className="animate-fade-up">
            <p className="eyebrow">{brand.tagline}</p>
            <h1 className="heading-serif mt-5 text-[2.75rem] leading-[1.02] sm:text-6xl lg:text-[4.25rem]">
              Food worth <em className="text-brand">gathering</em> around.
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-ink-soft">
              {brand.about ??
                `Welcome to ${brand.name}. Browse tonight's menu, book a table for your next visit, or order straight from your seat when you're with us.`}
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link href="/explore" className="btn btn-primary btn-lg">
                Explore the menu <Icon name="arrowRight" size={18} />
              </Link>
              <Link href="/reserve" className="btn btn-secondary btn-lg">
                <Icon name="calendar" size={18} /> Reserve a table
              </Link>
            </div>
            <p className="mt-6 flex items-center gap-2 text-sm text-ink-soft">
              <Icon name="qr" size={18} className="text-brand" />
              Dining in? Scan the QR code on your table to order.
            </p>
          </div>

          <div className="relative animate-fade-up [animation-delay:120ms]">
            {heroImage ? (
              <div className="relative">
                <FoodImage
                  src={heroImage}
                  alt={`Signature dish at ${brand.name}`}
                  ratio="aspect-[4/5] sm:aspect-[5/5] lg:aspect-[4/5]"
                  priority
                  className="rounded-[28px] shadow-xl"
                />
                {tonight.length > 0 ? (
                  <div className="absolute -bottom-6 left-4 right-4 rounded-2xl border border-line bg-surface/95 p-4 shadow-lg backdrop-blur sm:left-auto sm:right-[-12px] sm:w-[280px]">
                    <p className="kicker">On tonight</p>
                    <ul className="mt-2 grid gap-1.5">
                      {tonight.slice(0, 3).map((item) => (
                        <li key={item.id} className="flex items-baseline justify-between gap-3 text-sm">
                          <span className="truncate font-semibold text-ink">{item.name}</span>
                          <span className="money shrink-0 text-ink-soft">{formatMoney(item.price)}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            ) : (
              <div className="relative mx-auto max-w-md">
                <div aria-hidden="true" className="absolute -inset-6 rotate-[-3deg] rounded-[32px] bg-cream" />
                <div className="relative rounded-[24px] border border-line bg-surface px-8 py-10 shadow-xl">
                  <p className="text-center font-serif text-sm italic text-ink-muted">{brand.name}</p>
                  <p className="mt-1 text-center text-[11px] font-bold uppercase tracking-[0.3em] text-brand">Tonight&apos;s menu</p>
                  <div className="mx-auto my-6 h-px w-16 bg-line-strong" />
                  {tonight.length > 0 ? (
                    <ul className="grid gap-5">
                      {tonight.map((item) => (
                        <li key={item.id}>
                          <div className="flex items-baseline gap-2">
                            <span className="font-serif text-lg font-medium text-ink">{item.name}</span>
                            <span className="mb-1 flex-1 border-b border-dotted border-line-strong" aria-hidden="true" />
                            <span className="money text-sm text-ink">{formatMoney(item.price)}</span>
                          </div>
                          {item.description ? <p className="mt-0.5 line-clamp-1 text-sm text-ink-soft">{item.description}</p> : null}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-center text-sm text-ink-soft">Our menu is being updated. Please check back shortly.</p>
                  )}
                  <div className="mx-auto mt-8 h-px w-16 bg-line-strong" />
                  <Link href="/explore" className="mt-6 flex items-center justify-center gap-2 text-sm font-semibold text-brand hover:underline">
                    See the full menu <Icon name="arrowRight" size={16} />
                  </Link>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* ------------------------------------------------ CATEGORIES */}
      {categories.length > 0 ? (
        <section aria-labelledby="categories-title" className="border-y border-line bg-surface">
          <div className="site py-8">
            <h2 id="categories-title" className="sr-only">Menu categories</h2>
            <ul className="-mx-1 flex gap-3 overflow-x-auto px-1 pb-1 [scrollbar-width:none]">
              {categories.map((category) => (
                <li key={category.id} className="shrink-0">
                  <Link
                    href={`/explore?category=${category.id}`}
                    className="group flex min-w-[168px] items-center justify-between gap-4 rounded-xl border border-line bg-ivory px-5 py-4 transition-colors hover:border-brand-soft hover:bg-brand-subtle"
                  >
                    <span>
                      <span className="block font-display text-[15px] font-bold text-ink">{category.name}</span>
                      <span className="text-xs text-ink-muted">{countByCategory.get(category.id) ?? 0} dishes</span>
                    </span>
                    <Icon name="arrowRight" size={16} className="text-ink-muted transition-transform group-hover:translate-x-0.5 group-hover:text-brand" />
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </section>
      ) : null}

      {/* ------------------------------------------------ FEATURED DISHES */}
      <section aria-labelledby="dishes-title" className="site py-20 md:py-24">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">From the kitchen</p>
            <h2 id="dishes-title" className="heading-serif mt-3 text-4xl md:text-5xl">On the menu today</h2>
          </div>
          <Link href="/explore" className="btn btn-secondary">
            View full menu <Icon name="arrowRight" size={16} />
          </Link>
        </div>
        {highlights.length > 0 ? (
          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {highlights.map((item, index) => (
              <MenuCard key={item.id} item={item} priority={index < 3} />
            ))}
          </div>
        ) : (
          <div className="mt-10 rounded-2xl border border-dashed border-line-strong bg-surface p-10 text-center">
            <p className="font-serif text-2xl text-ink">{menu ? "The menu is being prepared" : "Menu temporarily unavailable"}</p>
            <p className="mx-auto mt-2 max-w-md text-sm text-ink-soft">
              {menu ? "Dishes will appear here as soon as the kitchen publishes them." : "We couldn't load the menu just now. Please refresh in a moment."}
            </p>
          </div>
        )}
      </section>

      {/* ------------------------------------------------ HOW TO ORDER */}
      <section aria-labelledby="order-title" className="bg-cream/60">
        <div className="site grid gap-12 py-20 md:py-24 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
          <div>
            <p className="eyebrow">Dining with us</p>
            <h2 id="order-title" className="heading-serif mt-3 text-4xl md:text-5xl">Order from your seat.</h2>
            <p className="mt-5 max-w-md text-lg leading-relaxed text-ink-soft">
              Skip the wait for a menu. Your phone becomes your menu, your order pad and your bill — and our team is still right there when you want them.
            </p>
          </div>
          <ol className="grid gap-4 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
            {steps.map((step, index) => (
              <li key={step.title} className="rounded-2xl border border-line bg-surface p-6 shadow-xs">
                <div className="flex items-center justify-between">
                  <span className="grid h-11 w-11 place-items-center rounded-xl bg-brand-subtle text-brand"><Icon name={step.icon} size={22} /></span>
                  <span className="font-serif text-3xl text-line-strong">0{index + 1}</span>
                </div>
                <h3 className="mt-5 font-display text-lg font-bold">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-soft">{step.body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* ------------------------------------------------ RESERVE BAND */}
      <section className="site py-20 md:py-24">
        <div className="relative overflow-hidden rounded-[28px] bg-espresso px-6 py-14 text-espresso-ink sm:px-12 md:py-16">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0"
            style={{ background: "radial-gradient(50% 80% at 100% 0%, rgba(180,83,42,0.45) 0%, transparent 60%), radial-gradient(40% 70% at 0% 100%, rgba(201,164,92,0.25) 0%, transparent 60%)" }}
          />
          <div className="relative grid gap-8 md:grid-cols-[1.4fr_1fr] md:items-center">
            <div>
              <p className="eyebrow text-gold">Reservations</p>
              <h2 className="mt-3 font-serif text-4xl font-medium leading-tight text-espresso-ink md:text-5xl">Save your seat at the table.</h2>
              <p className="mt-4 max-w-lg text-espresso-soft">Pick a date, a time and a table that suits your party. We&apos;ll have it ready when you arrive.</p>
            </div>
            <div className="flex flex-wrap gap-3 md:justify-end">
              <Link href="/reserve" className="btn btn-primary btn-lg">Book a table</Link>
              <Link href="/reserve#manage" className="btn btn-lg border-espresso-line bg-transparent text-espresso-ink hover:bg-espresso-raised">Manage booking</Link>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------ ABOUT + VISIT */}
      <section id="about" aria-labelledby="about-title" className="scroll-mt-24 border-t border-line bg-surface">
        <div className="site grid gap-12 py-20 md:grid-cols-2 md:py-24">
          <div>
            <p className="eyebrow">About</p>
            <h2 id="about-title" className="heading-serif mt-3 text-4xl">{brand.name}</h2>
            <p className="mt-5 text-lg leading-relaxed text-ink-soft">
              {brand.about ?? `${brand.tagline}. Everything on our menu is listed with today's availability, so what you see is what the kitchen is cooking.`}
            </p>
            <ul className="mt-8 grid gap-4 sm:grid-cols-2">
              {[
                { icon: "leaf" as const, title: "Live availability", body: "Dishes that sell out are marked straight away." },
                { icon: "chef" as const, title: "Straight to the kitchen", body: "Table orders reach the pass the moment you send them." },
              ].map((row) => (
                <li key={row.title} className="flex gap-3">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-sage-subtle text-sage"><Icon name={row.icon} size={20} /></span>
                  <span>
                    <span className="block font-display font-bold text-ink">{row.title}</span>
                    <span className="text-sm text-ink-soft">{row.body}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <div id="visit" className="scroll-mt-24 rounded-2xl border border-line bg-ivory p-7">
            <p className="eyebrow">Visit</p>
            <h2 className="heading-serif mt-3 text-3xl">Find us</h2>
            <dl className="mt-6 grid gap-5 text-sm">
              {brand.address ? (
                <div className="flex gap-3"><Icon name="pin" className="mt-0.5 text-brand" /><div><dt className="font-semibold text-ink">Address</dt><dd className="text-ink-soft">{brand.address}</dd></div></div>
              ) : null}
              <div className="flex gap-3">
                <Icon name="clock" className="mt-0.5 text-brand" />
                <div>
                  <dt className="font-semibold text-ink">Opening hours</dt>
                  {brand.hours.length > 0 ? brand.hours.map((line) => <dd key={line} className="text-ink-soft">{line}</dd>) : <dd className="text-ink-soft">Please call ahead for today&apos;s hours.</dd>}
                </div>
              </div>
              {brand.phone ? (
                <div className="flex gap-3"><Icon name="phone" className="mt-0.5 text-brand" /><div><dt className="font-semibold text-ink">Phone</dt><dd><a className="text-ink-soft hover:text-brand" href={`tel:${brand.phone.replace(/\s+/g, "")}`}>{brand.phone}</a></dd></div></div>
              ) : null}
              {brand.email ? (
                <div className="flex gap-3"><Icon name="mail" className="mt-0.5 text-brand" /><div><dt className="font-semibold text-ink">Email</dt><dd><a className="text-ink-soft hover:text-brand" href={`mailto:${brand.email}`}>{brand.email}</a></dd></div></div>
              ) : null}
            </dl>
            <Link href="/reserve" className="btn btn-primary btn-md mt-7">Reserve a table</Link>
          </div>
        </div>
      </section>
    </>
  );
}
