import { AnimatePresence, motion } from "framer-motion";
import { BarChart3, ChefHat, LayoutGrid, MonitorSmartphone, Truck, UserCheck, Wallet, WalletCards } from "lucide-react";
import { useId, useRef, useState } from "react";
import type { ScreenKey } from "../data/images";
import { BrowserFrame } from "./Media";
import { SectionHeader } from "./ui";

const tabs: { key: ScreenKey; label: string; icon: React.ComponentType<{ className?: string }>; text: string }[] = [
  { key: "dashboard", label: "Dashboard", icon: BarChart3, text: "Live service status, revenue, paid orders, average order value, best sellers, payment methods and busiest hours." },
  { key: "pos", label: "POS", icon: MonitorSmartphone, text: "Dishes by category on the left, the ticket on the right, with dine-in, pickup or delivery and a table picker." },
  { key: "kds", label: "Kitchen", icon: ChefHat, text: "A high-contrast kitchen display with waiting timers, late tickets highlighted, and allergy notes in plain sight." },
  { key: "floor", label: "Tables", icon: LayoutGrid, text: "Tables by zone with available, occupied, reserved and closed counts, seats and running totals." },
  { key: "financial", label: "Controls", icon: Wallet, text: "Discounts, voids and refunds need a manager's PIN. The close-of-day reconciliation sits below." },
  { key: "delivery", label: "Delivery", icon: Truck, text: "Assign riders to ready orders and manage delivery zones with fees, minimum orders and times." },
  { key: "attendance", label: "Attendance", icon: UserCheck, text: "Who is working, on break, late or absent today, with clock-in, clock-out and correction actions." },
  { key: "payroll", label: "Payroll", icon: WalletCards, text: "Payroll runs move from draft to calculated, approved by someone else, finalised and paid." },
];

/** Interactive tour of the real product screens. */
export default function ScreenshotShowcase() {
  const [active, setActive] = useState(0);
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const base = useId();

  const onKey = (e: React.KeyboardEvent, i: number) => {
    const n = tabs.length;
    const next = e.key === "ArrowRight" ? (i + 1) % n : e.key === "ArrowLeft" ? (i - 1 + n) % n : e.key === "Home" ? 0 : e.key === "End" ? n - 1 : -1;
    if (next < 0) return;
    e.preventDefault();
    setActive(next);
    refs.current[next]?.focus();
  };

  const tab = tabs[active];
  return (
    <section aria-labelledby="tour-title" className="relative overflow-hidden bg-ink py-24 text-cream sm:py-28">
      <div aria-hidden className="absolute -top-32 left-1/2 size-[50rem] -translate-x-1/2 rounded-full bg-[radial-gradient(circle,rgb(194_86_43/0.22),transparent_60%)]" />
      <div className="container-x relative">
        <SectionHeader
          id="tour-title"
          dark
          align="center"
          eyebrow="Take the tour"
          title="The real product, screen by screen."
          lead="Every screen on this page is taken from the working application. Choose a screen to see it, and click any screenshot to enlarge it."
        />

        <div role="tablist" aria-label="Product screens" className="mx-auto mt-12 flex max-w-4xl flex-wrap justify-center gap-2">
          {tabs.map((t, i) => (
            <button
              key={t.key}
              ref={(el) => { refs.current[i] = el; }}
              role="tab"
              id={`${base}-tab-${i}`}
              aria-selected={active === i}
              aria-controls={`${base}-panel`}
              tabIndex={active === i ? 0 : -1}
              onClick={() => setActive(i)}
              onKeyDown={(e) => onKey(e, i)}
              className={`relative flex min-h-11 items-center gap-2 rounded-full px-4 text-sm font-semibold transition-colors ${
                active === i ? "text-ink" : "text-cream/70 hover:bg-cream/8 hover:text-cream"
              }`}
            >
              {active === i && <motion.span layoutId="tour-pill" className="absolute inset-0 rounded-full bg-cream" transition={{ type: "spring", stiffness: 380, damping: 32 }} />}
              <t.icon className="relative size-4" aria-hidden />
              <span className="relative">{t.label}</span>
            </button>
          ))}
        </div>

        <div id={`${base}-panel`} role="tabpanel" aria-labelledby={`${base}-tab-${active}`} className="mx-auto mt-10 max-w-5xl">
          <AnimatePresence mode="wait">
            <motion.div
              key={tab.key}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <BrowserFrame name={tab.key} caption={tab.text} sizes="(min-width: 1024px) 64rem, 100vw" url={`${tab.label}`} className="[&_figcaption]:text-cream/70 [&_figcaption]:text-base" />
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </section>
  );
}
