"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Detail, Field, errorMessage, formatBytes, formatDate } from "@/features/hr/ui";
import { saveDownloadedFile } from "@/lib/apiClient";
import { formatMoney } from "@/lib/brand";
import {
  downloadMyDocument,
  getMyContracts,
  getMyDocuments,
  updateMyProfile,
  type Contract,
  type EmployeeDetail,
  type EmployeeDocument,
} from "@/lib/hrApi";

type ContactRow = { name: string; relationship: string; phone: string; email: string };

const toRows = (employee: EmployeeDetail): ContactRow[] =>
  employee.emergencyContacts.map((c) => ({ name: c.name, relationship: c.relationship ?? "", phone: c.phone, email: c.email ?? "" }));

/** Self-service: the employee keeps their own contact details and emergency contacts current. */
export function MyContactDetails({ employee, onSaved }: { employee: EmployeeDetail; onSaved: (updated: EmployeeDetail) => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [editing, setEditing] = useState(false);
  const [phone, setPhone] = useState(employee.summary.phone ?? "");
  const [email, setEmail] = useState(employee.summary.email ?? "");
  const [address, setAddress] = useState(employee.address ?? "");
  const [contacts, setContacts] = useState<ContactRow[]>(toRows(employee));
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  function reset() {
    setPhone(employee.summary.phone ?? "");
    setEmail(employee.summary.email ?? "");
    setAddress(employee.address ?? "");
    setContacts(toRows(employee));
    setError("");
  }

  function setContact(index: number, patch: Partial<ContactRow>) {
    setContacts((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const updated = await authenticatedFetch((s) => updateMyProfile(s, {
        phone: phone.trim() || null,
        email: email.trim() || null,
        address: address.trim() || null,
        emergencyContacts: contacts.filter((c) => c.name.trim() || c.phone.trim()).map((c) => ({
          name: c.name.trim(), relationship: c.relationship.trim() || null, phone: c.phone.trim(), email: c.email.trim() || null,
        })),
        version: employee.version,
      }));
      onSaved(updated);
      setEditing(false);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err, "Could not save your details"));
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <section className="panel grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 className="text-sm font-semibold">My contact details</h2>
            <p className="text-xs text-ink-muted">You can update these yourself. Your job, branch and pay are managed by HR.</p>
          </div>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => { reset(); setSaved(false); setEditing(true); }}>
            <Icon name="edit" size={16} /> Update my details
          </button>
        </div>
        {saved ? <p className="alert alert-success text-sm" role="status">Your details were saved.</p> : null}
        <dl className="grid gap-4 sm:grid-cols-3">
          <Detail label="Phone" value={employee.summary.phone} />
          <Detail label="Email" value={employee.summary.email} />
          <Detail label="Address" value={employee.address} />
        </dl>
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Emergency contacts</p>
          {employee.emergencyContacts.length === 0 ? <p className="mt-1 text-sm text-ink-muted">None yet. Please add someone we can call in an emergency.</p> : (
            <ul className="mt-1 grid gap-1 text-sm">
              {employee.emergencyContacts.map((c) => (
                <li key={`${c.name}-${c.phone}`}><span className="font-semibold">{c.name}</span>{c.relationship ? ` (${c.relationship})` : ""} · {c.phone}{c.email ? ` · ${c.email}` : ""}</li>
              ))}
            </ul>
          )}
        </div>
      </section>
    );
  }

  return (
    <form className="panel grid gap-3" onSubmit={submit} aria-label="Update my contact details">
      <h2 className="text-sm font-semibold">Update my contact details</h2>
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Phone"><input className="input" type="tel" value={phone} maxLength={20} onChange={(e) => setPhone(e.target.value)} /></Field>
        <Field label="Email"><input className="input" type="email" value={email} maxLength={255} onChange={(e) => setEmail(e.target.value)} /></Field>
      </div>
      <Field label="Address"><textarea className="input min-h-16" value={address} maxLength={500} onChange={(e) => setAddress(e.target.value)} /></Field>
      <fieldset className="grid gap-2">
        <legend className="text-sm font-semibold">Emergency contacts</legend>
        {contacts.map((c, i) => (
          <div key={i} className="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-[1fr_1fr_1fr_auto]">
            <Field label="Name"><input className="input" value={c.name} maxLength={120} required onChange={(e) => setContact(i, { name: e.target.value })} /></Field>
            <Field label="Relationship"><input className="input" value={c.relationship} maxLength={60} onChange={(e) => setContact(i, { relationship: e.target.value })} /></Field>
            <Field label="Phone"><input className="input" type="tel" value={c.phone} maxLength={20} required onChange={(e) => setContact(i, { phone: e.target.value })} /></Field>
            <button type="button" className="btn btn-ghost btn-sm self-end" aria-label={`Remove ${c.name || "this contact"}`}
              onClick={() => setContacts((rows) => rows.filter((_, j) => j !== i))}><Icon name="trash" size={16} /></button>
          </div>
        ))}
        {contacts.length < 5 ? (
          <button type="button" className="btn btn-ghost btn-sm w-fit" onClick={() => setContacts((rows) => [...rows, { name: "", relationship: "", phone: "", email: "" }])}>
            <Icon name="plus" size={16} /> Add a contact
          </button>
        ) : null}
      </fieldset>
      <div className="flex justify-end gap-2">
        <button type="button" className="btn btn-ghost btn-md" onClick={() => { reset(); setEditing(false); }}>Cancel</button>
        <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Save</button>
      </div>
    </form>
  );
}

