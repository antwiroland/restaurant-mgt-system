import Link from "next/link";

export default function Home() {
  return (
    <main className="shell">
      <section className="panel">
        <p className="kicker">Restaurant Manager</p>
        <h1 className="text-4xl font-semibold mt-2">Staff Console</h1>
        <p className="mt-3 text-[#35523d]">Phase 6 foundation with role-aware login, POS, tables, and active orders.</p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link href="/login" className="rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link>
          <Link href="/pos" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Go to POS</Link>
          <Link href="/kds" className="rounded-full border border-[#132018] px-4 py-2 text-[#132018]">Open KDS</Link>
        </div>
      </section>
    </main>
  );
}
