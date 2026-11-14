import { motion, useScroll, useTransform } from "framer-motion";
import { Calculator, Keyboard, Layers, ListChecks, PackageCheck, Tags } from "lucide-react";
import { useRef } from "react";
import { BrowserFrame } from "../components/Media";
import { Reveal, SectionHeader } from "../components/ui";

const points = [
  { icon: Layers, title: "Process orders", text: "Dine-in, pickup and delivery from one screen, with a table grid that shows which tables are free." },
  { icon: Calculator, title: "Handle bills", text: "Offers, promo codes, service charge and each tax line are worked out automatically." },
  { icon: ListChecks, title: "Manage transactions", text: "Every order with its status, channel and next step: start prep, mark ready, hand over." },
  { icon: Keyboard, title: "Built for speed", text: "Keyboard search adds dishes in seconds, and required options can't be skipped." },
  { icon: Tags, title: "Menus and offers", text: "Update dishes, photos and availability, and run combos, buy-X-get-Y and happy hours." },
  { icon: PackageCheck, title: "Stock that follows sales", text: "Recipes deduct ingredients per branch, and a dish is marked sold out when an ingredient runs out." },
];

export default function POSSection() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "center center"] });
  const rotate = useTransform(scrollYProgress, [0, 1], [8, 0]);
  const scale = useTransform(scrollYProgress, [0, 1], [0.92, 1]);

  return (
    <section id="pos" aria-labelledby="pos-title" className="bg-cream-2/60 py-24 sm:py-28">
      <div className="container-x">
        <SectionHeader
          id="pos-title"
          align="center"
          eyebrow="Restaurant POS"
          title={<>Power Your Restaurant <span className="text-terra">Operations.</span></>}
          lead="A fast, touch-friendly point of sale built for busy service. The menu and the ticket sit side by side, prices are calculated for you, and orders go straight to the kitchen."
        />

        <div ref={ref} className="mx-auto mt-14 max-w-5xl [perspective:1600px]">
          <motion.div style={{ rotateX: rotate, scale }} className="origin-bottom">
            <BrowserFrame name="pos" caption="The point of sale: dishes by category on the left; the ticket, order type and table picker on the right." sizes="(min-width: 1024px) 64rem, 100vw" url="Point of Sale" />
          </motion.div>
        </div>

        <ul className="mt-16 grid gap-x-10 gap-y-8 sm:grid-cols-2 lg:grid-cols-3">
          {points.map((p, i) => (
            <Reveal key={p.title} delay={(i % 3) * 0.06}>
              <li className="flex gap-4">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-white text-terra shadow-soft"><p.icon className="size-5" aria-hidden /></span>
                <div>
                  <p className="font-bold text-ink">{p.title}</p>
                  <p className="mt-1 text-[15px] leading-relaxed text-ink-soft">{p.text}</p>
                </div>
              </li>
            </Reveal>
          ))}
        </ul>

        <div className="mt-16 grid gap-6 md:grid-cols-3">
          <Reveal><BrowserFrame name="orders" caption="All orders, with status tabs and one-click next steps." sizes="(min-width: 768px) 33vw, 100vw" url="Orders" /></Reveal>
          <Reveal delay={0.08}><BrowserFrame name="menu" caption="Menu builder with availability switches." sizes="(min-width: 768px) 33vw, 100vw" url="Menu builder" /></Reveal>
          <Reveal delay={0.16}><BrowserFrame name="inventory" caption="Ingredients and stock with low-stock status." sizes="(min-width: 768px) 33vw, 100vw" url="Inventory" /></Reveal>
        </div>
      </div>
    </section>
  );
}
