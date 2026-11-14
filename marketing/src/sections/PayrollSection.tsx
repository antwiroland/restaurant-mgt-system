import { motion } from "framer-motion";
import { ArrowRight, CalendarCheck2, Clock3, FileCheck2, HandCoins, Scale, UsersRound } from "lucide-react";
import { BrowserFrame } from "../components/Media";
import { Reveal, SectionHeader } from "../components/ui";

const pipeline = [
  { icon: Clock3, title: "Attendance", text: "Clock-ins and breaks" },
  { icon: CalendarCheck2, title: "Approved timesheets", text: "Worked hours and approved overtime" },
  { icon: UsersRound, title: "Approved leave", text: "Paid and unpaid leave" },
  { icon: Scale, title: "Your rules", text: "Contracts, tax and deductions" },
  { icon: FileCheck2, title: "Payroll run", text: "Checked by a second person" },
];

const facts = [
  ["Salary configuration", "Contracts can be monthly, weekly, daily or hourly, each with its own rate."],
  ["Working hours & breaks", "Attendance policies, break rules and overtime settings are configurable."],
  ["Hourly calculations", "Hourly staff are paid from approved hours, with approved overtime at its own rate."],
  ["Approved leave", "Leave types, balances and approval chains. Approved leave clears the rota and flows into pay."],
  ["Monthly payroll", "Runs move from draft to calculated, approved, finalised and paid. Finalised payslips never change."],
  ["Statutory deductions", "Income tax bands and social security as dated rules, with a Ghana preset for SSNIT and PAYE."],
];

export default function PayrollSection() {
  return (
    <section id="payroll" aria-labelledby="payroll-title" className="bg-cream-2/60 py-24 sm:py-28">
      <div className="container-x">
        <SectionHeader
          id="payroll-title"
          align="center"
          eyebrow="Payroll management"
          title={<>From Attendance to Payroll. <span className="text-terra">Simplified.</span></>}
          lead="When clock-ins, timesheets, leave and payroll live in one system, hours are counted once and paid correctly, and month-end payroll takes hours instead of days."
        />

        <ol className="mt-14 grid gap-3 md:grid-cols-5" aria-label="How pay is calculated">
          {pipeline.map((p, i) => (
            <motion.li
              key={p.title}
              initial={{ opacity: 0, y: 14 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1, duration: 0.45 }}
              className="relative flex items-center gap-3 rounded-2xl bg-white p-4 shadow-soft ring-1 ring-ink/6 md:flex-col md:items-start"
            >
              <span className={`flex size-10 items-center justify-center rounded-xl ${i === pipeline.length - 1 ? "bg-terra text-white" : "bg-terra-soft text-terra"}`}><p.icon className="size-5" aria-hidden /></span>
              <span>
                <span className="block text-sm font-bold text-ink">{p.title}</span>
                <span className="block text-[13px] text-ink-muted">{p.text}</span>
              </span>
              {i < pipeline.length - 1 && <ArrowRight className="absolute top-1/2 -right-3 z-10 hidden size-5 -translate-y-1/2 rounded-full bg-cream p-0.5 text-terra md:block" aria-hidden />}
            </motion.li>
          ))}
        </ol>

        <div className="mt-14 grid gap-10 lg:grid-cols-[1.15fr_0.85fr] lg:items-start">
          <Reveal className="grid gap-5">
            <BrowserFrame name="payroll" caption="Payroll runs with staff count, gross, net and status." sizes="(min-width: 1024px) 42rem, 100vw" url="HR · Payroll" />
            <div className="grid gap-5 sm:grid-cols-2">
              <BrowserFrame name="deductions" caption="Deduction rules such as SSNIT and PAYE bands." sizes="(min-width: 640px) 21rem, 100vw" url="Deductions" />
              <BrowserFrame name="tips" caption="Tip and service charge pools, paid through payroll." sizes="(min-width: 640px) 21rem, 100vw" url="Tips & service charge" />
            </div>
          </Reveal>
          <Reveal delay={0.1}>
            <dl className="grid gap-5">
              {facts.map(([t, d]) => (
                <div key={t} className="border-b border-line pb-5 last:border-0">
                  <dt className="font-bold text-ink">{t}</dt>
                  <dd className="mt-1 text-[15px] leading-relaxed text-ink-soft">{d}</dd>
                </div>
              ))}
            </dl>
            <div className="mt-4 flex gap-3 rounded-2xl bg-white p-4 text-sm text-ink-soft shadow-soft ring-1 ring-ink/6">
              <HandCoins className="size-5 shrink-0 text-leaf" aria-hidden />
              Staff see their own payslips as PDFs. Bank and Mobile Money payment files can be downloaded for the payout.
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
