"use client";

import { useEffect, useState, type FormEvent } from "react";
import { SectionHeader } from "@/components/ui";
import { useStaffSession } from "@/components/SessionProvider";
import { Field, errorMessage, formatDateTime } from "@/features/hr/ui";
import { getMyPayout, getPayout, updateMyPayout, updatePayout, type PaymentMethod, type PayoutDetails, type PayoutRequest } from "@/lib/hrApi";

const METHODS: Array<{ value: Exclude<PaymentMethod, "OTHER">; label: string }> = [
  { value: "BANK", label: "Bank transfer" },
  { value: "MOBILE_MONEY", label: "Mobile Money" },
  { value: "CASH", label: "Cash" },
];

const EMPTY: PayoutRequest = { method: "BANK", bankName: "", bankBranch: "", bankAccountName: "", bankAccountNumber: "", momoNetwork: "", momoNumber: "" };

/**
 * How an employee is paid. Payroll uses these details for the bank and Mobile Money payment files. On an employee's
 * profile (`employeeId`) or for the signed-in employee (`self`). Viewers without payroll processing see numbers masked.
 */
export function PayoutPanel({ employeeId, self = false, canEdit }: { employeeId?: string; self?: boolean; canEdit: boolean }) {
  const { authenticatedFetch } = useStaffSession();
  const [details, setDetails] = useState<PayoutDetails | null>(null);
  const [form, setForm] = useState<PayoutRequest>(EMPTY);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    authenticatedFetch((s) => (self ? getMyPayout(s) : getPayout(s, employeeId ?? "")))
      .then((d) => { setDetails(d); setForm(toForm(d)); setEditing(!d.method && canEdit); })
      .catch((err) => setError(errorMessage(err, "Could not load payout details")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [employeeId, self]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const body = clean(form);
      const saved = await authenticatedFetch((s) => (self ? updateMyPayout(s, body) : updatePayout(s, employeeId ?? "", body)));
      setDetails(saved);
      setForm(toForm(saved));
      setEditing(false);
      setNotice("Payout details saved.");
    } catch (err) {
      setError(errorMessage(err, "Could not save payout details"));
    } finally {
      setBusy(false);
    }
  }

  const set = (key: keyof PayoutRequest) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <section className="panel grid gap-3" aria-label="Payout details">
      <SectionHeader title={self ? "How I'm paid" : "Payout details"}
        description={self ? "Payroll pays your net pay to this account. Keep it up to date." : "Used for this person's bank or Mobile Money payment file."} />
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
      {notice ? <p className="alert alert-success" role="status">{notice}</p> : null}

      {details && !editing ? (
        <div className="grid gap-2 text-sm">
          {!details.method ? <p className="text-ink-soft">Not set yet.</p> : (
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
              <dt className="text-ink-soft">Method</dt><dd>{METHODS.find((m) => m.value === details.method)?.label ?? details.method}</dd>
              {details.method === "BANK" ? (
                <>
                  <dt className="text-ink-soft">Bank</dt><dd>{details.bankName}{details.bankBranch ? ` · ${details.bankBranch}` : ""}</dd>
                  <dt className="text-ink-soft">Account name</dt><dd>{details.bankAccountName}</dd>
                  <dt className="text-ink-soft">Account number</dt><dd className="font-mono">{details.bankAccountNumber}</dd>
                </>
              ) : null}
              {details.method === "MOBILE_MONEY" ? (
                <>
                  <dt className="text-ink-soft">Network</dt><dd>{details.momoNetwork}</dd>
                  <dt className="text-ink-soft">Number</dt><dd className="font-mono">{details.momoNumber}</dd>
                </>
              ) : null}
            </dl>
          )}
          {details.method && !details.complete ? <p className="alert alert-warning">Details are incomplete, so this person cannot be included in a payment file.</p> : null}
          {details.updatedAt ? <p className="text-xs text-ink-muted">Last changed {formatDateTime(details.updatedAt)}</p> : null}
          {canEdit && !details.masked ? (
            <button type="button" className="btn btn-secondary btn-sm justify-self-start" onClick={() => setEditing(true)}>
              {details.method ? "Change" : "Add details"}
            </button>
          ) : null}
        </div>
      ) : null}

      {details && editing ? (
        <form className="grid gap-3" onSubmit={(e) => void save(e)}>
          <fieldset className="grid gap-2">
            <legend className="field-label">Paid by</legend>
            <div className="segmented grid grid-cols-3" role="group" aria-label="Paid by">
              {METHODS.map((m) => (
                <button key={m.value} type="button" aria-pressed={form.method === m.value} onClick={() => setForm((f) => ({ ...f, method: m.value }))}>{m.label}</button>
              ))}
            </div>
          </fieldset>
          {form.method === "BANK" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Bank"><input className="input" required maxLength={120} value={form.bankName ?? ""} onChange={set("bankName")} /></Field>
              <Field label="Branch (optional)"><input className="input" maxLength={120} value={form.bankBranch ?? ""} onChange={set("bankBranch")} /></Field>
              <Field label="Account name"><input className="input" required maxLength={160} value={form.bankAccountName ?? ""} onChange={set("bankAccountName")} /></Field>
              <Field label="Account number"><input className="input font-mono" required maxLength={40} inputMode="numeric" value={form.bankAccountNumber ?? ""} onChange={set("bankAccountNumber")} /></Field>
            </div>
          ) : null}
          {form.method === "MOBILE_MONEY" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Network">
                <select className="select" required value={form.momoNetwork ?? ""} onChange={set("momoNetwork")}>
                  <option value="">Choose…</option>
                  <option value="MTN">MTN</option>
                  <option value="Telecel">Telecel</option>
                  <option value="AT">AT</option>
                </select>
              </Field>
              <Field label="Mobile Money number"><input className="input font-mono" required type="tel" inputMode="tel" maxLength={20} value={form.momoNumber ?? ""} onChange={set("momoNumber")} /></Field>
            </div>
          ) : null}
          {form.method === "CASH" ? <p className="text-sm text-ink-soft">Paid in cash: not included in payment files.</p> : null}
          <div className="flex gap-2">
            <button type="submit" className="btn btn-primary btn-md" disabled={busy}>Save</button>
            {details.method ? <button type="button" className="btn btn-secondary btn-md" onClick={() => { setForm(toForm(details)); setEditing(false); }}>Cancel</button> : null}
          </div>
        </form>
      ) : null}
    </section>
  );
}

function toForm(d: PayoutDetails): PayoutRequest {
  return {
    method: d.method && d.method !== "OTHER" ? d.method : "BANK",
    bankName: d.bankName ?? "", bankBranch: d.bankBranch ?? "", bankAccountName: d.bankAccountName ?? "",
    bankAccountNumber: d.masked ? "" : d.bankAccountNumber ?? "", momoNetwork: d.momoNetwork ?? "", momoNumber: d.masked ? "" : d.momoNumber ?? "",
  };
}

/** Sends only the fields for the chosen method. */
function clean(f: PayoutRequest): PayoutRequest {
  if (f.method === "BANK") return { method: "BANK", bankName: f.bankName, bankBranch: f.bankBranch || undefined, bankAccountName: f.bankAccountName, bankAccountNumber: f.bankAccountNumber };
  if (f.method === "MOBILE_MONEY") return { method: "MOBILE_MONEY", momoNetwork: f.momoNetwork, momoNumber: f.momoNumber };
  return { method: f.method };
}
