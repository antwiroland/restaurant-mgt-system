"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { Modal } from "@/components/Modal";
import { useStaffSession } from "@/components/SessionProvider";
import { createBranch, getBranches, updateBranch, type BranchRecord } from "@/lib/apiClient";

export default function BranchesPage() {
  const { session, loading, authenticatedFetch } = useStaffSession();
  const [branches, setBranches] = useState<BranchRecord[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ALL");
  const [form, setForm] = useState({ code: "", name: "", active: true });
  const [editingBranch, setEditingBranch] = useState<BranchRecord | null>(null);
  const [editForm, setEditForm] = useState({ code: "", name: "", active: true });
  const [editingBusy, setEditingBusy] = useState(false);
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

  async function saveEdit() {
    if (!session || !editingBranch) return;
    setEditingBusy(true);
    try {
      await authenticatedFetch((activeSession) => updateBranch(activeSession, editingBranch.id, {
        code: editForm.code,
        name: editForm.name,
        active: editForm.active,
      }));
      await loadBranches();
      setMessage("Branch updated.");
      setEditingBranch(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update branch");
    } finally {
      setEditingBusy(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel">Loading branches...</section></main>;
  if (!session) return <main className="shell"><section className="panel"><p className="kicker">Branches</p><h1 className="text-2xl font-semibold">Staff access required</h1><Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link></section></main>;

  return (
    <main className="shell grid gap-4">
      <section className="panel">
        <p className="kicker">Branches</p>
        <h1 className="text-2xl font-semibold">Branch Management</h1>
        <div className="mt-3 grid gap-2 md:grid-cols-2">
          <input className="input text-sm" placeholder="Search code or branch name" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="input text-sm" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as "ALL" | "ACTIVE" | "INACTIVE")}>
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active only</option>
            <option value="INACTIVE">Inactive only</option>
          </select>
        </div>
        {error ? <p className="mt-3 alert alert-danger">{error}</p> : null}
        {message ? <p className="mt-3 alert alert-success">{message}</p> : null}
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="panel">
          <h2 className="text-lg font-semibold">Create Branch</h2>
          <form className="mt-3 grid gap-2" onSubmit={(event) => void handleCreate(event)}>
            <input className="input" placeholder="Code" value={form.code} onChange={(e) => setForm((s) => ({ ...s, code: e.target.value }))} required />
            <input className="input" placeholder="Name" value={form.name} onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))} required />
            <label className="flex items-center gap-2 text-sm text-ink-soft"><input type="checkbox" checked={form.active} onChange={(e) => setForm((s) => ({ ...s, active: e.target.checked }))} /> Active</label>
            <button className="btn btn-primary">Create</button>
          </form>
        </article>

        <article className="panel">
          <h2 className="text-lg font-semibold">Existing Branches</h2>
          <ul className="mt-3 grid gap-2">
            {filteredBranches.map((branch) => (
              <li key={branch.id} className="input flex items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{branch.name}</p>
                  <p className="text-xs text-ink-soft">{branch.code} · {branch.active ? "active" : "inactive"}</p>
                </div>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => {
                    setEditingBranch(branch);
                    setEditForm({ code: branch.code, name: branch.name, active: branch.active });
                  }}
                >
                  Edit
                </button>
              </li>
            ))}
            {filteredBranches.length === 0 ? <li className="rounded-xl border border-dashed border-line p-3 text-sm text-ink-soft">No branches match the current filters.</li> : null}
          </ul>
        </article>
      </section>

      <Modal
        open={!!editingBranch}
        title={editingBranch ? `Edit ${editingBranch.name}` : "Edit Branch"}
        onClose={() => setEditingBranch(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setEditingBranch(null)} disabled={editingBusy}>
              Cancel
            </button>
            <button
              className="btn btn-primary btn-sm"
              disabled={editingBusy || !editForm.code.trim() || !editForm.name.trim()}
              onClick={() => void saveEdit()}
            >
              {editingBusy ? "Saving..." : "Save"}
            </button>
          </>
        }
      >
        <div className="grid gap-3">
          <label className="field">
            <span className="field-label">Code</span>
            <input className="input" value={editForm.code} onChange={(e) => setEditForm((s) => ({ ...s, code: e.target.value }))} />
          </label>
          <label className="field">
            <span className="field-label">Name</span>
            <input className="input" value={editForm.name} onChange={(e) => setEditForm((s) => ({ ...s, name: e.target.value }))} />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={editForm.active} onChange={(e) => setEditForm((s) => ({ ...s, active: e.target.checked }))} />
            Active
          </label>
        </div>
      </Modal>
    </main>
  );
}


