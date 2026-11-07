"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { routeByRole } from "@/features/auth/auth";
import { useStaffSession } from "@/components/SessionProvider";
import { Spinner } from "@/components/Spinner";
import { Icon } from "@/components/Icon";
import { BrandMark } from "@/components/NavSidebar";
import { brand } from "@/lib/brand";
import { canAccessPath } from "@/lib/navigation";

/** Only allow same-origin, absolute-path redirects (no `//evil.com`). */
function safeNext(): string | null {
  if (typeof window === "undefined") return null;
  const next = new URLSearchParams(window.location.search).get("next");
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/login")) return null;
  return next;
}

export default function LoginPage() {
  const router = useRouter();
  const { login, logout, session } = useStaffSession();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ phone?: string; password?: string }>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextFieldErrors: { phone?: string; password?: string } = {};
    if (!phone.trim()) nextFieldErrors.phone = "Enter your staff phone number";
    if (!password.trim()) nextFieldErrors.password = "Enter your password";
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      return;
    }

    setSubmitting(true);
    setError("");
    setFieldErrors({});

    try {
      const nextSession = await login(phone.trim(), password);
      if (nextSession.user.role === "CUSTOMER") {
        await logout();
        throw new Error("Customer accounts cannot access the staff console");
      }
      const next = safeNext();
      const role = nextSession.user.role;
      router.replace(next && canAccessPath(role, next) ? next : routeByRole(role));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="grid min-h-dvh bg-canvas lg:grid-cols-[1.05fr_1fr]">
      {/* Editorial panel */}
      <section className="relative hidden overflow-hidden bg-espresso text-espresso-ink lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 opacity-[0.55]"
          style={{
            background:
              "radial-gradient(60% 50% at 85% 10%, rgba(180,83,42,0.35) 0%, transparent 60%), radial-gradient(50% 45% at 10% 95%, rgba(201,164,92,0.22) 0%, transparent 60%)",
          }}
        />
        <div className="relative flex items-center gap-3">
          <BrandMark size={44} />
          <div>
            <p className="font-serif text-2xl font-semibold leading-none">{brand.name}</p>
            <p className="mt-1 text-xs font-semibold uppercase tracking-[0.18em] text-espresso-soft">Operations console</p>
          </div>
        </div>

        <div className="relative max-w-lg">
          <p className="eyebrow text-gold">Front of house · Kitchen · Office</p>
          <h1 className="mt-4 font-serif text-5xl font-medium leading-[1.05] tracking-tight text-espresso-ink">
            Every table, ticket and till — <em className="text-gold">in one calm place.</em>
          </h1>
          <ul className="mt-10 grid gap-4 text-sm text-espresso-soft">
            {[
              { icon: "register" as const, text: "Cashiers land straight on the point of sale" },
              { icon: "chef" as const, text: "The kitchen sees tickets the moment they're paid" },
              { icon: "chart" as const, text: "Owners and managers get the day at a glance" },
            ].map((row) => (
              <li key={row.text} className="flex items-center gap-3">
                <span className="grid h-9 w-9 place-items-center rounded-lg bg-espresso-raised text-gold"><Icon name={row.icon} size={18} /></span>
                {row.text}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-espresso-soft">Staff access only. Activity is recorded in the audit log.</p>
      </section>

      {/* Form */}
      <section className="flex flex-col justify-center px-5 py-10 sm:px-10">
        <div className="mx-auto w-full max-w-[400px]">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <BrandMark size={40} />
            <p className="font-serif text-xl font-semibold">{brand.name}</p>
          </div>

          <p className="kicker">Staff sign-in</p>
          <h2 className="heading-lg mt-2">Welcome back</h2>
          <p className="mt-2 text-sm text-ink-soft">
            Use the phone number and password from your manager. You&apos;ll go straight to your workspace.
          </p>

          <form className="mt-8 grid gap-4" onSubmit={handleSubmit} noValidate>
            <label className="field">
              <span className="field-label">Phone number</span>
              <input
                className="input"
                placeholder="+233 20 000 0000"
                inputMode="tel"
                autoComplete="tel"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                aria-invalid={fieldErrors.phone ? true : undefined}
                aria-describedby={fieldErrors.phone ? "login-phone-error" : undefined}
              />
              {fieldErrors.phone ? <span id="login-phone-error" className="field-error" role="alert">{fieldErrors.phone}</span> : null}
            </label>
            <label className="field">
              <span className="field-label">Password</span>
              <span className="relative block">
                <input
                  className="input pr-12"
                  placeholder="••••••••"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={fieldErrors.password ? true : undefined}
                  aria-describedby={fieldErrors.password ? "login-password-error" : undefined}
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-1 my-auto grid h-10 w-10 place-items-center rounded-md text-ink-muted hover:text-ink"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  aria-pressed={showPassword}
                >
                  <Icon name="eye" size={18} />
                </button>
              </span>
              {fieldErrors.password ? <span id="login-password-error" className="field-error" role="alert">{fieldErrors.password}</span> : null}
            </label>

            {error ? (
              <p className="alert alert-danger" role="alert">
                <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
                {error}
              </p>
            ) : null}
            {session && session.user.role !== "CUSTOMER" ? (
              <p className="alert alert-info">
                <Icon name="info" size={18} className="mt-0.5 shrink-0" />
                <span>
                  Signed in as <strong>{session.user.name}</strong>.{" "}
                  <Link className="font-semibold underline underline-offset-2" href={routeByRole(session.user.role)}>Continue</Link>
                </span>
              </p>
            ) : null}

            <button className="btn btn-primary btn-lg btn-block mt-2" disabled={submitting}>
              {submitting ? <Spinner className="text-current" /> : null}
              {submitting ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="mt-8 text-center text-sm text-ink-soft">
            Forgot your password?{" "}
            <Link href="/forgot-password" className="font-semibold text-brand hover:underline">Send a reset link</Link>
            {" "}or ask an admin to send one.
          </p>
          <p className="mt-2 text-center">
            <Link href="/" className="text-sm font-semibold text-brand hover:underline">← Back to {brand.name}</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
