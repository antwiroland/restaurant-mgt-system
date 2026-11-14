import { motion } from "framer-motion";
import { Banknote, CheckCheck, FileSearch, KeyRound, QrCode, ScanLine, ShieldCheck, Smartphone, Store, Vault } from "lucide-react";
import { BrowserFrame } from "../components/Media";
import { Badge, Point, Reveal, SectionHeader } from "../components/ui";

const flow = [
  { icon: Smartphone, title: "Bill settled", text: "Mobile Money or card payment confirmed through Paystack, or cash confirmed by the cashier at the counter.", status: "live" },
  { icon: QrCode, title: "Temporary payment QR", text: "A one-time QR code proving the bill is paid, separate from the table's permanent QR code.", status: "planned" },
  { icon: ScanLine, title: "Quick scan at the exit", text: "Security staff scan it at the post. Quick and discreet: paying guests simply walk out.", status: "planned" },
  { icon: ShieldCheck, title: "Verified and logged", text: "The system confirms the payment status, and every verification scan is kept for audit.", status: "planned" },
] as const;

export default function PaymentSection() {
  return (
    <section id="payments" aria-labelledby="payments-title" className="py-24 sm:py-28">
      <div className="container-x">
        <div className="grid items-end gap-8 lg:grid-cols-2">
          <SectionHeader
            id="payments-title"
            eyebrow="Payments & accountability"
            title={<>Accountability Without Compromising <span className="text-terra">Hospitality.</span></>}
          />
          <Reveal delay={0.1}>
            <p className="text-lg leading-relaxed text-ink-soft lg:pb-2">
              Improve payment accountability while keeping the customer experience smooth and welcoming. Every payment, discount, void and refund is recorded,
              approved where it matters and traceable to a person.
            </p>
          </Reveal>
        </div>

        <div className="mt-14 grid gap-10 lg:grid-cols-2">
          <Reveal>
            <ul className="grid gap-6">
              <Point icon={Smartphone} title="Electronic payment confirmation">Mobile Money and card payments are processed through Paystack. A successful payment confirms the order automatically and updates the table.</Point>
              <Point icon={Banknote} title="Cash, confirmed by the cashier">Cash is recorded at the counter with the amount received and change due, and tied to the cashier's open drawer.</Point>
              <Point icon={CheckCheck} title="Customer bill settlement">Guests pay from the table QR page or at the counter, split bills equally, and an order that's already paid can't be charged twice.</Point>
              <Point icon={Store} title="Walk-in customers, handled normally">Guests who never scan a QR code are served on the POS, on the same table bill, kitchen tickets and receipts.</Point>
              <Point icon={KeyRound} title="Manager approval">Discounts, voids and refunds need a manager's PIN for that specific action, and every one is in the audit trail.</Point>
              <Point icon={Vault} title="Cash drawers and close of day">Cash shifts with opening float and counted cash, plus a daily reconciliation that is signed off per branch.</Point>
            </ul>
          </Reveal>
          <Reveal delay={0.1} className="grid content-start gap-5">
            <BrowserFrame name="payments" caption="Record payments by cash, Mobile Money or card." sizes="(min-width: 1024px) 36rem, 100vw" url="Payments" />
            <div className="grid grid-cols-2 gap-5">
              <BrowserFrame name="receipts" caption="Find any receipt in seconds." sizes="(min-width: 1024px) 18rem, 50vw" url="Receipts" />
              <BrowserFrame name="shifts" caption="Cash drawers with expected cash." sizes="(min-width: 1024px) 18rem, 50vw" url="Cash shifts" />
            </div>
          </Reveal>
        </div>

        {/* exit verification workflow */}
        <Reveal className="mt-20 overflow-hidden rounded-[2.5rem] bg-kenvo-ink p-6 text-white sm:p-10">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="font-brand text-xs font-bold tracking-[0.18em] text-kenvo-bright uppercase">Payment verification workflow</p>
              <h3 className="mt-2 font-display text-3xl font-semibold sm:text-4xl">Exit verification at the security post</h3>
            </div>
            <div className="flex flex-wrap gap-2 text-xs font-bold">
              <span className="flex items-center gap-1.5 rounded-full bg-leaf/25 px-3 py-1 text-[#9fe0b4]"><span className="size-2 rounded-full bg-[#5fd38a]" />Available today</span>
              <span className="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-white/80"><span className="size-2 rounded-full border border-white/70" />Planned, on the roadmap</span>
            </div>
          </div>

          <ol className="mt-10 grid gap-4 md:grid-cols-4">
            {flow.map((f, i) => (
              <motion.li
                key={f.title}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.12, duration: 0.5 }}
                className={`relative rounded-3xl p-5 ${f.status === "live" ? "bg-white/10 ring-1 ring-[#5fd38a]/50" : "border border-dashed border-white/25"}`}
              >
                <div className="flex items-center justify-between">
                  <span className={`flex size-11 items-center justify-center rounded-2xl ${f.status === "live" ? "bg-[#5fd38a]/20 text-[#9fe0b4]" : "bg-kenvo/20 text-kenvo-bright"}`}>
                    <f.icon className="size-5" aria-hidden />
                  </span>
                  <span className="font-display text-2xl text-white/25">{i + 1}</span>
                </div>
                <p className="mt-4 font-bold">{f.title}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-white/70">{f.text}</p>
                <p className="mt-3 text-[11px] font-bold tracking-wider uppercase">
                  {f.status === "live" ? <span className="text-[#9fe0b4]">Available today</span> : <span className="text-white/55">Planned</span>}
                </p>
              </motion.li>
            ))}
          </ol>

          <div className="mt-8 flex gap-3 rounded-2xl bg-white/5 p-4 text-sm leading-relaxed text-white/75">
            <FileSearch className="mt-0.5 size-5 shrink-0 text-kenvo-bright" aria-hidden />
            <p>
              <span className="font-semibold text-white">Conceptual workflow, not yet part of the current release.</span> Steps 2 to 4 are on the product roadmap. Today, staff can
              already confirm any bill's payment status in seconds from the receipt or order number, and managers can see every unpaid table on the payment watch.
              Walk-in guests are covered too, because their bills are created by staff on the POS.
            </p>
          </div>
        </Reveal>
        <div className="mt-6 flex justify-center"><Badge tone="kenvo">Backed by KENVO, specialists in CCTV, networking and electrical systems</Badge></div>
      </div>
    </section>
  );
}
