"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Spinner } from "@/components/Spinner";
import { resetPassword } from "@/lib/apiClient";

function ResetPasswordForm() {
  const token = useSearchParams().get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ password?: string; confirm?: string }>({});
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: { password?: string; confirm?: string } = {};
    if (password.length < 8) next.password = "Use at least 8 characters";
    if (confirm !== password) next.confirm = "Passwords do not match";
    setFieldErrors(next);
    if (Object.keys(next).length > 0) return;
    setError("");
    setSubmitting(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not reset the password");
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <div className="mt-4 grid gap-3">
        <p className="alert alert-danger" role="alert">This reset link is incomplete. Open the link from your email or SMS again, or request a new one.</p>
        <Link href="/forgot-password" className="btn btn-secondary justify-self-start">Request a new link</Link>
      </div>
    );
  }

  if (done) {
    return (
      <div className="mt-4 grid gap-3" aria-live="polite">
        <p className="alert alert-success">Your password has been changed. You have been signed out everywhere else.</p>
        <Link href="/login" className="btn btn-primary justify-self-start">Sign in</Link>
      </div>
    );
  }

  return (
    <form className="mt-6 grid gap-3" onSubmit={handleSubmit} noValidate>
      <label className="field">
        <span className="field-label">New password</span>
        <input
          className="input"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-invalid={fieldErrors.password ? true : undefined}
          aria-describedby={fieldErrors.password ? "reset-password-error" : undefined}
        />
        {fieldErrors.password ? <span id="reset-password-error" className="field-error" role="alert">{fieldErrors.password}</span> : null}
      </label>
      <label className="field">
        <span className="field-label">Confirm new password</span>
        <input
          className="input"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          aria-invalid={fieldErrors.confirm ? true : undefined}
          aria-describedby={fieldErrors.confirm ? "reset-confirm-error" : undefined}
        />
        {fieldErrors.confirm ? <span id="reset-confirm-error" className="field-error" role="alert">{fieldErrors.confirm}</span> : null}
      </label>
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error} <Link href="/forgot-password" className="underline">Request a new link</Link>
        </p>
      ) : null}
      <button type="submit" className="btn btn-primary justify-self-start" disabled={submitting}>
        {submitting ? <Spinner className="text-current" /> : null}
        Set new password
      </button>
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <main className="shell">
      <section className="panel mx-auto max-w-xl">
        <p className="kicker">Access</p>
        <h1 className="mt-2 text-3xl font-semibold text-ink">Choose a new password</h1>
        <Suspense fallback={<p className="mt-4 text-ink-soft">Loading…</p>}>
          <ResetPasswordForm />
        </Suspense>
      </section>
    </main>
  );
}
