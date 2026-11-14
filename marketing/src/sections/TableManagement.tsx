import { CalendarCheck, CircleDollarSign, DoorOpen, LayoutGrid, ReceiptText, UsersRound } from "lucide-react";
import { BrowserFrame, Photo } from "../components/Media";
import { Point, Reveal, SectionHeader } from "../components/ui";

export default function TableManagement() {
  return (
    <section id="tables" aria-labelledby="tables-title" className="py-24 sm:py-28">
      <div className="container-x grid items-center gap-14 lg:grid-cols-[0.9fr_1.1fr]">
        <div>
          <SectionHeader
            id="tables-title"
            eyebrow="Table management"
            title={<>Every Table. Every Order. <span className="text-terra">Under Control.</span></>}
            lead="See your whole dining room at a glance: which tables are free, occupied or reserved, what each one has ordered and what is still owed."
          />
          <Reveal delay={0.1}>
            <ul className="mt-9 grid gap-6">
              <Point icon={LayoutGrid} title="Available and occupied, live">Tables are grouped by zone with status counts. Placing a dine-in order marks the table occupied.</Point>
              <Point icon={UsersRound} title="Dining sessions and orders">Several rounds of orders build up on one table bill, and each new party starts with a clean page.</Point>
              <Point icon={CircleDollarSign} title="Outstanding bills">Managers can see what each table owes, when it was served and whether the bill was requested.</Point>
              <Point icon={DoorOpen} title="Table closure">When the bill is settled, the table is released automatically for the next guests.</Point>
              <Point icon={CalendarCheck} title="Reservations without clashes">Bookings are checked for overlaps, and the table is held for the party an hour before they arrive.</Point>
              <Point icon={ReceiptText} title="Walk-in guests, served as usual">Guests who don't scan a QR code are served through the POS the same way, on the same table bill.</Point>
            </ul>
          </Reveal>
        </div>

        <Reveal delay={0.15} className="relative">
          <div aria-hidden className="absolute -top-8 -right-6 hidden h-56 w-44 overflow-hidden rounded-[2rem] shadow-lift lg:block">
            <Photo name="interior-modern" sizes="11rem" className="size-full" />
          </div>
          <BrowserFrame name="floor" caption="Floor & tables: status counts, seats and running totals per table." sizes="(min-width: 1024px) 40rem, 100vw" className="relative" url="Floor & tables" />
          <div className="mt-6 grid grid-cols-2 gap-4">
            <BrowserFrame name="reservations" caption="Reservations with clash checks." sizes="(min-width: 1024px) 20rem, 50vw" url="Reservations" />
            <BrowserFrame name="qr-split" caption="Guests split the bill equally." sizes="(min-width: 1024px) 20rem, 50vw" url="Table QR · split bill" />
          </div>
        </Reveal>
      </div>
    </section>
  );
}
