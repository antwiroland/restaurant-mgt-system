import type { ReactNode } from "react";
import { Icon, type IconName } from "@/components/Icon";

/* ------------------------------------------------------------------ */
/* PageHeader — consistent title block for every staff page            */
/* ------------------------------------------------------------------ */
type PageHeaderProps = {
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  icon?: IconName;
};

export function PageHeader({ eyebrow, title, description, actions, icon }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        {icon ? (
          <span className="mt-0.5 hidden h-11 w-11 shrink-0 place-items-center rounded-xl bg-brand-subtle text-brand sm:grid">
            <Icon name={icon} size={22} />
          </span>
        ) : null}
        <div className="min-w-0">
          {eyebrow ? <p className="kicker">{eyebrow}</p> : null}
          <h1 className="heading-lg mt-1">{title}</h1>
          {description ? <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-ink-soft">{description}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}

/* ------------------------------------------------------------------ */
/* StatCard — KPI tile                                                 */
/* ------------------------------------------------------------------ */
type StatCardProps = {
  label: string;
  value: ReactNode;
  meta?: ReactNode;
  icon?: IconName;
  tone?: "brand" | "sage" | "gold" | "info" | "neutral" | "danger";
};

const toneClasses: Record<NonNullable<StatCardProps["tone"]>, string> = {
  brand: "bg-brand-subtle text-brand",
  sage: "bg-success-subtle text-success-on",
  gold: "bg-accent-subtle text-accent-hover",
  info: "bg-info-subtle text-info-on",
  neutral: "bg-muted text-ink-soft",
  danger: "bg-danger-subtle text-danger-on",
};

export function StatCard({ label, value, meta, icon, tone = "neutral" }: StatCardProps) {
  return (
    <article className="stat">
      <div className="flex items-start justify-between gap-3">
        <p className="stat-label">{label}</p>
        {icon ? (
          <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg ${toneClasses[tone]}`}>
            <Icon name={icon} size={18} />
          </span>
        ) : null}
      </div>
      <p className="stat-value">{value}</p>
      {meta ? <p className="stat-meta">{meta}</p> : null}
    </article>
  );
}

/* ------------------------------------------------------------------ */
/* EmptyState / ErrorState                                             */
/* ------------------------------------------------------------------ */
type EmptyStateProps = {
  icon?: IconName;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
  tone?: "light" | "dark";
};

export function EmptyState({ icon = "sparkle", title, description, action, compact = false, tone = "light" }: EmptyStateProps) {
  const dark = tone === "dark";
  return (
    <div
      className={[
        "grid justify-items-center gap-3 rounded-card border border-dashed text-center",
        compact ? "px-4 py-6" : "px-6 py-12",
        dark ? "border-night-line text-night-soft" : "border-line-strong bg-ivory/60",
      ].join(" ")}
    >
      <span className={`grid h-12 w-12 place-items-center rounded-full ${dark ? "bg-night-card text-night-soft" : "bg-surface text-ink-muted shadow-xs"}`}>
        <Icon name={icon} size={22} />
      </span>
      <div className="grid gap-1">
        <p className={`font-display text-base font-bold ${dark ? "text-night-ink" : "text-ink"}`}>{title}</p>
        {description ? <p className={`mx-auto max-w-sm text-sm ${dark ? "text-night-soft" : "text-ink-soft"}`}>{description}</p> : null}
      </div>
      {action ? <div className="mt-1">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="alert alert-danger items-center justify-between" role="alert">
      <span className="flex items-start gap-2">
        <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
        <span>{message}</span>
      </span>
      {onRetry ? (
        <button type="button" className="btn btn-danger btn-sm" onClick={onRetry}>
          <Icon name="refresh" size={16} /> Retry
        </button>
      ) : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Order status badge — maps every backend status to a token colour    */
/* ------------------------------------------------------------------ */
const statusClass: Record<string, string> = {
  PENDING: "badge-pending",
  CONFIRMED: "badge-confirmed",
  PREPARING: "badge-preparing",
  READY: "badge-ready",
  OUT_FOR_DELIVERY: "badge-info",
  DELIVERED: "badge-completed",
  COMPLETED: "badge-completed",
  CANCELLED: "badge-cancelled",
  VOIDED: "badge-voided",
  AVAILABLE: "badge-available",
  OCCUPIED: "badge-occupied",
  RESERVED: "badge-reserved",
  CLOSED: "badge-closed",
  OPEN: "badge-success",
  SUCCESS: "badge-success",
  FAILED: "badge-danger",
  REFUNDED: "badge-neutral",
  INITIATED: "badge-pending",
  PAYMENT_PENDING: "badge-pending",
};

export function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((part, index) => (index === 0 ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(" ");
}

export function StatusBadge({ status, className = "" }: { status: string; className?: string }) {
  return <span className={`badge badge-dot ${statusClass[status] ?? "badge-neutral"} ${className}`}>{humanize(status)}</span>;
}

/* ------------------------------------------------------------------ */
/* SectionHeader — heading row inside a panel                          */
/* ------------------------------------------------------------------ */
export function SectionHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="heading-md">{title}</h2>
        {description ? <p className="mt-0.5 text-sm text-ink-soft">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}
