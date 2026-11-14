import { motion } from "framer-motion";
import { Photo } from "../components/Media";
import { SectionHeader } from "../components/ui";
import type { FoodKey } from "../data/images";

const types: { name: string; photo: FoodKey; text: string }[] = [
  { name: "Fine Dining Restaurants", photo: "served-plate", text: "Table service with reservations, allergy notes on every ticket, discreet bill requests and itemised bills with every tax line." },
  { name: "Fast Food Restaurants", photo: "burger-fries", text: "Fast POS ordering, pickups paid before cooking and handed over with a code, a public pickup board and a busy kitchen display." },
  { name: "Cafés", photo: "cafe", text: "Quick counter service, table QR ordering for seated guests, loyalty points and simple cash drawer control." },
  { name: "Hotels", photo: "hotel-terrace", text: "App-free table ordering for guests, reservations, card and Mobile Money payments, and full HR and payroll for the team." },
  { name: "Casual Dining", photo: "table-overhead", text: "Live floor plans, running bills, split payments and waiter requests built around the shared table." },
  { name: "Restaurant Chains", photo: "interior-hall", text: "Branch-level stock, cash, staff and feedback, with group-wide dashboards and reports for the owners." },
];

export default function WhoWeServe() {
  return (
    <section id="solutions" aria-labelledby="solutions-title" className="bg-cream-2/60 py-24 sm:py-28">
      <div className="container-x">
        <SectionHeader
          id="solutions-title"
          align="center"
          eyebrow="Who we serve"
          title={<>Built for the Way <span className="text-terra">You Serve.</span></>}
          lead="From a single café to a group of restaurants, the platform adapts to how your guests order, eat and pay."
        />
        <ul className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {types.map((t, i) => (
            <motion.li
              key={t.name}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-60px" }}
              transition={{ duration: 0.55, delay: (i % 3) * 0.08 }}
              className="group relative isolate flex min-h-[26rem] flex-col justify-end overflow-hidden rounded-[2rem] p-6 shadow-soft"
            >
              <Photo name={t.photo} sizes="(min-width: 1024px) 25rem, (min-width: 640px) 50vw, 100vw" className="absolute inset-0 -z-20 size-full transition-transform duration-700 group-hover:scale-105" />
              <div aria-hidden className="absolute inset-0 -z-10 bg-gradient-to-t from-ink via-ink/60 to-ink/0" />
              <h3 className="font-display text-2xl font-semibold text-white">{t.name}</h3>
              <p className="mt-2 text-[15px] leading-relaxed text-white/80">{t.text}</p>
            </motion.li>
          ))}
        </ul>
      </div>
    </section>
  );
}
