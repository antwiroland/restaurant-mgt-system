"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useStaffSession } from "@/components/SessionProvider";
import { createBranch, getBranches, updateBranch, type BranchRecord } from "@/lib/apiClient";

export default function BranchesPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [form, setForm] = useState({ code: "", name: "", active: true });
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const filteredBranches = branches.filter((branch) => {
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || branch.name.toLowerCase().includes(normalizedQuery)
      || branch.code.toLowerCase().includes(normalizedQuery);
    const matchesStatus = statusFilter === "ALL"
      || (statusFilter === "ACTIVE" && branch.active)
      || (statusFilter === "INACTIVE" && !branch.active);
    return matchesQuery && matchesStatus;
  });

  async function loadBranches() {
    if (!session) return;
    const next = await authenticatedFetch(getBranches);
    setBranches(next);
  }

  useEffect(() => {
    loadBranches().catch((err) => setError(err instanceof Error ? err.message : "Could not load branches"));
  }, [session]);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    try {
      setError("");
      await authenticatedFetch((activeSession) => createBranch(activeSession, form));
      setForm({ code: "", name: "", active: true });
      await loadBranches();
      setMessage("Branch created.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create branch");
    }
  }

  async function quickEdit(branch: BranchRecord) {
    if (!session) return;
    const code = prompt("Code", branch.code);
    if (!code) return;
    const name = prompt("Name", branch.name);
    if (!name) return;
    const active = confirm("Set active? OK = active, Cancel = inactive");
    try {
      await authenticatedFetch((activeSession) => updateBranch(activeSession, branch.id, { code, name, active }));
      await loadBranches();
      setMessage("Branch updated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update branch");
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading branches...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Branches</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex rounded-full bg-[#132018] px-4 py-2 text-white">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Branches</p>
        <h1 className="text-2xl font-semibold">Branch Management</h1>
        <div className="mt-3 grid gap-2 md:grid-cols-2">
          <input className="rounded-xl border border-[#cfe0c8] p-2 text-sm" placeholder="Search code or branch name" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="rounded-xl border border-[#cfe0c8] p-2 text-sm" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as "ALL" | "ACTIVE" | "INACTIVE")}>
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active only</option>
            <option value="INACTIVE">Inactive only</option>
          </select>
        </div>
        {error ? <p className="mt-3 rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
        {message ? <p className="mt-3 rounded-xl bg-[#dcfce7] px-4 py-3 text-sm text-[#166534]">{message}</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Create Branch</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void handleCreate(event)}>
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Code" value={form.code} onChange={(e) => setForm((s) => ({ ...s, code: e.target.value }))} required />
            <input className="rounded-xl border border-[#cfe0c8] p-3" placeholder="Name" value={form.name} onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))} required />
            <label className="flex items-center gap-2 text-sm text-[#35523d]"><input type="checkbox" checked={form.active} onChange={(e) => setForm((s) => ({ ...s, active: e.target.checked }))} /> Active</label>
            <button className="rounded-full bg-[#132018] px-4 py-2 text-white">Create</button>
          </form>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Existing Branches</h2>
          <ul className="mt-3 grid gap-2">
            {filteredBranches.map((branch) => (
              <li key={branch.id} className="rounded-xl border border-[#cfe0c8] p-3 flex items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{branch.name}</p>
                  <p className="text-xs text-[#35523d]">{branch.code} · {branch.active ? "active" : "inactive"}</p>
                </div>
                <button className="rounded-full border border-[#132018] px-3 py-1 text-xs" onClick={() => void quickEdit(branch)}>Edit</button>
              </li>
            ))}
            {filteredBranches.length === 0 ? <li className="rounded-xl border border-dashed border-[#cfe0c8] p-3 text-sm text-[#35523d]">No branches match the current filters.</li> : null}
          </ul>
        </article>
      </section>
    </main>
  );
}
