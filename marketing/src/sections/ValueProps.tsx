import { BarChart3, CalendarClock, ClipboardList, LayoutGrid, MonitorSmartphone, QrCode, Users, Wallet, WalletCards } from "lucide-react";
import FeatureCard from "../components/FeatureCard";
import { SectionHeader } from "../components/ui";

const features = [
  { icon: QrCode, title: "Customer Ordering", href: "#how-it-works", text: "Guests scan the table QR code and order from their phone, with no app or account. Or they order pickup and delivery from the customer app." },
  { icon: MonitorSmartphone, title: "Restaurant POS", href: "#pos", text: "A fast point of sale for dine-in, pickup and delivery, with offers, service charge and taxes worked out for you." },
  { icon: LayoutGrid, title: "Table Management", href: "#tables", text: "A live floor plan of free, occupied and reserved tables, with each table's running bill a tap away." },
  { icon: ClipboardList, title: "Order Management", href: "#team", text: "Every order flows from the table or counter to the kitchen display and back, with live status for everyone." },
  { icon: Wallet, title: "Payment Management", href: "#payments", text: "Mobile Money, card and cash, split bills, receipts, cash drawers and manager-approved refunds, all on record." },
  { icon: Users, title: "Staff Management", href: "#team", text: "Role-based access for managers, cashiers, kitchen, riders and HR, plus rotas, leave and employee records." },
  { icon: CalendarClock, title: "Attendance Tracking", href: "#attendance", text: "Clock-ins from the POS or Hikvision biometric terminals, with late arrivals and missed clock-outs flagged live." },
  { icon: WalletCards, title: "Payroll Management", href: "#payroll", text: "Payslips built from approved hours, leave and your deduction rules, approved by a second person." },
  { icon: BarChart3, title: "Business Reporting", href: "#reporting", text: "Dashboards and exportable reports on sales, best sellers, cash, ratings, food cost and labour." },
];

export default function ValueProps() {
  return (
    <section id="features" aria-labelledby="features-title" className="py-24 sm:py-28">
      <div className="container-x">
        <SectionHeader
          id="features-title"
          align="center"
          eyebrow="One connected platform"
          title={<>Everything Your Restaurant Needs. <span className="text-terra">All in One Place.</span></>}
          lead="The platform connects what your guests see with how your restaurant runs: the order a guest places at the table reaches the kitchen, the bill, the cash drawer and the owner's dashboard without anyone retyping it."
        />
        <ul className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f, i) => (
            <FeatureCard key={f.title} icon={f.icon} title={f.title} index={i} href={f.href}>{f.text}</FeatureCard>
          ))}
        </ul>
      </div>
    </section>
  );
}
