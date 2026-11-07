"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { requestPasswordReset, resetPassword, verifyEmail } from "@/lib/apiClient";

export function ForgotPasswordForm() {
  const [value, setValue] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!value.trim()) {
      setError("Enter the email or phone number on your account");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await requestPasswordReset(value.trim());
      setSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <div className="grid gap-4">
        <p className="alert alert-success" role="status">
          <Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />
          If an account matches, we&apos;ve sent a reset link. It expires in 30 minutes.
        </p>
        <Link href="/login" className="btn btn-secondary btn-md btn-block">Back to sign in</Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      <label className="field">
        <span className="field-label">Email or phone number</span>
        <input className="input" autoComplete="username" value={value} onChange={(e) => setValue(e.target.value)} aria-invalid={error ? true : undefined} aria-describedby={error ? "forgot-error" : undefined} />
        {error ? <span id="forgot-error" className="field-error" role="alert">{error}</span> : null}
      </label>
      <button className="btn btn-primary btn-lg btn-block" disabled={busy}>{busy ? <Spinner /> : null} Send reset link</button>
      <Link href="/login" className="text-center text-sm font-semibold text-brand hover:underline">Back to sign in</Link>
    </form>
  );
}

export function ResetPasswordForm({ token }: { token: string }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<{ password?: string; confirm?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <div className="grid gap-4">
        <p className="alert alert-danger" role="alert">This reset link is incomplete. Request a new one.</p>
        <Link href="/forgot-password" className="btn btn-primary btn-md btn-block">Request a new link</Link>
      </div>
    );
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const next: typeof errors = {};
    if (password.length < 8) next.password = "Use at least 8 characters";
    if (confirm !== password) next.confirm = "Passwords don't match";
    setErrors(next);
    if (Object.keys(next).length) return;
    setBusy(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setErrors({ form: err instanceof Error ? err.message : "This link is invalid or has expired." });
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="grid gap-4">
        <p className="alert alert-success" role="status"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />Your password has been changed. Other sessions have been signed out.</p>
        <Link href="/login" className="btn btn-primary btn-md btn-block">Sign in</Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      <label className="field">
        <span className="field-label">New password</span>
        <input className="input" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={errors.password ? true : undefined} aria-describedby="pw-hint" />
        <span id="pw-hint" className={errors.password ? "field-error" : "field-hint"} role={errors.password ? "alert" : undefined}>{errors.password ?? "At least 8 characters."}</span>
      </label>
      <label className="field">
        <span className="field-label">Confirm new password</span>
        <input className="input" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} aria-invalid={errors.confirm ? true : undefined} aria-describedby={errors.confirm ? "pw2-error" : undefined} />
        {errors.confirm ? <span id="pw2-error" className="field-error" role="alert">{errors.confirm}</span> : null}
      </label>
      {errors.form ? (
        <p className="alert alert-danger" role="alert">
          {errors.form} <Link href="/forgot-password" className="font-semibold underline">Request a new link</Link>
        </p>
      ) : null}
      <button className="btn btn-primary btn-lg btn-block" disabled={busy}>{busy ? <Spinner /> : null} Set new password</button>
    </form>
  );
}

export function VerifyEmail({ token }: { token: string }) {
  const [state, setState] = useState<"working" | "done" | "error">(token ? "working" : "error");
  const [message, setMessage] = useState(token ? "" : "This verification link is incomplete.");
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    verifyEmail(token)
      .then(() => setState("done"))
      .catch((err) => {
        setMessage(err instanceof Error ? err.message : "This link is invalid or has expired.");
        setState("error");
      });
  }, [token]);

  if (state === "working") {
    return <p className="flex items-center gap-3 text-ink-soft" role="status"><Spinner /> Confirming your email…</p>;
  }
  if (state === "done") {
    return (
      <div className="grid gap-4">
        <p className="alert alert-success" role="status"><Icon name="checkCircle" size={18} className="mt-0.5 shrink-0" />Your email address is confirmed.</p>
        <Link href="/" className="btn btn-primary btn-md btn-block">Continue</Link>
      </div>
    );
  }
  return (
    <div className="grid gap-4">
      <p className="alert alert-danger" role="alert">{message} Staff can send a new link from My Account.</p>
      <Link href="/account" className="btn btn-secondary btn-md btn-block">Open My Account</Link>
    </div>
  );
}
