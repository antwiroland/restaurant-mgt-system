import Link from "next/link";

export default function Home() {
  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Restaurant Manager</p>
        <h1 className="text-4xl font-semibold mt-2">Staff Console</h1>
        <p className="mt-3 text-[#35523d]">Role-aware staff console with POS, KDS, shifts, branches, users, and audit views.</p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link href="/login" className="rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link>
          <Link href="/pos" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Go to POS</Link>
          <Link href="/kds" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Open KDS</Link>
          <Link href="/group-ordering" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Group Ordering</Link>
          <Link href="/shifts" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Manage Shifts</Link>
          <Link href="/branches" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Manage Branches</Link>
          <Link href="/users" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Manage Users</Link>
          <Link href="/audit" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Audit Log</Link>
          <Link href="/reservations" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Reservations</Link>
          <Link href="/payments" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Payments</Link>
          <Link href="/financial" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Financial</Link>
          <Link href="/receipts" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Receipts</Link>
        </div>
      </section>
    </main>
  );
}
