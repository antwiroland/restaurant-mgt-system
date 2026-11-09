"use client";

import type { ReactNode } from "react";
import { SectionHeader, humanize } from "@/components/ui";
import { Detail, formatDate } from "@/features/hr/ui";
import type { EmployeeDetail } from "@/lib/hrApi";

/** Read-only employee record, shared by the HR profile and the self-service page. */
export function EmployeeOverview({ employee, linkActions }: { employee: EmployeeDetail; linkActions?: ReactNode }) {
  const s = employee.summary;
  return (
    <div className="grid gap-8">
      <section className="grid gap-3">
        <SectionHeader title="Personal" />
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <Detail label="First name" value={employee.firstName} />
          <Detail label="Middle name" value={employee.middleName} />
          <Detail label="Last name" value={employee.lastName} />
          <Detail label="Gender" value={employee.gender ? humanize(employee.gender) : null} />
          <Detail label="Date of birth" value={employee.dateOfBirth ? formatDate(employee.dateOfBirth) : null} />
          <Detail label="Phone" value={s.phone} />
          <Detail label="Email" value={s.email} />
          <Detail label="Address" value={employee.address} />
        </dl>
      </section>

      <section className="grid gap-3">
        <SectionHeader title="Employment" />
        <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <Detail label="Employee number" value={s.employeeNumber} />
          <Detail label="Department" value={s.departmentName} />
          <Detail label="Position" value={s.positionTitle} />
          <Detail label="Primary branch" value={s.primaryBranchName ?? "Head office"} />
          <Detail label="Employment type" value={humanize(s.employmentType)} />
          <Detail label="Hire date" value={formatDate(s.hireDate)} />
          <Detail label="Reports to" value={employee.reportingManagerName} />
          <Detail label="Status" value={humanize(s.employmentStatus)} />
          {employee.exitDate ? <Detail label="Exit date" value={formatDate(employee.exitDate)} /> : null}
          {employee.exitReason ? <Detail label="Exit reason" value={employee.exitReason} /> : null}
        </dl>
        {employee.qualifications || employee.experience ? (
          <dl className="grid gap-4 md:grid-cols-2">
            <Detail label="Qualifications" value={employee.qualifications} />
            <Detail label="Experience" value={employee.experience} />
          </dl>
        ) : null}
      </section>

      <section className="grid gap-3">
        <SectionHeader title="Login account" actions={linkActions} />
        {employee.linkedUser ? (
          <p className="text-sm">
            <span className="font-semibold">{employee.linkedUser.name}</span> · {humanize(employee.linkedUser.role)}
            {employee.linkedUser.active ? "" : " · deactivated"}
          </p>
        ) : (
          <p className="text-sm text-ink-soft">Not linked. This employee cannot use self-service until a login is linked.</p>
        )}
      </section>

      <section className="grid gap-3">
        <SectionHeader title="Emergency contacts" />
        {employee.emergencyContacts.length ? (
          <ul className="grid gap-2 md:grid-cols-2">
            {employee.emergencyContacts.map((c, i) => (
              <li key={c.id ?? i} className="rounded-xl border border-line p-3 text-sm">
                <p className="font-semibold">{c.name}</p>
                <p className="text-ink-soft">{[c.relationship, c.phone, c.email].filter(Boolean).join(" · ")}</p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-ink-soft">None recorded.</p>
        )}
      </section>
    </div>
  );
}
