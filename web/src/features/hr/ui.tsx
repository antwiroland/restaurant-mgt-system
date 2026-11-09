"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Icon } from "@/components/Icon";
import { Skeleton } from "@/components/Skeleton";
import { humanize } from "@/components/ui";
import { useHrAccess } from "@/features/hr/HrAccessContext";
import type { EmploymentStatus } from "@/lib/hrApi";

/** Renders children only when the user holds one of the HR permissions; otherwise explains why. */
export function HrGuard({ anyOf, children, title = "HR access required" }: { anyOf: string[]; children: ReactNode; title?: string }) {
  const { access, loading, can } = useHrAccess();
  if (loading && !access) {
    return (
      <div className="grid gap-3" aria-busy="true">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }
  if (!can(...anyOf)) {
    return (
      <section className="panel mx-auto mt-10 max-w-lg p-8 text-center">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-muted text-ink-soft"><Icon name="shield" /></span>
        <h1 className="heading-md mt-4">{title}</h1>
        <p className="mt-2 text-sm text-ink-soft">Your account does not have permission for this page. An owner or admin can grant HR access.</p>
        <Link href="/account" className="btn btn-secondary btn-md mt-6">Back to my account</Link>
      </section>
    );
  }
  return <>{children}</>;
}

const statusTone: Record<EmploymentStatus, string> = {
  ACTIVE: "badge-success",
  ON_LEAVE: "badge-info",
  SUSPENDED: "badge-warning",
  RESIGNED: "badge-neutral",
  TERMINATED: "badge-danger",
  RETIRED: "badge-neutral",
};

export function EmploymentBadge({ status }: { status: EmploymentStatus }) {
  return <span className={`badge badge-dot ${statusTone[status]}`}>{humanize(status)}</span>;
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (total <= size) return null;
  return (
    <nav className="flex items-center justify-between gap-3 pt-3 text-sm" aria-label="Pagination">
      <p className="text-ink-soft tabular">
        {page * size + 1}–{Math.min(total, (page + 1) * size)} of {total}
      </p>
      <div className="flex gap-2">
        <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          <Icon name="chevronLeft" size={16} /> Previous
        </button>
        <button type="button" className="btn btn-secondary btn-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          Next <Icon name="chevronRight" size={16} />
        </button>
      </div>
    </nav>
  );
}

export function Field({ label, hint, children, className = "" }: { label: string; hint?: string; children: ReactNode; className?: string }) {
  return (
    <label className={`field ${className}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export function Detail({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-0.5 break-words text-sm text-ink">{value === null || value === undefined || value === "" ? <span className="text-ink-muted">—</span> : value}</dd>
    </div>
  );
}

export function formatDate(value?: string | null): string {
  if (!value) return "—";
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

export function formatDateTime(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(undefined, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function todayIso(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

export function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}
