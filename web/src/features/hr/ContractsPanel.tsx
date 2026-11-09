"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Modal } from "@/components/Modal";
import { Skeleton } from "@/components/Skeleton";
import { EmptyState, ErrorState, SectionHeader, humanize } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import { Detail, Field, errorMessage, formatDate, todayIso } from "@/features/hr/ui";
import { formatMoney } from "@/lib/brand";
import {
  CONTRACT_TYPES,
  OPEN_ENDED_CONTRACTS,
  SALARY_TYPES,
  activateContract,
  createContract,
  endContract,
  getContracts,
  renewContract,
  updateContract,
  type Contract,
  type ContractInput,
  type ContractStatus,
  type ContractType,
  type EmployeeDocument,
  type SalaryType,
} from "@/lib/hrApi";

const statusTone: Record<ContractStatus, string> = {
  DRAFT: "badge-pending",
  ACTIVE: "badge-success",
  EXPIRED: "badge-warning",
  ENDED: "badge-neutral",
  RENEWED: "badge-info",
};

type Editor = { mode: "create" | "edit" | "renew"; contract?: Contract } | null;

export function ContractsPanel({ employeeId, documents }: { employeeId: string; documents: EmployeeDocument[] }) {
  const { authenticatedFetch } = useStaffSession();
  const { can } = useHrAccess();
  const [contracts, setContracts] = useState<Contract[] | null>(null);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [editor, setEditor] = useState<Editor>(null);
  const [ending, setEnding] = useState<Contract | null>(null);
  const canManage = can("hr.contract.manage");

  const load = useCallback(async () => {
    setError("");
    try {
      setContracts(await authenticatedFetch((s) => getContracts(s, employeeId)));
    } catch (err) {
      setError(errorMessage(err, "Could not load contracts"));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function activate(contract: Contract) {
    setActionError("");
    try {
      await authenticatedFetch((s) => activateContract(s, contract.id));
      await load();
    } catch (err) {
      setActionError(errorMessage(err, "Could not activate the contract"));
    }
  }

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!contracts) return <Skeleton className="h-40 w-full" />;

  return (
    <section className="grid gap-4">
      <SectionHeader
        title="Employment contracts"
        description="Only one contract can be active. Renewals keep the history; expiry never changes the employee's status."
        actions={canManage ? (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditor({ mode: "create" })}>
            <Icon name="plus" size={16} /> New contract
          </button>
        ) : null}
      />
      {actionError ? <p className="alert alert-danger" role="alert">{actionError}</p> : null}
      {contracts.length === 0 ? (
        <EmptyState compact icon="note" title="No contracts recorded" />
      ) : (
        <div className="grid gap-3">
          {contracts.map((c) => (
            <article key={c.id} className="card p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{c.contractNumber} · {humanize(c.contractType)}</p>
                  <p className="text-sm text-ink-soft">{formatDate(c.startDate)} → {c.endDate ? formatDate(c.endDate) : "open-ended"}</p>
                </div>
                <span className={`badge badge-dot ${statusTone[c.status]}`}>{humanize(c.status)}</span>
              </div>
              <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <Detail label="Pay" value={c.payVisible ? (c.salaryAmount != null ? `${formatMoney(c.salaryAmount, c.currency ?? undefined)} ${humanize(c.salaryType ?? "")}`.trim() : null) : "Restricted"} />
                <Detail label="Weekly hours" value={c.weeklyHours} />
                <Detail label="Probation ends" value={c.probationEndDate ? formatDate(c.probationEndDate) : null} />
                <Detail label="Document" value={c.documentId ? documents.find((d) => d.id === c.documentId)?.title ?? "Attached" : null} />
              </dl>
              {c.endReason ? <p className="mt-2 text-sm text-ink-soft">Ended: {c.endReason}</p> : null}
              {canManage && (c.status === "DRAFT" || c.status === "ACTIVE" || c.status === "EXPIRED") ? (
                <div className="mt-3 flex flex-wrap gap-2 border-t border-line pt-3">
                  {c.status === "DRAFT" ? (
                    <>
                      <button type="button" className="btn btn-success btn-sm" onClick={() => activate(c)}>Activate</button>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditor({ mode: "edit", contract: c })}>Edit</button>
                    </>
                  ) : null}
                  {c.status === "ACTIVE" || c.status === "EXPIRED" ? (
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditor({ mode: "renew", contract: c })}>Renew</button>
                  ) : null}
                  {c.status === "ACTIVE" || c.status === "DRAFT" ? (
                    <button type="button" className="btn btn-danger btn-sm" onClick={() => setEnding(c)}>{c.status === "DRAFT" ? "Cancel draft" : "End contract"}</button>
                  ) : null}
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {editor ? (
        <ContractEditor
          editor={editor}
          employeeId={employeeId}
          documents={documents}
          canSetPay={can("hr.compensation.view")}
          onClose={() => setEditor(null)}
          onSaved={() => { setEditor(null); void load(); }}
        />
      ) : null}
      {ending ? <EndContractModal contract={ending} onClose={() => setEnding(null)} onDone={() => { setEnding(null); void load(); }} /> : null}
    </section>
  );
}

function ContractEditor({ editor, employeeId, documents, canSetPay, onClose, onSaved }: {
  editor: NonNullable<Editor>; employeeId: string; documents: EmployeeDocument[]; canSetPay: boolean; onClose: () => void; onSaved: () => void;
}) {
  const { authenticatedFetch } = useStaffSession();
  const source = editor.mode === "edit" ? editor.contract : undefined;
  const renewing = editor.mode === "renew" ? editor.contract : undefined;
  const [form, setForm] = useState({
    contractType: (source?.contractType ?? renewing?.contractType ?? "PERMANENT") as ContractType,
    startDate: source?.startDate ?? (renewing?.endDate ?? todayIso()),
    endDate: source?.endDate ?? "",
    probationEndDate: source?.probationEndDate ?? "",
    salaryType: (source?.salaryType ?? renewing?.salaryType ?? "") as SalaryType | "",
    salaryAmount: source?.salaryAmount != null ? String(source.salaryAmount) : renewing?.salaryAmount != null ? String(renewing.salaryAmount) : "",
    weeklyHours: source?.weeklyHours != null ? String(source.weeklyHours) : renewing?.weeklyHours != null ? String(renewing.weeklyHours) : "",
    terms: source?.terms ?? renewing?.terms ?? "",
    documentId: source?.documentId ?? "",
  });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const needsEnd = !OPEN_ENDED_CONTRACTS.includes(form.contractType);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (needsEnd && !form.endDate) return setError("This contract type needs an end date.");
    if (form.endDate && form.endDate < form.startDate) return setError("End date cannot be before the start date.");
    if (!!form.salaryType !== !!form.salaryAmount) return setError("Enter both the pay basis and the amount, or neither.");
    if (form.salaryAmount && !/^\d{1,12}(\.\d{1,2})?$/.test(form.salaryAmount)) return setError("Pay amount must be a number with up to 2 decimals.");
    const input: ContractInput = {
      contractType: form.contractType,
      startDate: form.startDate,
      endDate: form.endDate || null,
      probationEndDate: form.probationEndDate || null,
      salaryType: canSetPay ? form.salaryType || null : null,
      salaryAmount: canSetPay ? form.salaryAmount || null : null,
      weeklyHours: form.weeklyHours || null,
      terms: form.terms || undefined,
      documentId: form.documentId || null,
    };
    setSaving(true);
    setError("");
    try {
      await authenticatedFetch((s) =>
        editor.mode === "edit" && source ? updateContract(s, source.id, input)
          : editor.mode === "renew" && renewing ? renewContract(s, renewing.id, input)
            : createContract(s, employeeId, input));
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Could not save the contract"));
    } finally {
      setSaving(false);
    }
  }

  const title = editor.mode === "renew" ? `Renew ${renewing?.contractNumber}` : editor.mode === "edit" ? `Edit ${source?.contractNumber}` : "New contract";
  return (
    <Modal
      open
      title={title}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Cancel</button>
          <button type="submit" form="contract-form" className="btn btn-primary btn-md" disabled={saving}>{saving ? "Saving…" : "Save as draft"}</button>
        </>
      )}
    >
      <form id="contract-form" onSubmit={submit} className="grid gap-3 sm:grid-cols-2" noValidate>
        {error ? <p className="alert alert-danger sm:col-span-2" role="alert">{error}</p> : null}
        <Field label="Contract type">
          <select className="select" value={form.contractType} onChange={(e) => setForm((f) => ({ ...f, contractType: e.target.value as ContractType }))}>
            {CONTRACT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Weekly hours"><input className="input" inputMode="decimal" value={form.weeklyHours} onChange={(e) => setForm((f) => ({ ...f, weeklyHours: e.target.value }))} /></Field>
        <Field label="Start date *"><input type="date" className="input" value={form.startDate} onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))} required /></Field>
        <Field label={needsEnd ? "End date *" : "End date"}><input type="date" className="input" value={form.endDate} min={form.startDate} onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))} /></Field>
        <Field label="Probation ends"><input type="date" className="input" value={form.probationEndDate} min={form.startDate} onChange={(e) => setForm((f) => ({ ...f, probationEndDate: e.target.value }))} /></Field>
        <Field label="Signed document">
          <select className="select" value={form.documentId} onChange={(e) => setForm((f) => ({ ...f, documentId: e.target.value }))}>
            <option value="">None</option>
            {documents.filter((d) => !d.archived).map((d) => <option key={d.id} value={d.id}>{d.title}</option>)}
          </select>
        </Field>
        {canSetPay ? (
          <>
            <Field label="Pay basis">
              <select className="select" value={form.salaryType} onChange={(e) => setForm((f) => ({ ...f, salaryType: e.target.value as SalaryType | "" }))}>
                <option value="">Not set</option>
                {SALARY_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
              </select>
            </Field>
            <Field label="Amount"><input className="input" inputMode="decimal" value={form.salaryAmount} onChange={(e) => setForm((f) => ({ ...f, salaryAmount: e.target.value }))} /></Field>
          </>
        ) : (
          <p className="text-sm text-ink-soft sm:col-span-2">Pay can only be set by someone with compensation access.</p>
        )}
        <Field label="Terms and conditions" className="sm:col-span-2">
          <textarea className="textarea" rows={4} value={form.terms} onChange={(e) => setForm((f) => ({ ...f, terms: e.target.value }))} maxLength={20000} />
        </Field>
      </form>
    </Modal>
  );
}

