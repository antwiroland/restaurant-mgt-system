import { motion } from "framer-motion";
import { BellRing, ChefHat, CreditCard, Gift, QrCode, ScanLine, ShoppingBag, Smartphone, UtensilsCrossed } from "lucide-react";
import { useState } from "react";
import { Photo, PhoneFrame, Shot } from "../components/Media";
import { Reveal, SectionHeader } from "../components/ui";
import type { ScreenKey } from "../data/images";

const journey: { icon: React.ComponentType<{ className?: string }>; title: string; text: string; screen: ScreenKey }[] = [
  { icon: QrCode, title: "Scan the table's QR code", text: "Every table has its own permanent QR code. The guest points their camera at it, with no app to download and no account to create.", screen: "qr-welcome" },
  { icon: Smartphone, title: "The menu opens on their phone", text: "They enter a phone number (a name is optional) and the restaurant's live menu opens for that table.", screen: "qr-welcome" },
  { icon: UtensilsCrossed, title: "Browse food and drinks", text: "Dishes by category, with options such as size or spice level and current offers.", screen: "qr-menu" },
  { icon: ShoppingBag, title: "Place the order", text: "They review the order, add a note for the kitchen and send it.", screen: "qr-cart" },
  { icon: ChefHat, title: "The restaurant receives it", text: "The order lands on the kitchen display, and the guest follows it live: received, accepted, cooking, ready, served.", screen: "qr-tracker" },
  { icon: BellRing, title: "Call a waiter anytime", text: "\"Call a waiter\" and \"Ask for the bill\" reach the team's screens instantly.", screen: "waiter-call" },
  { icon: CreditCard, title: "Settle the bill", text: "Pay the whole bill by Mobile Money from the phone, split it equally, or pay the team in cash or by card.", screen: "qr-bill" },
];

export default function CustomerOrdering() {
  const [step, setStep] = useState(0);
  const current = journey[step];
  return (
    <section id="how-it-works" aria-labelledby="ordering-title" className="relative overflow-hidden py-24 sm:py-28">
      <div aria-hidden className="absolute inset-y-0 right-0 -z-10 w-full bg-gradient-to-b from-cream-2/0 via-cream-2/70 to-cream-2/0" />
      <div className="container-x">
        <SectionHeader
          id="ordering-title"
          eyebrow="Customer experience · How it works"
          title={<>Let Your Customers <span className="text-terra">Order Their Way.</span></>}
          lead="At the table, at the counter, for pickup or for delivery. Guests order at their own pace, and every order goes straight to the people who make and serve it."
        />

        <div className="mt-14 grid items-start gap-12 lg:grid-cols-[1fr_auto] lg:gap-16">
          <ol className="relative grid gap-2" aria-label="The guest journey">
            <span aria-hidden className="absolute top-6 bottom-6 left-[1.45rem] w-px bg-line" />
            {journey.map((j, i) => (
              <li key={j.title}>
                <button
                  type="button"
                  onClick={() => setStep(i)}
                  onMouseEnter={() => setStep(i)}
                  aria-pressed={step === i}
                  className={`relative flex w-full gap-4 rounded-2xl p-3 text-left transition-colors ${step === i ? "bg-white shadow-soft ring-1 ring-ink/6" : "hover:bg-white/60"}`}
                >
                  <span className={`relative z-10 flex size-11 shrink-0 items-center justify-center rounded-full font-bold transition-colors ${step === i ? "bg-terra text-white" : "bg-cream-2 text-ink-soft ring-4 ring-cream"}`}>
                    <j.icon className="size-5" aria-hidden />
                  </span>
                  <span>
                    <span className="block text-xs font-bold tracking-wider text-ink-muted uppercase">Step {i + 1}</span>
                    <span className="block font-bold text-ink">{j.title}</span>
                    <span className={`block overflow-hidden text-[15px] leading-relaxed text-ink-soft transition-all ${step === i ? "mt-1 max-h-40 opacity-100" : "max-h-0 opacity-0 lg:max-h-0"}`}>{j.text}</span>
                  </span>
                </button>
              </li>
            ))}
          </ol>

          <div className="relative mx-auto flex justify-center lg:sticky lg:top-28">
            <div className="absolute -top-6 -right-10 hidden size-56 overflow-hidden rounded-full shadow-lift ring-8 ring-cream lg:block">
              <Photo name="dining-group" sizes="14rem" className="size-full" />
            </div>
            <motion.div key={current.screen} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
              {current.screen === "waiter-call" ? (
                <div className="w-72 rounded-[2rem] bg-white p-4 shadow-lift ring-1 ring-ink/6">
                  <p className="mb-3 text-center text-xs font-bold tracking-wider text-ink-muted uppercase">On the staff screen</p>
                  <PhoneFrameless />
                </div>
              ) : (
                <PhoneFrame name={current.screen} width="w-64 sm:w-72" caption="Real screen from the table QR ordering page" />
              )}
            </motion.div>
          </div>
        </div>

        {/* customer app */}
        <Reveal className="mt-24 rounded-[2.5rem] bg-ink p-6 text-cream sm:p-10 lg:p-14">
          <div className="grid items-center gap-10 lg:grid-cols-[0.9fr_1.1fr]">
            <div>
              <p className="eyebrow text-gold">Customer app</p>
              <h3 className="mt-3 font-display text-3xl leading-tight font-semibold sm:text-4xl">Pickup, delivery and loyalty in their pocket.</h3>
              <p className="mt-4 text-[17px] leading-relaxed text-cream/75">
                Beyond the table, customers can use the mobile app to order ahead for pickup or delivery, book a table, take up current deals,
                pay by Mobile Money or card, collect loyalty points and reorder favourites.
              </p>
              <ul className="mt-6 grid gap-3 text-[15px] text-cream/85">
                {[
                  [Gift, "Deals and loyalty points spent at checkout"],
                  [ScanLine, "Scan a table code or book a table for later"],
                  [CreditCard, "Mobile Money or card, with tips never taxed"],
                ].map(([Icon, t], i) => {
                  const I = Icon as React.ComponentType<{ className?: string }>;
                  return <li key={i} className="flex items-center gap-3"><I className="size-5 text-gold" aria-hidden />{t as string}</li>;
                })}
              </ul>
            </div>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              {(["app-home", "app-cart", "app-checkout", "app-orders"] as ScreenKey[]).map((k, i) => (
                <PhoneFrame key={k} name={k} width="w-full" className={`${i % 2 ? "sm:mt-10" : ""} [&_figcaption]:text-cream/60`} caption={["Deals & menu", "Cart & promos", "Checkout", "Order history"][i]} />
              ))}
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

// the waiter-call notification is a small desktop crop; show it plainly instead of in a phone
function PhoneFrameless() {
  return <div className="overflow-hidden rounded-2xl ring-1 ring-ink/8"><Shot name="waiter-call" caption="Staff notification when a guest taps Call a waiter" /></div>;
}
