import Link from "next/link";

export default function Home() {
  return (
    <main className="shell">
      <section className="panel grid gap-4">
        <p className="kicker">Restaurant Manager</p>
        <h1 className="heading-lg">Staff Console</h1>
        <p className="body-sm">Role-aware staff console with POS, KDS, shifts, branches, users, and audit views.</p>
        <div className="flex flex-wrap gap-3">
          <Link href="/login" className="btn btn-primary">Open Login</Link>
          <Link href="/pos" className="btn btn-secondary">Go to POS</Link>
          <Link href="/kds" className="btn btn-secondary">Open KDS</Link>
          <Link href="/group-ordering" className="btn btn-secondary">Group Ordering</Link>
          <Link href="/shifts" className="btn btn-secondary">Manage Shifts</Link>
          <Link href="/branches" className="btn btn-secondary">Manage Branches</Link>
          <Link href="/users" className="btn btn-secondary">Manage Users</Link>
          <Link href="/audit" className="btn btn-secondary">Audit Log</Link>
          <Link href="/reservations" className="btn btn-secondary">Reservations</Link>
          <Link href="/payments" className="btn btn-secondary">Payments</Link>
          <Link href="/financial" className="btn btn-secondary">Financial</Link>
          <Link href="/receipts" className="btn btn-secondary">Receipts</Link>
        </div>
      </section>
    </main>
  );
}