function EndContractModal({ contract, onClose, onDone }: { contract: Contract; onClose: () => void; onDone: () => void }) {
  const { authenticatedFetch } = useStaffSession();
  const [reason, setReason] = useState("");
  const [endDate, setEndDate] = useState(todayIso());
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!reason.trim()) return setError("A reason is required.");
    setBusy(true);
    try {
      await authenticatedFetch((s) => endContract(s, contract.id, { reason: reason.trim(), endDate: contract.status === "ACTIVE" ? endDate : undefined }));
      onDone();
    } catch (err) {
      setError(errorMessage(err, "Could not end the contract"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open
      title={contract.status === "DRAFT" ? `Cancel draft ${contract.contractNumber}` : `End ${contract.contractNumber}`}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-ghost btn-md" onClick={onClose}>Back</button>
          <button type="button" className="btn btn-danger-solid btn-md" onClick={submit} disabled={busy}>{busy ? "Saving…" : "Confirm"}</button>
        </>
      )}
    >
      <div className="grid gap-3">
        {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
        <p className="text-sm text-ink-soft">Ending a contract does not change the employee&apos;s employment status.</p>
        {contract.status === "ACTIVE" ? (
          <Field label="End date"><input type="date" className="input" value={endDate} min={contract.startDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
        ) : null}
        <Field label="Reason *"><textarea className="textarea" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} /></Field>
      </div>
    </Modal>
  );
}
