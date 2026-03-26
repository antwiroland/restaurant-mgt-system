"use client";

import { useEffect } from "react";

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
    <main className="shell">
      <section className="panel">
        <p className="kicker">Unexpected Error</p>
        <h1 className="text-2xl font-semibold">Something went wrong</h1>
        <p className="mt-2 text-sm text-[#35523d]">
          {error.message || "An unexpected error occurred while rendering this page."}
        </p>
        <button
          type="button"
          className="mt-4 rounded-full bg-[#132018] px-4 py-2 text-white"
          onClick={() => reset()}
        >
          Try Again
        </button>
      </section>
    </main>
  );
}
