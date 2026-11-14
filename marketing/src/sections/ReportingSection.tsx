import { Building2, Coins, FileSpreadsheet, Gauge, Star, Users } from "lucide-react";
import { BrowserFrame } from "../components/Media";
import { Reveal, SectionHeader } from "../components/ui";

const items = [
  { icon: Gauge, title: "Sales visibility", text: "Revenue trend, paid orders, average order value, best sellers and busiest hours." },
  { icon: FileSpreadsheet, title: "Transaction records", text: "Sales, daily reconciliation, audit trail, timesheets and walkouts as CSV or PDF." },
  { icon: Coins, title: "Operational monitoring", text: "Cash shift variances, low-stock alerts and a food cost report with margin per dish." },
  { icon: Users, title: "Staff management", text: "HR dashboard and reports: headcount, attendance, leave, hours and payroll summaries." },
  { icon: Star, title: "Restaurant performance", text: "Guest ratings by dish, staff member and rider, with live alerts for low ratings." },
  { icon: Building2, title: "Multi-branch view", text: "Every branch keeps its own tables, stock, cash and staff, and reports filter by branch." },
];

export default function ReportingSection() {
  return (
    <section id="reporting" aria-labelledby="reporting-title" className="py-24 sm:py-28">
      <div className="container-x">
        <SectionHeader
          id="reporting-title"
          eyebrow="Business reporting"
          title={<>Make Better Decisions With <span className="text-terra">Better Visibility.</span></>}
          lead="Every order, payment, shift and rating becomes information you can act on, today and not at the end of the month."
        />
        <div className="mt-12 grid gap-10 lg:grid-cols-[1.25fr_0.75fr]">
          <Reveal>
            <BrowserFrame name="dashboard" caption="The owner's dashboard: revenue, paid orders, average order value, best sellers, payment methods, order channels and busiest hours." sizes="(min-width: 1024px) 46rem, 100vw" url="Owner dashboard" />
          </Reveal>
          <Reveal delay={0.1}>
            <ul className="grid gap-6">
              {items.map((it) => (
                <li key={it.title} className="flex gap-4">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-leaf-soft text-leaf"><it.icon className="size-5" aria-hidden /></span>
                  <div><p className="font-bold text-ink">{it.title}</p><p className="mt-0.5 text-[15px] leading-relaxed text-ink-soft">{it.text}</p></div>
                </li>
              ))}
            </ul>
          </Reveal>
        </div>
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          <Reveal><BrowserFrame name="reports" caption="Report exports as CSV or PDF." sizes="(min-width: 768px) 33vw, 100vw" url="Reports" /></Reveal>
          <Reveal delay={0.08}><BrowserFrame name="feedback" caption="Guest feedback and ratings." sizes="(min-width: 768px) 33vw, 100vw" url="Feedback" /></Reveal>
          <Reveal delay={0.16}><BrowserFrame name="hr" caption="HR dashboard: headcount, today's attendance, approvals." sizes="(min-width: 768px) 33vw, 100vw" url="HR dashboard" /></Reveal>
        </div>
      </div>
    </section>
  );
}
