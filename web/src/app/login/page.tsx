"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { routeByRole } from "@/features/auth/auth";
import { useStaffSession } from "@/components/SessionProvider";

export default function LoginPage() {
  const router = useRouter();
  const { login, session } = useStaffSession();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

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
        <h1 className="mt-2 text-3xl font-semibold">Staff Login</h1>
        <p className="mt-2 text-[#35523d]">
          Sign in with an existing staff account. Admins and managers land on the dashboard; cashiers go straight to POS.
        </p>

        <form className="mt-6 grid gap-3" onSubmit={handleSubmit}>
          <label className="grid gap-2">
            <span className="text-sm font-medium text-[#35523d]">Phone</span>
            <input
              className="rounded-xl border border-[#cfe0c8] p-3"
              placeholder="+233..."
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </label>
          <label className="grid gap-2">
            <span className="text-sm font-medium text-[#35523d]">Password</span>
            <input
              className="rounded-xl border border-[#cfe0c8] p-3"
              placeholder="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error ? <p className="rounded-xl bg-[#fee2e2] px-4 py-3 text-sm text-[#991b1b]">{error}</p> : null}
          {session ? <p className="text-sm text-[#35523d]">Signed in as {session.user.name} ({session.user.role}).</p> : null}
          <button className="rounded-full bg-[#132018] px-4 py-3 text-white disabled:opacity-60" disabled={submitting}>
            {submitting ? "Signing in..." : "Sign In"}
          </button>
        </form>
      </section>
    </main>
  );
}
