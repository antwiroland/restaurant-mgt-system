"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { verifyEmail } from "@/lib/apiClient";

function VerifyEmailStatus() {
  const token = useSearchParams().get("token") ?? "";
  const [state, setState] = useState<"working" | "done" | "failed">(token ? "working" : "failed");
  const [error, setError] = useState(token ? "" : "This verification link is incomplete.");
  const started = useRef(false);

  useEffect(() => {
    // Tokens are single use, so make sure a re-render never sends the request twice.
    if (!token || started.current) return;
    started.current = true;
    verifyEmail(token)
      .then(() => setState("done"))
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Could not verify the email address");
        setState("failed");
      });
  }, [token]);

  if (state === "working") return <p className="mt-4 text-ink-soft" aria-live="polite">Verifying your email address…</p>;
  if (state === "done") {
    return (
      <div className="mt-4 grid gap-3" aria-live="polite">
        <p className="alert alert-success">Your email address is verified. Password reset links will now be sent there.</p>
        <Link href="/login" className="btn btn-primary justify-self-start">Continue</Link>
      </div>
    );
  }
  return (
    <div className="mt-4 grid gap-3">
      <p className="alert alert-danger" role="alert">{error} You can send a new link from My Account.</p>
      <Link href="/account" className="btn btn-secondary justify-self-start">Open My Account</Link>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <main className="shell">
      <section className="panel mx-auto max-w-xl">
        <p className="kicker">Access</p>
        <h1 className="mt-2 text-3xl font-semibold text-ink">Verify email</h1>
        <Suspense fallback={<p className="mt-4 text-ink-soft">Loading…</p>}>
          <VerifyEmailStatus />
        </Suspense>
      </section>
    </main>
  );
}
