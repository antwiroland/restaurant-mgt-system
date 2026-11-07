"use client";

import Link from "next/link";
import { useState } from "react";
import { Spinner } from "@/components/Spinner";
import { requestPasswordReset } from "@/lib/apiClient";

export default function ForgotPasswordPage() {
  const [identifier, setIdentifier] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [error, setError] = useState("");
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!identifier.trim()) {
      setFieldError("Enter your email address or phone number");
      return;
    }
    setFieldError("");
    setError("");
    setSubmitting(true);
    try {
      await requestPasswordReset(identifier.trim());
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send a reset link");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="shell">
      <section className="panel mx-auto max-w-xl">
        <p className="kicker">Access</p>
        <h1 className="mt-2 text-3xl font-semibold text-ink">Reset your password</h1>
        {sent ? (
          <div className="mt-4 grid gap-3" aria-live="polite">
            <p className="alert alert-success">
              If an account matches, we have sent a reset link to its email address (or by SMS if it has no email). The link works once and expires soon.
            </p>
            <Link href="/login" className="btn btn-secondary justify-self-start">Back to sign in</Link>
          </div>
        ) : (
          <>
            <p className="mt-2 text-ink-soft">Enter the email address or phone number on your account and we will send you a link to choose a new password.</p>
            <form className="mt-6 grid gap-3" onSubmit={handleSubmit} noValidate>
              <label className="field">
                <span className="field-label">Email or phone</span>
                <input
                  className="input"
                  autoComplete="username"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  aria-invalid={fieldError ? true : undefined}
                  aria-describedby={fieldError ? "forgot-identifier-error" : undefined}
                />
                {fieldError ? <span id="forgot-identifier-error" className="field-error" role="alert">{fieldError}</span> : null}
              </label>
              {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
              <div className="flex flex-wrap items-center gap-3">
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? <Spinner label="Sending" /> : "Send reset link"}
                </button>
                <Link href="/login" className="text-sm text-ink-soft underline">Back to sign in</Link>
              </div>
            </form>
          </>
        )}
      </section>
    </main>
  );
}
