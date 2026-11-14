import { motion } from "framer-motion";
import { BadgeCheck, Bike, ChefHat, ConciergeBell, Landmark, ShieldCheck, Wallet } from "lucide-react";
import { useState } from "react";
import { BrowserFrame, Photo, PhoneFrame } from "../components/Media";
import { SectionHeader } from "../components/ui";
import type { ScreenKey } from "../data/images";

const roles: { icon: React.ComponentType<{ className?: string }>; role: string; text: string; screen: ScreenKey; phone?: boolean; caption: string }[] = [
  { icon: ConciergeBell, role: "Waiters & front of house", text: "Take orders on the POS, see the live floor plan, and get \"Call a waiter\" and \"Ask for the bill\" requests the moment guests tap them.", screen: "floor", caption: "The live floor plan front-of-house staff work from." },
  { icon: ChefHat, role: "Kitchen staff", text: "Every order lands on the kitchen display with options and notes. Cooks move tickets forward, mark single items ready and print tickets.", screen: "kds", caption: "The kitchen display, with timers and late tickets highlighted." },
  { icon: Wallet, role: "Cashiers", text: "Take payment in cash, by card or Mobile Money, split bills, hand over pickups with the customer's code and run their own cash drawer.", screen: "payments", caption: "Payments at the counter, with change worked out." },
  { icon: Bike, role: "Riders", text: "Assigned deliveries with address, total and navigation, with location shared with the customer until it's marked delivered.", screen: "rider-delivery", phone: true, caption: "The rider's delivery screen." },
  { icon: BadgeCheck, role: "Managers", text: "Approve discounts, voids and refunds, dispatch riders, manage stock, reply to feedback and watch service live.", screen: "delivery", caption: "Delivery dispatch: assign riders and manage zones." },
  { icon: ShieldCheck, role: "Administrators & HR", text: "Set up branches, billing and taxes, menus and staff roles. HR officers manage people, attendance, leave and payroll.", screen: "hr", caption: "The HR dashboard." },
];

export default function StaffManagement() {
  const [active, setActive] = useState(0);
  const r = roles[active];
  return (
    <section id="team" aria-labelledby="team-title" className="relative overflow-hidden bg-ink py-24 text-cream sm:py-28">
      <div aria-hidden className="absolute inset-0 opacity-[0.12]">
        <Photo name="chef-flame" sizes="100vw" className="size-full" alt="" />
      </div>
      <div aria-hidden className="absolute inset-0 bg-gradient-to-b from-ink via-ink/95 to-ink" />
      <div className="container-x relative">
        <SectionHeader
          id="team-title"
          dark
          eyebrow="Restaurant team"
          title={<>Your Team. Your Operations. <span className="text-gold">One Connected Platform.</span></>}
          lead="Orders, requests and status changes reach the right screen the moment they happen. Each person sees what their role needs, and nothing it shouldn't."
        />

        <div className="mt-14 grid gap-10 lg:grid-cols-[0.85fr_1.15fr] lg:items-center">
          <ul className="grid gap-2" role="list">
            {roles.map((x, i) => (
              <li key={x.role}>
                <button
                  type="button"
                  onClick={() => setActive(i)}
                  aria-pressed={active === i}
                  className={`flex w-full gap-4 rounded-2xl p-4 text-left transition-colors ${active === i ? "bg-cream/10 ring-1 ring-cream/15" : "hover:bg-cream/5"}`}
                >
                  <span className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${active === i ? "bg-terra text-white" : "bg-cream/10 text-gold"}`}>
                    <x.icon className="size-5" aria-hidden />
                  </span>
                  <span>
                    <span className="block font-bold text-cream">{x.role}</span>
                    <span className={`block text-[15px] leading-relaxed text-cream/70 ${active === i ? "" : "line-clamp-1"}`}>{x.text}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <motion.div key={r.screen} initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.4 }} className="flex justify-center">
            {r.phone ? (
              <PhoneFrame name={r.screen} caption={r.caption} width="w-60" className="[&_figcaption]:text-cream/60" />
            ) : (
              <BrowserFrame name={r.screen} caption={r.caption} sizes="(min-width: 1024px) 40rem, 100vw" url={r.role} className="w-full [&_figcaption]:text-cream/60" />
            )}
          </motion.div>
        </div>

        <div className="mt-14 flex flex-wrap items-center gap-3 rounded-2xl bg-cream/5 p-5 text-sm text-cream/75 ring-1 ring-cream/10">
          <Landmark className="size-5 text-gold" aria-hidden />
          Six built-in roles: administrator, manager, cashier, rider, HR officer and customer. Access is enforced by the server, not just hidden in the interface.
        </div>
      </div>
    </section>
  );
}
