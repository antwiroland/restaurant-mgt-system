"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Icon } from "@/components/Icon";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="grid min-h-[70dvh] place-items-center px-4 py-12">
      <section className="panel w-full max-w-md p-8 text-center shadow-card" role="alert">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-danger-subtle text-danger-on">
          <Icon name="alert" size={22} />
        </span>
        <h1 className="heading-md mt-4">Something went wrong</h1>
        <p className="mt-2 text-sm text-ink-soft">
          {error.message || "An unexpected error occurred while rendering this page."}
        </p>
        <div className="mt-6 flex justify-center gap-2">
          <button type="button" className="btn btn-primary" onClick={() => reset()}>
            <Icon name="refresh" size={16} /> Try again
          </button>
          <Link href="/" className="btn btn-secondary">Go home</Link>
        </div>
      </section>
    </main>
  );
}
