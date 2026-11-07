import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Icon } from "@/components/Icon";
import { FoodImage } from "@/components/site/FoodImage";
import { MenuCard } from "@/components/site/MenuCard";
import { formatMoney } from "@/lib/brand";
import { fetchPublicMenu, fetchPublicMenuItem } from "@/lib/publicServer";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const data = await fetchPublicMenuItem(id);
  return data ? { title: data.item.name, description: data.item.description } : { title: "Dish" };
}

export default async function DishPage({ params }: Props) {
  await connection();
  const { id } = await params;
  const [data, menu] = await Promise.all([fetchPublicMenuItem(id), fetchPublicMenu()]);
  if (!data || !data.item.active) notFound();
  const { item, modifiers } = data;
  const related = (menu?.items ?? []).filter((other) => other.categoryId === item.categoryId && other.id !== item.id && other.available).slice(0, 3);

  return (
    <div className="pb-24">
      <div className="site pt-6">
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-sm text-ink-muted">
          <Link href="/explore" className="hover:text-brand">Menu</Link>
          <Icon name="chevronRight" size={14} />
          <Link href={`/explore?category=${item.categoryId}`} className="hover:text-brand">{item.categoryName}</Link>
          <Icon name="chevronRight" size={14} />
          <span className="truncate text-ink-soft" aria-current="page">{item.name}</span>
        </nav>
      </div>

      <section className="site mt-6 grid gap-10 lg:grid-cols-[1.1fr_1fr] lg:gap-14">
        <FoodImage src={item.imageUrl} alt={item.name} ratio="aspect-[4/3] lg:aspect-[5/4]" priority className="rounded-[24px] shadow-lg" />

        <div className="flex flex-col">
          <p className="eyebrow">{item.categoryName}</p>
          <h1 className="heading-serif mt-3 text-4xl leading-tight md:text-5xl">{item.name}</h1>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            <span className="money font-display text-3xl text-brand">{formatMoney(item.price)}</span>
            {item.available ? (
              <span className="badge badge-success badge-dot">Available today</span>
            ) : (
              <span className="badge badge-neutral">Sold out today</span>
            )}
          </div>
          {item.description ? <p className="mt-6 text-lg leading-relaxed text-ink-soft">{item.description}</p> : null}

          {modifiers.length > 0 ? (
            <div className="mt-8 grid gap-5">
              {modifiers.map((group) => (
                <section key={group.id} className="rounded-xl border border-line bg-surface p-5">
                  <div className="flex items-center justify-between gap-3">
                    <h2 className="font-display text-base font-bold">{group.name}</h2>
                    <span className="text-xs font-semibold text-ink-muted">
                      {group.required ? "Required · " : "Optional · "}
                      {group.selectionType === "SINGLE" ? "choose one" : group.maxSelect ? `up to ${group.maxSelect}` : "choose any"}
                    </span>
                  </div>
                  <ul className="mt-3 divide-y divide-line-subtle">
                    {group.options.map((option) => (
                      <li key={option.id} className="flex items-center justify-between py-2.5 text-[15px]">
                        <span className="text-ink">{option.name}</span>
                        <span className="money text-sm text-ink-soft">
                          {Number(option.priceDelta) > 0 ? `+ ${formatMoney(option.priceDelta)}` : "Included"}
                        </span>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          ) : null}

          <div className="mt-8 rounded-2xl bg-cream/70 p-6">
            <div className="flex gap-4">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-surface text-brand shadow-xs"><Icon name="qr" size={22} /></span>
              <div>
                <p className="font-display font-bold text-ink">Order this at your table</p>
                <p className="mt-1 text-sm leading-relaxed text-ink-soft">
                  Scan the QR code on your table to add {item.name} to your order, choose your options, and leave a note for the kitchen.
                </p>
              </div>
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              <Link href="/reserve" className="btn btn-primary"><Icon name="calendar" size={18} /> Reserve a table</Link>
              <Link href="/explore" className="btn btn-secondary">Back to menu</Link>
            </div>
          </div>
        </div>
      </section>

      {related.length > 0 ? (
        <section className="site mt-24" aria-labelledby="related-title">
          <h2 id="related-title" className="heading-serif text-3xl">More {item.categoryName.toLowerCase()}</h2>
          <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {related.map((other) => <MenuCard key={other.id} item={other} />)}
          </div>
        </section>
      ) : null}
    </div>
  );
}
