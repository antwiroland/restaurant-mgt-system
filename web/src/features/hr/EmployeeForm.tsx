"use client";

import { useMemo, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { humanize } from "@/components/ui";
import { Field, todayIso } from "@/features/hr/ui";
import type { BranchRecord } from "@/lib/apiClient";
import {
  EMPLOYMENT_TYPES,
  GENDERS,
  type Department,
  type EmergencyContact,
  type EmployeeDetail,
  type EmployeeInput,
  type EmploymentType,
  type Gender,
  type Position,
} from "@/lib/hrApi";

type Props = {
  initial?: EmployeeDetail | null;
  departments: Department[];
  positions: Position[];
  branches: BranchRecord[];
  /** Branch is locked for users who cannot transfer employees. */
  canChangeBranch: boolean;
  submitting: boolean;
  onSubmit: (input: EmployeeInput) => void;
  onCancel: () => void;
  formId: string;
};

type FormState = {
  employeeNumber: string;
  firstName: string;
  middleName: string;
  lastName: string;
  gender: Gender | "";
  dateOfBirth: string;
  email: string;
  phone: string;
  address: string;
  departmentId: string;
  positionId: string;
  employmentType: EmploymentType;
  hireDate: string;
  primaryBranchId: string;
  qualifications: string;
  experience: string;
  contacts: EmergencyContact[];
  /** Adding an employee only: how they are paid ("" = decide later). */
  payoutMethod: "" | "BANK" | "MOBILE_MONEY" | "CASH";
  bankName: string;
  bankBranch: string;
  bankAccountName: string;
  bankAccountNumber: string;
  momoNetwork: string;
  momoNumber: string;
};

function initialState(e?: EmployeeDetail | null): FormState {
  return {
    employeeNumber: e?.summary.employeeNumber ?? "",
    firstName: e?.firstName ?? "",
    middleName: e?.middleName ?? "",
    lastName: e?.lastName ?? "",
    gender: e?.gender ?? "",
    dateOfBirth: e?.dateOfBirth ?? "",
    email: e?.summary.email ?? "",
    phone: e?.summary.phone ?? "",
    address: e?.address ?? "",
    departmentId: e?.summary.departmentId ?? "",
    positionId: e?.summary.positionId ?? "",
    employmentType: e?.summary.employmentType ?? "FULL_TIME",
    hireDate: e?.summary.hireDate ?? todayIso(),
    primaryBranchId: e?.summary.primaryBranchId ?? "",
    qualifications: e?.qualifications ?? "",
    experience: e?.experience ?? "",
    contacts: e?.emergencyContacts?.map((c) => ({ name: c.name, relationship: c.relationship ?? "", phone: c.phone, email: c.email ?? "" })) ?? [],
    payoutMethod: "", bankName: "", bankBranch: "", bankAccountName: "", bankAccountNumber: "", momoNetwork: "", momoNumber: "",
  };
}

type PayoutFields = Pick<FormState, "payoutMethod" | "bankName" | "bankAccountName" | "bankAccountNumber" | "momoNetwork" | "momoNumber">;

/** Same rules as the Payout tab, so the employee is never added with half their payout details. */
export function validatePayout(form: PayoutFields): string[] {
  const errors: string[] = [];
  if (form.payoutMethod === "BANK") {
    if (!form.bankName.trim() || !form.bankAccountName.trim() || !form.bankAccountNumber.trim()) {
      errors.push("Bank payments need the bank, account name and account number.");
    } else if (!/^[0-9A-Za-z -]{1,40}$/.test(form.bankAccountNumber.trim())) {
      errors.push("Account number may only contain letters, digits, spaces and dashes.");
    }
  }
  if (form.payoutMethod === "MOBILE_MONEY") {
    if (!form.momoNetwork || !form.momoNumber.trim()) errors.push("Mobile Money payments need the network and number.");
    else if (!/^\+?[0-9 ()-]{9,20}$/.test(form.momoNumber.trim())) errors.push("Mobile Money number is not valid.");
  }
  return errors;
}

/** Client-side checks mirroring the API's validation, so mistakes are caught before a round trip. */
export function validateEmployee(form: Pick<FormState, "firstName" | "hireDate" | "email" | "phone" | "dateOfBirth" | "contacts">): string[] {
  const errors: string[] = [];
  if (!form.firstName.trim()) errors.push("First name is required.");
  if (!form.hireDate) errors.push("Hire date is required.");
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.push("Email address is not valid.");
  if (form.phone && !/^[+0-9 ()-]{6,20}$/.test(form.phone.trim())) errors.push("Phone number is not valid.");
  if (form.dateOfBirth && form.dateOfBirth >= todayIso()) errors.push("Date of birth must be in the past.");
  form.contacts.forEach((c, i) => {
    if (!c.name.trim() || !c.phone.trim()) errors.push(`Emergency contact ${i + 1} needs a name and phone number.`);
    else if (!/^[+0-9 ()-]{6,20}$/.test(c.phone.trim())) errors.push(`Emergency contact ${i + 1} has an invalid phone number.`);
  });
  return errors;
}

export function EmployeeForm({ initial, departments, positions, branches, canChangeBranch, submitting, onSubmit, onCancel, formId }: Props) {
  const [form, setForm] = useState<FormState>(() => initialState(initial));
  const [errors, setErrors] = useState<string[]>([]);
  const editing = !!initial;

  const visiblePositions = useMemo(
    () => positions.filter((p) => (p.active || p.id === form.positionId) && (!form.departmentId || !p.departmentId || p.departmentId === form.departmentId)),
    [positions, form.departmentId, form.positionId],
  );
  const visibleDepartments = useMemo(() => departments.filter((d) => d.active || d.id === form.departmentId), [departments, form.departmentId]);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((s) => ({ ...s, [key]: value }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const problems = [...validateEmployee(form), ...(editing ? [] : validatePayout(form))];
    setErrors(problems);
    if (problems.length) return;
    onSubmit({
      employeeNumber: editing ? undefined : form.employeeNumber.trim() || undefined,
      firstName: form.firstName.trim(),
      middleName: form.middleName.trim() || undefined,
      lastName: form.lastName.trim() || undefined,
      gender: form.gender || null,
      dateOfBirth: form.dateOfBirth || null,
      email: form.email.trim() || undefined,
      phone: form.phone.trim() || undefined,
      address: form.address.trim() || undefined,
      departmentId: form.departmentId || null,
      positionId: form.positionId || null,
      employmentType: form.employmentType,
      hireDate: form.hireDate,
      reportingManagerId: initial?.reportingManagerId ?? null,
      primaryBranchId: form.primaryBranchId || null,
      qualifications: form.qualifications.trim() || undefined,
      experience: form.experience.trim() || undefined,
      emergencyContacts: form.contacts.map((c) => ({
        name: c.name.trim(),
        relationship: c.relationship?.trim() || undefined,
        phone: c.phone.trim(),
        email: c.email?.trim() || undefined,
      })),
      version: initial?.version,
      payout: editing || !form.payoutMethod ? undefined
        : form.payoutMethod === "BANK"
          ? { method: "BANK", bankName: form.bankName.trim(), bankBranch: form.bankBranch.trim() || undefined, bankAccountName: form.bankAccountName.trim(), bankAccountNumber: form.bankAccountNumber.trim() }
          : form.payoutMethod === "MOBILE_MONEY"
            ? { method: "MOBILE_MONEY", momoNetwork: form.momoNetwork, momoNumber: form.momoNumber.trim() }
            : { method: "CASH" },
    });
  }

  return (
    <form id={formId} onSubmit={submit} className="grid gap-5" noValidate>
      {errors.length ? (
        <div className="alert alert-danger" role="alert">
          <ul className="list-disc pl-4 text-sm">{errors.map((e) => <li key={e}>{e}</li>)}</ul>
        </div>
      ) : null}

      <fieldset className="grid gap-3 sm:grid-cols-3">
        <legend className="kicker mb-2 sm:col-span-3">Personal</legend>
        <Field label="First name *"><input className="input" value={form.firstName} onChange={(e) => set("firstName", e.target.value)} maxLength={80} required /></Field>
        <Field label="Middle name"><input className="input" value={form.middleName} onChange={(e) => set("middleName", e.target.value)} maxLength={80} /></Field>
        <Field label="Last name"><input className="input" value={form.lastName} onChange={(e) => set("lastName", e.target.value)} maxLength={80} /></Field>
        <Field label="Gender">
          <select className="select" value={form.gender} onChange={(e) => set("gender", e.target.value as Gender | "")}>
            <option value="">Not recorded</option>
            {GENDERS.map((g) => <option key={g} value={g}>{humanize(g)}</option>)}
          </select>
        </Field>
        <Field label="Date of birth"><input type="date" className="input" value={form.dateOfBirth} max={todayIso()} onChange={(e) => set("dateOfBirth", e.target.value)} /></Field>
        <Field label="Phone"><input className="input" inputMode="tel" value={form.phone} onChange={(e) => set("phone", e.target.value)} maxLength={20} placeholder="+233…" /></Field>
        <Field label="Email" className="sm:col-span-2"><input type="email" className="input" value={form.email} onChange={(e) => set("email", e.target.value)} maxLength={255} /></Field>
        <Field label="Residential address" className="sm:col-span-3"><input className="input" value={form.address} onChange={(e) => set("address", e.target.value)} maxLength={500} /></Field>
      </fieldset>

      <fieldset className="grid gap-3 sm:grid-cols-3">
        <legend className="kicker mb-2 sm:col-span-3">Employment</legend>
        <Field label="Employee number" hint={editing ? "Cannot be changed" : "Leave blank to generate"}>
          <input className="input" value={form.employeeNumber} onChange={(e) => set("employeeNumber", e.target.value)} disabled={editing} maxLength={30} />
        </Field>
        <Field label="Employment type *">
          <select className="select" value={form.employmentType} onChange={(e) => set("employmentType", e.target.value as EmploymentType)}>
            {EMPLOYMENT_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </select>
        </Field>
        <Field label="Hire date *"><input type="date" className="input" value={form.hireDate} onChange={(e) => set("hireDate", e.target.value)} required /></Field>
        <Field label="Department">
          <select className="select" value={form.departmentId} onChange={(e) => setForm((s) => ({ ...s, departmentId: e.target.value, positionId: "" }))}>
            <option value="">None</option>
            {visibleDepartments.map((d) => <option key={d.id} value={d.id}>{d.name}{d.active ? "" : " (archived)"}</option>)}
          </select>
        </Field>
        <Field label="Position">
          <select className="select" value={form.positionId} onChange={(e) => set("positionId", e.target.value)}>
            <option value="">None</option>
            {visiblePositions.map((p) => <option key={p.id} value={p.id}>{p.title}{p.active ? "" : " (archived)"}</option>)}
          </select>
        </Field>
        <Field label="Primary branch" hint={canChangeBranch ? undefined : "Moving branches needs transfer permission"}>
          <select className="select" value={form.primaryBranchId} onChange={(e) => set("primaryBranchId", e.target.value)} disabled={editing && !canChangeBranch}>
            <option value="">{editing ? "None" : "My branch"}</option>
            {branches.filter((b) => b.active || b.id === form.primaryBranchId).map((b) => <option key={b.id} value={b.id}>{b.code} — {b.name}</option>)}
          </select>
        </Field>
        <Field label="Qualifications" className="sm:col-span-3">
          <textarea className="textarea" rows={2} value={form.qualifications} onChange={(e) => set("qualifications", e.target.value)} maxLength={2000} />
        </Field>
        <Field label="Relevant experience" className="sm:col-span-3">
          <textarea className="textarea" rows={2} value={form.experience} onChange={(e) => set("experience", e.target.value)} maxLength={2000} />
        </Field>
      </fieldset>

      <fieldset className="grid gap-3">
        <legend className="kicker mb-2">Emergency contacts</legend>
        {form.contacts.length === 0 ? <p className="text-sm text-ink-soft">No emergency contacts yet.</p> : null}
        {form.contacts.map((c, i) => (
          <div key={i} className="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end">
            <Field label="Name"><input className="input" value={c.name} maxLength={120} onChange={(e) => set("contacts", form.contacts.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} /></Field>
            <Field label="Relationship"><input className="input" value={c.relationship ?? ""} maxLength={60} onChange={(e) => set("contacts", form.contacts.map((x, j) => (j === i ? { ...x, relationship: e.target.value } : x)))} /></Field>
            <Field label="Phone"><input className="input" inputMode="tel" value={c.phone} maxLength={20} onChange={(e) => set("contacts", form.contacts.map((x, j) => (j === i ? { ...x, phone: e.target.value } : x)))} /></Field>
            <button type="button" className="btn btn-ghost btn-sm" aria-label={`Remove contact ${i + 1}`} onClick={() => set("contacts", form.contacts.filter((_, j) => j !== i))}>
              <Icon name="trash" size={16} />
            </button>
          </div>
        ))}
        {form.contacts.length < 5 ? (
          <div>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => set("contacts", [...form.contacts, { name: "", relationship: "", phone: "", email: "" }])}>
              <Icon name="plus" size={16} /> Add contact
            </button>
          </div>
        ) : null}
      </fieldset>

      {!editing ? (
        <fieldset className="grid gap-3">
          <legend className="kicker mb-2">How they&apos;re paid <span className="font-normal normal-case tracking-normal text-ink-muted">(optional)</span></legend>
          <p className="-mt-2 text-sm text-ink-soft">Used for payroll payment files. You can also add or change this later on the Payout tab.</p>
          <div className="segmented grid grid-cols-2 sm:grid-cols-4" role="group" aria-label="Paid by">
            {([["", "Decide later"], ["BANK", "Bank transfer"], ["MOBILE_MONEY", "Mobile Money"], ["CASH", "Cash"]] as const).map(([value, label]) => (
              <button key={value || "later"} type="button" aria-pressed={form.payoutMethod === value} onClick={() => set("payoutMethod", value)}>{label}</button>
            ))}
          </div>
          {form.payoutMethod === "BANK" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Bank"><input className="input" maxLength={120} value={form.bankName} onChange={(e) => set("bankName", e.target.value)} /></Field>
              <Field label="Branch (optional)"><input className="input" maxLength={120} value={form.bankBranch} onChange={(e) => set("bankBranch", e.target.value)} /></Field>
              <Field label="Account name">
                <input className="input" maxLength={160} value={form.bankAccountName} placeholder={[form.firstName, form.lastName].filter(Boolean).join(" ")}
                  onChange={(e) => set("bankAccountName", e.target.value)} />
              </Field>
              <Field label="Account number"><input className="input font-mono" maxLength={40} inputMode="numeric" value={form.bankAccountNumber} onChange={(e) => set("bankAccountNumber", e.target.value)} /></Field>
            </div>
          ) : null}
          {form.payoutMethod === "MOBILE_MONEY" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Network">
                <select className="select" value={form.momoNetwork} onChange={(e) => set("momoNetwork", e.target.value)}>
                  <option value="">Choose…</option>
                  <option value="MTN">MTN</option>
                  <option value="Telecel">Telecel</option>
                  <option value="AT">AT</option>
                </select>
              </Field>
              <Field label="Mobile Money number">
                <input className="input font-mono" type="tel" inputMode="tel" maxLength={20} value={form.momoNumber} placeholder={form.phone || undefined}
                  onChange={(e) => set("momoNumber", e.target.value)} />
              </Field>
              {form.phone && !form.momoNumber ? (
                <button type="button" className="btn btn-ghost btn-sm justify-self-start sm:col-span-2" onClick={() => set("momoNumber", form.phone)}>
                  Use their phone number ({form.phone})
                </button>
              ) : null}
            </div>
          ) : null}
        </fieldset>
      ) : null}

      <div className="flex justify-end gap-2 border-t border-line pt-4">
        <button type="button" className="btn btn-ghost btn-md" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn btn-primary btn-md" disabled={submitting}>{submitting ? "Saving…" : editing ? "Save changes" : "Create employee"}</button>
      </div>
    </form>
  );
}
