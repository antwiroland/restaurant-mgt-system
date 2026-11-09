import type { Metadata } from "next";
import { ReserveClient } from "./ReserveClient";

export const metadata: Metadata = {
  title: "Reservations",
  description: "Book a table or manage an existing reservation.",
};

export default function ReservePage() {
  return (
    <div className="site pb-24 pt-10 md:pt-14">
      <header className="max-w-2xl">
        <p className="eyebrow">Reservations</p>
        <h1 className="heading-serif mt-3 text-5xl md:text-6xl">Book your table</h1>
        <p className="mt-4 text-lg text-ink-soft">Choose a date, time and table. Bookings are held for 90 minutes.</p>
      </header>
      <div className="mt-10">
        <ReserveClient />
      </div>
    </div>
  );
}