/** Self-service: your agreed contracts (with your own pay) and your current documents. */
export function MyContractsAndDocuments() {
  const { authenticatedFetch } = useStaffSession();
  const [contracts, setContracts] = useState<Contract[] | null>(null);
  const [documents, setDocuments] = useState<EmployeeDocument[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    authenticatedFetch((s) => getMyContracts(s)).then(setContracts).catch((err) => { setContracts([]); setError(errorMessage(err, "Could not load your contracts")); });
    authenticatedFetch((s) => getMyDocuments(s)).then(setDocuments).catch((err) => { setDocuments([]); setError(errorMessage(err, "Could not load your documents")); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function download(doc: EmployeeDocument) {
    setError("");
    try {
      saveDownloadedFile(await authenticatedFetch((s) => downloadMyDocument(s, doc.id, doc.fileName)));
    } catch (err) {
      setError(errorMessage(err, "Could not download the document"));
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {error ? <p className="alert alert-danger lg:col-span-2" role="alert">{error}</p> : null}
      <section className="panel grid content-start gap-3">
        <h2 className="text-sm font-semibold">My contracts</h2>
        {contracts === null ? <p className="text-sm text-ink-muted">Loading…</p> : contracts.length === 0 ? <p className="text-sm text-ink-muted">No contract on file yet.</p> : (
          <ul className="grid gap-2">
            {contracts.map((c) => (
              <li key={c.id} className="rounded-xl border border-line p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold">{c.contractNumber} · {humanize(c.contractType)}</p>
                  <span className={`badge ${c.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>{humanize(c.status)}</span>
                </div>
                <dl className="mt-2 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                  <Detail label="From" value={formatDate(c.startDate)} />
                  <Detail label="To" value={c.endDate ? formatDate(c.endDate) : "Open-ended"} />
                  <Detail label="Pay" value={c.salaryAmount != null ? `${formatMoney(c.salaryAmount, c.currency ?? undefined)} ${humanize(c.salaryType ?? "")}`.trim() : "—"} />
                  <Detail label="Hours / week" value={c.weeklyHours ?? "—"} />
                </dl>
                {c.probationEndDate ? <p className="mt-2 text-xs text-ink-muted">Probation until {formatDate(c.probationEndDate)}</p> : null}
              </li>
            ))}
          </ul>
        )}
      </section>
      <section className="panel grid content-start gap-3">
        <h2 className="text-sm font-semibold">My documents</h2>
        {documents === null ? <p className="text-sm text-ink-muted">Loading…</p> : documents.length === 0 ? <p className="text-sm text-ink-muted">HR has not filed any documents for you.</p> : (
          <ul className="grid gap-1">
            {documents.map((d) => (
              <li key={d.id} className="flex items-center justify-between gap-3 rounded-lg px-2 py-2 hover:bg-sunken">
                <span className="min-w-0 text-sm">
                  <span className="font-semibold text-ink">{d.title}</span>
                  <span className="block truncate text-xs text-ink-muted">
                    {humanize(d.documentType)} · {formatBytes(d.sizeBytes)}{d.expiresOn ? ` · expires ${formatDate(d.expiresOn)}` : ""}
                  </span>
                </span>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => download(d)} aria-label={`Download ${d.title}`}>
                  <Icon name="download" size={16} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
