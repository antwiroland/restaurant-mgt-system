"use client";

import Link from "next/link";
import { useState } from "react";
import { Skeleton } from "@/components/Skeleton";
import { useStaffSession } from "@/components/SessionProvider";
import { changePassword, requestEmailVerification } from "@/lib/apiClient";

export default function AccountPage() {
  const { session, loading, authenticatedFetch, logout } = useStaffSession();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ current?: string; next?: string; confirm?: string }>({});
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function submitPassword(event: React.FormEvent) {
    event.preventDefault();
    const errors: typeof fieldErrors = {};
    if (!current) errors.current = "Enter your current password";
    if (next.length < 8) errors.next = "Use at least 8 characters";
    if (confirm !== next) errors.confirm = "Passwords do not match";
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((s) => changePassword(s, current, next));
      setCurrent("");
      setNext("");
      setConfirm("");
      setMessage("Password changed. Other devices have been signed out; sign in again here with the new password.");
      window.setTimeout(() => void logout(), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not change password");
    } finally {
      setBusy(false);
    }
  }

  async function sendVerification() {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await authenticatedFetch((s) => requestEmailVerification(s));
      setMessage("If your account has an unverified email address, a verification link is on its way.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send verification email");
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <main className="shell"><section className="panel grid gap-3"><Skeleton className="h-6 w-40" /><Skeleton className="h-40 w-full" /></section></main>;
  if (!session) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">Account</p>
          <h1 className="text-2xl font-semibold">Staff access required</h1>
          <Link href="/login" className="mt-4 inline-flex btn btn-primary">Open Login</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="shell grid gap-4 lg:grid-cols-2">
      <section className="panel grid content-start gap-3">
        <div>
          <p className="kicker">My account</p>
          <h1 className="text-2xl font-semibold">{session.user.name}</h1>
          <p className="text-sm text-ink-soft">Role: {session.user.role}</p>
        </div>
        <form className="grid gap-3" onSubmit={(e) => void submitPassword(e)} noValidate>
          <h2 className="text-lg font-semibold">Change password</h2>
          {([
            ["current", "Current password", current, setCurrent, "current-password"],
            ["next", "New password", next, setNext, "new-password"],
            ["confirm", "Confirm new password", confirm, setConfirm, "new-password"],
          ] as const).map(([key, label, value, setValue, autoComplete]) => (
            <label key={key} className="field">
              <span className="field-label">{label}</span>
              <input
                className="input"
                type="password"
                autoComplete={autoComplete}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                aria-invalid={fieldErrors[key] ? true : undefined}
                aria-describedby={fieldErrors[key] ? `account-${key}-error` : undefined}
              />
              {fieldErrors[key] ? <span id={`account-${key}-error`} className="field-error" role="alert">{fieldErrors[key]}</span> : null}
            </label>
          ))}
          <button type="submit" className="btn btn-primary justify-self-start" disabled={busy}>Change password</button>
        </form>
      </section>

      <section className="panel grid content-start gap-3">
        <h2 className="text-lg font-semibold">Email verification</h2>
        <p className="text-sm text-ink-soft">
          Password reset links go to a verified email address. If you have not verified yours, send yourself a new link.
        </p>
        <button type="button" className="btn btn-secondary justify-self-start" disabled={busy} onClick={() => void sendVerification()}>
          Send verification link
        </button>
        <div aria-live="polite">
          {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
          {message ? <p className="alert alert-success">{message}</p> : null}
        </div>
      </section>
    </main>
  );
}
