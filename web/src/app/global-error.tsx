"use client";

import { useEffect } from "react";

export default function GlobalError({
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
    <html lang="en">
      <body>
        <main className="shell">
          <section className="panel">
            <p className="kicker">Fatal Error</p>
            <h1 className="text-2xl font-semibold">Application error</h1>
            <p className="mt-2 text-sm text-[#35523d]">
              {error.message || "The app encountered a fatal error."}
            </p>
            <button
              type="button"
              className="mt-4 rounded-full bg-[#132018] px-4 py-2 text-white"
              onClick={() => reset()}
            >
              Reload
            </button>
          </section>
        </main>
      </body>
    </html>
  );
}
