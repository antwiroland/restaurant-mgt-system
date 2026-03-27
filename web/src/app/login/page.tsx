"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { routeByRole } from "@/features/auth/auth";
import { useStaffSession } from "@/components/SessionProvider";
import { Spinner } from "@/components/Spinner";

export default function LoginPage() {
  const router = useRouter();
  const { login, session } = useStaffSession();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ phone?: string; password?: string }>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextFieldErrors: { phone?: string; password?: string } = {};
    if (!phone.trim()) nextFieldErrors.phone = "Phone is required";
    if (!password.trim()) nextFieldErrors.password = "Password is required";
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
        throw new Error("Customer accounts cannot access the staff console");
      }
      router.replace(routeByRole(nextSession.user.role));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="shell">
      <section className="panel mx-auto max-w-xl">
        <p className="kicker">Access</p>
        <h1 className="mt-2 text-3xl font-semibold text-ink">Staff Login</h1>
        <p className="mt-2 text-ink-soft">
          Sign in with an existing staff account. Admins and managers land on the dashboard; cashiers go straight to POS.
        </p>

        <form className="mt-6 grid gap-3" onSubmit={handleSubmit}>
          <label className="field">
            <span className="field-label">Phone</span>
            <input
              className="input"
              placeholder="+233..."
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
            {fieldErrors.phone ? <span className="field-error">{fieldErrors.phone}</span> : null}
          </label>
          <label className="field">
            <span className="field-label">Password</span>
            <input
              className="input"
              placeholder="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {fieldErrors.password ? <span className="field-error">{fieldErrors.password}</span> : null}
          </label>
          {error ? <p className="alert alert-danger">{error}</p> : null}
          {session ? <p className="text-sm text-ink-soft">Signed in as {session.user.name} ({session.user.role}).</p> : null}
          <button className="btn btn-primary btn-md w-full disabled:opacity-60" disabled={submitting}>
            {submitting ? <Spinner className="text-current" /> : null}
            Sign In
          </button>
        </form>
      </section>
    </main>
  );
}
