import { motion } from "framer-motion";
import { Blocks, Eye, HandHeart, QrCode, ShieldCheck, Sprout, UserCog } from "lucide-react";
import { Photo } from "../components/Media";
import { Reveal, SectionHeader } from "../components/ui";

const reasons = [
  { icon: Blocks, title: "Integrated restaurant operations", text: "Ordering, kitchen, tables, payments, stock and staff share one record, so nothing is retyped." },
  { icon: QrCode, title: "Modern customer ordering", text: "App-free table QR ordering, plus a customer app for pickup, delivery and loyalty." },
  { icon: ShieldCheck, title: "Better payment accountability", text: "Manager approvals, cash drawers, reconciliation sign-off and a complete audit trail." },
  { icon: Eye, title: "Centralised visibility", text: "Live dashboards and reports across every branch, in one place." },
  { icon: UserCog, title: "Workforce management", text: "Rotas, biometric attendance, leave, payroll, tips and recruitment, built in." },
  { icon: HandHeart, title: "Less admin work", text: "Taxes, offers, stock deductions, timesheets and payslips are calculated for you." },
  { icon: Sprout, title: "Ready for growth", text: "Add branches, staff and channels such as pickup and delivery on the same platform." },
];

const comparison = [
  ["A guest's order is re-entered for the kitchen", "The QR order goes straight to the kitchen display and the table's bill"],
  ["Payments are matched to orders by hand", "Each payment confirms its order and frees the table"],
  ["Attendance sheets are retyped into payroll", "Approved timesheets flow straight into payroll runs"],
  ["Each branch reports its own way", "Every branch reports the same way, side by side"],
];

export default function WhyChoose() {
  return (
    <section aria-labelledby="why-title" className="py-24 sm:py-28">
      <div className="container-x">
        <div className="grid gap-12 lg:grid-cols-[1fr_1fr] lg:items-center">
          <SectionHeader
            id="why-title"
            eyebrow="Why choose our platform"
            title={<>More Than Software. <span className="text-terra">A Better Way to Run Your Restaurant.</span></>}
            lead="Many restaurants run a POS from one vendor, take bookings in another app, track attendance on paper and calculate payroll in a spreadsheet. This platform brings it all together."
          />
          <Reveal delay={0.1} className="relative aspect-[16/11] overflow-hidden rounded-[2.5rem] shadow-lift">
            <Photo name="plates-spread" sizes="(min-width: 1024px) 36rem, 100vw" className="size-full" />
          </Reveal>
        </div>

        <ul className="mt-16 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {reasons.map((r, i) => (
            <motion.li
              key={r.title}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: (i % 4) * 0.07 }}
              whileHover={{ y: -4 }}
              className={`rounded-3xl p-6 ring-1 ${i === 0 ? "bg-ink text-cream ring-ink lg:row-span-2" : "bg-white ring-ink/6 shadow-soft"}`}
            >
              <r.icon className={`size-7 ${i === 0 ? "text-gold" : "text-terra"}`} aria-hidden />
              <p className={`mt-4 font-bold ${i === 0 ? "text-xl text-cream" : "text-ink"}`}>{r.title}</p>
              <p className={`mt-2 text-[15px] leading-relaxed ${i === 0 ? "text-cream/75" : "text-ink-soft"}`}>{r.text}</p>
              {i === 0 && (
                <dl className="mt-6 grid gap-4 border-t border-cream/15 pt-6 text-sm">
                  {comparison.map(([before, after]) => (
                    <div key={before}>
                      <dt className="text-cream/50 line-through decoration-terra/70">{before}</dt>
                      <dd className="mt-1 font-semibold text-cream">{after}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </motion.li>
          ))}
        </ul>
      </div>
    </section>
  );
}
